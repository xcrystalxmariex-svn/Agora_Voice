package com.newoether.agora.data

import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A saved voice / TTS endpoint profile.
 * Users can keep multiple profiles and assign one per chat (or fall back to the global default).
 */
@Serializable
data class VoiceProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "Default",
    /** "magpie_zeroshot" or "openai_speech". */
    val endpointKind: String = VoiceSettings.ENDPOINT_KIND_MAGPIE,
    val endpointUrl: String = VoiceSettings.DEFAULT_MAGPIE_URL,
    val apiKey: String = "",
    val model: String = "magpie-tts-zeroshot",
    /** Absolute path of the reference WAV used for zero-shot cloning (magpie). */
    val referenceWavPath: String = "",
) {
    fun toSettings(enabled: Boolean, autoSpeak: Boolean): VoiceSettings =
        VoiceSettings(
            enabled = enabled,
            endpointKind = endpointKind,
            endpointUrl = endpointUrl,
            apiKey = apiKey,
            model = model,
            referenceWavPath = referenceWavPath,
            autoSpeak = autoSpeak,
        )
}

/** Voice (TTS) configuration snapshot consumed by [com.newoether.agora.service.VoiceController]. */
data class VoiceSettings(
    val enabled: Boolean = false,
    /** "magpie_zeroshot" (NVIDIA CV-style base64 JSON) or "openai_speech" (/audio/speech style). */
    val endpointKind: String = ENDPOINT_KIND_MAGPIE,
    /** Full invoke URL, e.g. https://ai.api.nvidia.com/v1/cv/nvidia/magpie-tts-zeroshot */
    val endpointUrl: String = DEFAULT_MAGPIE_URL,
    val apiKey: String = "",
    val model: String = "magpie-tts-zeroshot",
    /** Absolute path of the locally stored reference WAV used for zero-shot voice cloning. */
    val referenceWavPath: String = "",
    /** Speak assistant replies automatically when they finish streaming. */
    val autoSpeak: Boolean = false,
) {
    fun isConfigured(): Boolean =
        enabled && endpointUrl.isNotBlank() && apiKey.isNotBlank()

    companion object {
        const val ENDPOINT_KIND_MAGPIE = "magpie_zeroshot"
        const val ENDPOINT_KIND_OPENAI = "openai_speech"
        const val DEFAULT_MAGPIE_URL =
            "https://ai.api.nvidia.com/v1/cv/nvidia/magpie-tts-zeroshot"
    }
}
