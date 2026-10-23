package com.newoether.agora.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Owns the voice / TTS preference keys (enable, auto-speak, endpoint style, credentials,
 * model, and reference WAV path). SettingsManager exposes the same surface so existing
 * call sites keep working; this store is the single owner of the DataStore keys.
 */
internal class SettingsVoicePreferenceStore(
    private val dataStore: DataStore<Preferences>,
) {
    val voiceEnabled: Flow<Boolean> = dataStore.data.map { it[VOICE_ENABLED] ?: false }
    val voiceAutoSpeak: Flow<Boolean> = dataStore.data.map { it[VOICE_AUTO_SPEAK] ?: false }
    val voiceEndpointKind: Flow<String> = dataStore.data.map {
        it[VOICE_ENDPOINT_KIND] ?: VoiceSettings.ENDPOINT_KIND_MAGPIE
    }
    val voiceEndpointUrl: Flow<String> = dataStore.data.map {
        it[VOICE_ENDPOINT_URL] ?: VoiceSettings.DEFAULT_MAGPIE_URL
    }
    val voiceApiKey: Flow<String> = dataStore.data.map {
        com.newoether.agora.util.SecretCrypto.decrypt(it[VOICE_API_KEY] ?: "")
    }
    val voiceModel: Flow<String> = dataStore.data.map {
        it[VOICE_MODEL] ?: "magpie-tts-zeroshot"
    }
    val voiceReferenceWavPath: Flow<String> = dataStore.data.map {
        it[VOICE_REFERENCE_WAV_PATH] ?: ""
    }

    suspend fun saveVoiceEnabled(enabled: Boolean) {
        dataStore.edit { it[VOICE_ENABLED] = enabled }
    }

    suspend fun saveVoiceAutoSpeak(enabled: Boolean) {
        dataStore.edit { it[VOICE_AUTO_SPEAK] = enabled }
    }

    suspend fun saveVoiceEndpointKind(kind: String) {
        dataStore.edit {
            it[VOICE_ENDPOINT_KIND] =
                if (kind == VoiceSettings.ENDPOINT_KIND_OPENAI) VoiceSettings.ENDPOINT_KIND_OPENAI
                else VoiceSettings.ENDPOINT_KIND_MAGPIE
        }
    }

    suspend fun saveVoiceEndpointUrl(url: String) {
        dataStore.edit { it[VOICE_ENDPOINT_URL] = url }
    }

    suspend fun saveVoiceApiKey(key: String) {
        dataStore.edit { prefs ->
            if (key.isBlank()) prefs.remove(VOICE_API_KEY)
            else prefs[VOICE_API_KEY] = com.newoether.agora.util.SecretCrypto.encrypt(key)
        }
    }

    suspend fun saveVoiceModel(model: String) {
        dataStore.edit { it[VOICE_MODEL] = model }
    }

    suspend fun saveVoiceReferenceWavPath(path: String) {
        dataStore.edit { prefs ->
            if (path.isBlank()) prefs.remove(VOICE_REFERENCE_WAV_PATH)
            else prefs[VOICE_REFERENCE_WAV_PATH] = path
        }
    }
}
