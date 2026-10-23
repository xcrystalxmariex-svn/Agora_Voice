package com.newoether.agora.data.repository

import com.newoether.agora.data.VoiceProfile

/** Voice / TTS setters for [SettingsRepository] — kept out of the main file for the 800-line gate. */
suspend fun SettingsRepository.saveVoiceEnabled(enabled: Boolean) =
    settingsManager.saveVoiceEnabled(enabled)

suspend fun SettingsRepository.saveVoiceAutoSpeak(enabled: Boolean) =
    settingsManager.saveVoiceAutoSpeak(enabled)

suspend fun SettingsRepository.saveVoiceEndpointKind(kind: String) =
    settingsManager.saveVoiceEndpointKind(kind)

suspend fun SettingsRepository.saveVoiceEndpointUrl(url: String) =
    settingsManager.saveVoiceEndpointUrl(url)

suspend fun SettingsRepository.saveVoiceApiKey(key: String) =
    settingsManager.saveVoiceApiKey(key)

suspend fun SettingsRepository.saveVoiceModel(model: String) =
    settingsManager.saveVoiceModel(model)

suspend fun SettingsRepository.saveVoiceReferenceWavPath(path: String) =
    settingsManager.saveVoiceReferenceWavPath(path)

suspend fun SettingsRepository.saveVoiceProfiles(profiles: List<VoiceProfile>) =
    settingsManager.saveVoiceProfiles(profiles)

suspend fun SettingsRepository.upsertVoiceProfile(profile: VoiceProfile) =
    settingsManager.upsertVoiceProfile(profile)

suspend fun SettingsRepository.deleteVoiceProfile(id: String) =
    settingsManager.deleteVoiceProfile(id)

suspend fun SettingsRepository.saveVoiceActiveProfileId(id: String) =
    settingsManager.saveVoiceActiveProfileId(id)
