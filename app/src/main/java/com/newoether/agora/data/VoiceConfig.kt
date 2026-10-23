package com.newoether.agora.data

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
