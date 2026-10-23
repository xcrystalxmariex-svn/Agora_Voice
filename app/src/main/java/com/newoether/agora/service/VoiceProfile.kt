package com.newoether.agora.service

import com.newoether.agora.data.VoiceSettings
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/**
 * A saved voice endpoint profile. Mirrors the endpoint fields of [VoiceSettings]
 * (endpoint style, URL, API key, model, reference WAV) so several endpoint
 * configurations can coexist and individual chats can pick one.
 */
@Serializable
data class VoiceProfile(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    /** [VoiceSettings.ENDPOINT_KIND_MAGPIE] or [VoiceSettings.ENDPOINT_KIND_OPENAI]. */
    val endpointKind: String = VoiceSettings.ENDPOINT_KIND_MAGPIE,
    val endpointUrl: String = "",
    val apiKey: String = "",
    val model: String = "",
    /** Absolute path of this profile's reference WAV (magpie zero-shot cloning). */
    val referenceWavPath: String = "",
    val isDefault: Boolean = false,
) {
    /**
     * Overlays this profile's endpoint fields on [base]. Blank profile fields fall back
     * to the legacy single-endpoint values; enabled / autoSpeak always come from [base].
     */
    fun applyTo(base: VoiceSettings): VoiceSettings = base.copy(
        endpointKind = endpointKind.ifBlank { base.endpointKind },
        endpointUrl = endpointUrl.ifBlank { base.endpointUrl },
        apiKey = apiKey.ifBlank { base.apiKey },
        model = model.ifBlank { base.model },
        referenceWavPath = referenceWavPath.ifBlank { base.referenceWavPath },
    )
}

object VoiceProfileCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val listSerializer = ListSerializer(VoiceProfile.serializer())

    fun encode(profiles: List<VoiceProfile>): String =
        json.encodeToString(listSerializer, profiles)

    fun decode(raw: String?): List<VoiceProfile> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(listSerializer, raw)
        } catch (_: Exception) {
            emptyList()
        }
    }
}
