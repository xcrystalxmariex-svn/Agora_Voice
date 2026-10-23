package com.newoether.agora.service

import android.content.Context
import android.media.MediaPlayer
import android.util.Base64
import com.newoether.agora.data.VoiceSettings
import com.newoether.agora.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * App-wide voice (TTS) controller.
 *
 * Supports two endpoint styles behind one [VoiceSettings] snapshot:
 *  - magpie_zeroshot: POST {endpointUrl}, JSON {"input": text, "audio": base64(wav)},
 *    response JSON {"audio": base64}. Used for zero-shot voice cloning from a short WAV.
 *  - openai_speech: POST {endpointUrl} (the provider's /audio/speech URL), JSON
 *    {"model": ..., "input": text, "response_format": "wav"}, raw audio bytes returned.
 *
 * Synthesized audio is cached under cacheDir/tts/<messageId>.wav and played with MediaPlayer.
 */
class VoiceController private constructor(private val appContext: Context) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()
    private val json = Json { ignoreUnknownKeys = true }
    private var player: MediaPlayer? = null

    private val _playingMessageId = MutableStateFlow<String?>(null)
    val playingMessageId: StateFlow<String?> = _playingMessageId

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError

    @Volatile
    private var settings: VoiceSettings = VoiceSettings()

    /** Call whenever the voice settings change so subsequent requests use the new values. */
    fun updateSettings(value: VoiceSettings) {
        settings = value
    }

    /**
     * Speaks [rawText]. [override] (e.g. the chat's resolved voice profile) wins over the
     * snapshot last pushed with [updateSettings].
     */
    fun speak(messageId: String, rawText: String, override: VoiceSettings? = null) {
        val s = override ?: settings
        if (!s.isConfigured() || rawText.isBlank()) return
        stop()
        _lastError.value = null
        scope.launch {
            try {
                val audio = synthesize(s, plainTextForSpeech(rawText))
                val out = File(appContext.cacheDir, "tts/$messageId.wav")
                    .apply { parentFile?.mkdirs() }
                withContext(Dispatchers.IO) { out.writeBytes(audio) }
                play(messageId, out)
            } catch (e: Exception) {
                DebugLog.e(TAG, "TTS synthesis failed", e)
                _lastError.value = e.message ?: "Speech synthesis failed"
                _isSpeaking.value = false
                _playingMessageId.value = null
            }
        }
    }

    fun stop() {
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        _isSpeaking.value = false
        _playingMessageId.value = null
    }

    fun shutdown() {
        stop()
    }

    private suspend fun synthesize(s: VoiceSettings, text: String): ByteArray =
        withContext(Dispatchers.IO) {
            when (s.endpointKind) {
                VoiceSettings.ENDPOINT_KIND_OPENAI -> openAiSpeech(s, text)
                else -> magpieZeroShot(s, text)
            }
        }

    /** NVIDIA-style zero-shot voice cloning: base64 reference WAV travels with the request. */
    private fun magpieZeroShot(s: VoiceSettings, text: String): ByteArray {
        val refFile = s.referenceWavPath.takeIf { it.isNotBlank() }?.let(::File)
        require(refFile != null && refFile.exists()) {
            "No voice reference WAV configured. Pick a short (~5 s) .wav in Settings > Voice."
        }
        val refB64 = Base64.encodeToString(refFile.readBytes(), Base64.NO_WRAP)
        val payload = buildJsonObject {
            put("input", text)
            put("audio", refB64)
        }.toString()
        val request = Request.Builder()
            .url(s.endpointUrl)
            .header("Authorization", "Bearer ${s.apiKey}")
            .header("Accept", "application/json")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("TTS endpoint error ${response.code}: ${body.take(300)}")
            }
            val audioB64 = json.parseToJsonElement(body)
                .jsonObject["audio"]?.jsonPrimitive?.content
                ?: throw IllegalStateException("TTS response had no audio field")
            Base64.decode(audioB64, Base64.DEFAULT)
        }
    }

    /** OpenAI-compatible /audio/speech style endpoint returning raw audio bytes. */
    private fun openAiSpeech(s: VoiceSettings, text: String): ByteArray {
        val payload = buildJsonObject {
            put("model", s.model.ifBlank { "tts-1" })
            put("input", text)
            put("response_format", "wav")
        }.toString()
        val request = Request.Builder()
            .url(s.endpointUrl)
            .header("Authorization", "Bearer ${s.apiKey}")
            .post(payload.toRequestBody(JSON_MEDIA_TYPE))
            .build()
        return client.newCall(request).execute().use { response ->
            val bytes = response.body?.bytes()
            if (!response.isSuccessful || bytes == null) {
                val snippet = response.body?.string().orEmpty().take(300)
                throw IllegalStateException("TTS endpoint error ${response.code}: $snippet")
            }
            bytes
        }
    }

    private fun play(messageId: String, file: File) {
        stop()
        val mp = MediaPlayer()
        player = mp
        mp.setDataSource(file.absolutePath)
        mp.setOnCompletionListener {
            _isSpeaking.value = false
            _playingMessageId.value = null
        }
        mp.setOnErrorListener { _, _, _ ->
            _isSpeaking.value = false
            _playingMessageId.value = null
            true
        }
        mp.prepare()
        mp.start()
        _playingMessageId.value = messageId
        _isSpeaking.value = true
    }

    companion object {
        private const val TAG = "VoiceController"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        @Volatile
        private var instance: VoiceController? = null

        fun get(context: Context): VoiceController =
            instance ?: synchronized(this) {
                instance ?: VoiceController(context.applicationContext).also { instance = it }
            }
    }
}

/** Strip markdown / code fences so spoken text sounds natural. */
internal fun plainTextForSpeech(raw: String): String {
    var text = raw
    // fenced code blocks
    text = Regex("```[\\s\\S]*?```").replace(text, " ")
    // inline code
    text = Regex("`([^`]*)`").replace(text, "$1")
    text = text.replace("**", "").replace("__", "")
    // markdown links [label](url) -> label
    text = Regex("""!?\[([^\]]*)\]\([^)]*\)""").replace(text, "$1")
    // html tags
    text = Regex("<[^>]+>").replace(text, " ")
    // list markers and horizontal rules
    text = Regex("""(?m)^\s*[-*+]\s+""").replace(text, "")
    text = Regex("""(?m)^\s*#+\s*""").replace(text, "")
    text = Regex("""(?m)^-{3,}\s*$""").replace(text, " ")
    // collapse whitespace
    text = Regex("""\s+""").replace(text, " ").trim()
    return text
}
