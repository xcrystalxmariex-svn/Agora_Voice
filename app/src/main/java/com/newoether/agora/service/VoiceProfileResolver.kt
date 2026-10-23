package com.newoether.agora.service

import com.newoether.agora.data.VoiceProfile
import com.newoether.agora.data.VoiceSettings

/**
 * Resolves which [VoiceProfile] applies for a conversation and produces a [VoiceSettings]
 * snapshot for [VoiceController].
 *
 * Resolution order:
 * 1. chatVoiceProfileId (per-conversation override)
 * 2. global active profile id
 * 3. first profile in the list
 * 4. null / empty settings
 */
object VoiceProfileResolver {
    fun resolve(
        profiles: List<VoiceProfile>,
        activeProfileId: String,
        chatVoiceProfileId: String?,
        enabled: Boolean,
        autoSpeak: Boolean,
    ): VoiceSettings {
        if (profiles.isEmpty()) {
            return VoiceSettings(enabled = enabled, autoSpeak = autoSpeak)
        }
        val chosen = chatVoiceProfileId
            ?.let { id -> profiles.firstOrNull { it.id == id } }
            ?: profiles.firstOrNull { it.id == activeProfileId }
            ?: profiles.first()
        return chosen.toSettings(enabled = enabled, autoSpeak = autoSpeak)
    }

    fun applyToController(
        controller: VoiceController,
        profiles: List<VoiceProfile>,
        activeProfileId: String,
        chatVoiceProfileId: String?,
        enabled: Boolean,
        autoSpeak: Boolean,
    ) {
        controller.updateSettings(
            resolve(profiles, activeProfileId, chatVoiceProfileId, enabled, autoSpeak)
        )
    }
}
