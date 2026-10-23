package com.newoether.agora.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.newoether.agora.util.SecretCrypto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Owns voice / TTS preference keys.
 *
 * Supports multiple [VoiceProfile]s plus global enable/auto-speak and an active default profile id.
 * Legacy single-endpoint keys are migrated into the first profile on first write.
 */
internal class SettingsVoicePreferenceStore(
    private val dataStore: DataStore<Preferences>,
) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    val voiceEnabled: Flow<Boolean> = dataStore.data.map { it[VOICE_ENABLED] ?: false }
    val voiceAutoSpeak: Flow<Boolean> = dataStore.data.map { it[VOICE_AUTO_SPEAK] ?: false }
    val voiceActiveProfileId: Flow<String> = dataStore.data.map { it[VOICE_ACTIVE_PROFILE_ID] ?: "" }

    val voiceProfiles: Flow<List<VoiceProfile>> = dataStore.data.map { prefs ->
        decodeProfiles(prefs)
    }

    /** Resolved default profile (active id, else first). */
    val voiceActiveProfile: Flow<VoiceProfile?> = dataStore.data.map { prefs ->
        val profiles = decodeProfiles(prefs)
        if (profiles.isEmpty()) return@map null
        val activeId = prefs[VOICE_ACTIVE_PROFILE_ID].orEmpty()
        profiles.firstOrNull { it.id == activeId } ?: profiles.first()
    }

    // Legacy single-value flows (backed by active profile)
    val voiceEndpointKind: Flow<String> = voiceActiveProfile.map {
        it?.endpointKind ?: VoiceSettings.ENDPOINT_KIND_MAGPIE
    }
    val voiceEndpointUrl: Flow<String> = voiceActiveProfile.map {
        it?.endpointUrl ?: VoiceSettings.DEFAULT_MAGPIE_URL
    }
    val voiceApiKey: Flow<String> = voiceActiveProfile.map { it?.apiKey.orEmpty() }
    val voiceModel: Flow<String> = voiceActiveProfile.map { it?.model ?: "magpie-tts-zeroshot" }
    val voiceReferenceWavPath: Flow<String> = voiceActiveProfile.map { it?.referenceWavPath.orEmpty() }

    suspend fun saveVoiceEnabled(enabled: Boolean) {
        dataStore.edit { it[VOICE_ENABLED] = enabled }
    }

    suspend fun saveVoiceAutoSpeak(enabled: Boolean) {
        dataStore.edit { it[VOICE_AUTO_SPEAK] = enabled }
    }

    suspend fun saveVoiceActiveProfileId(id: String) {
        dataStore.edit { it[VOICE_ACTIVE_PROFILE_ID] = id }
    }

    suspend fun saveVoiceProfiles(profiles: List<VoiceProfile>) {
        dataStore.edit { prefs ->
            val encrypted = profiles.map { p ->
                p.copy(apiKey = if (p.apiKey.isBlank()) "" else SecretCrypto.encrypt(p.apiKey))
            }
            prefs[VOICE_PROFILES_JSON] = json.encodeToString(encrypted)
            val active = prefs[VOICE_ACTIVE_PROFILE_ID].orEmpty()
            if (active.isNotEmpty() && profiles.none { it.id == active }) {
                prefs[VOICE_ACTIVE_PROFILE_ID] = profiles.firstOrNull()?.id.orEmpty()
            }
            if (prefs[VOICE_ACTIVE_PROFILE_ID].isNullOrEmpty() && profiles.isNotEmpty()) {
                prefs[VOICE_ACTIVE_PROFILE_ID] = profiles.first().id
            }
        }
    }

    suspend fun upsertVoiceProfile(profile: VoiceProfile) {
        ensureMigrated()
        val current = voiceProfiles.first().toMutableList()
        val idx = current.indexOfFirst { it.id == profile.id }
        if (idx >= 0) current[idx] = profile else current.add(profile)
        saveVoiceProfiles(current)
        if (voiceActiveProfileId.first().isEmpty()) {
            saveVoiceActiveProfileId(profile.id)
        }
    }

    suspend fun deleteVoiceProfile(id: String) {
        ensureMigrated()
        val current = voiceProfiles.first().filterNot { it.id == id }
        saveVoiceProfiles(current)
    }

    suspend fun saveVoiceEndpointKind(kind: String) = mutateActive {
        it.copy(
            endpointKind = if (kind == VoiceSettings.ENDPOINT_KIND_OPENAI)
                VoiceSettings.ENDPOINT_KIND_OPENAI else VoiceSettings.ENDPOINT_KIND_MAGPIE
        )
    }

    suspend fun saveVoiceEndpointUrl(url: String) = mutateActive { it.copy(endpointUrl = url) }
    suspend fun saveVoiceApiKey(key: String) = mutateActive { it.copy(apiKey = key) }
    suspend fun saveVoiceModel(model: String) = mutateActive { it.copy(model = model) }
    suspend fun saveVoiceReferenceWavPath(path: String) =
        mutateActive { it.copy(referenceWavPath = path) }

    private suspend fun mutateActive(block: (VoiceProfile) -> VoiceProfile) {
        ensureMigrated()
        val profiles = voiceProfiles.first().toMutableList()
        if (profiles.isEmpty()) {
            val created = block(VoiceProfile(name = "Default"))
            saveVoiceProfiles(listOf(created))
            saveVoiceActiveProfileId(created.id)
            return
        }
        val activeId = voiceActiveProfileId.first()
        val idx = profiles.indexOfFirst { it.id == activeId }.let { if (it < 0) 0 else it }
        profiles[idx] = block(profiles[idx])
        saveVoiceProfiles(profiles)
    }

    private fun decodeProfiles(prefs: Preferences): List<VoiceProfile> {
        val raw = prefs[VOICE_PROFILES_JSON]
        if (!raw.isNullOrBlank()) {
            return try {
                json.decodeFromString<List<VoiceProfile>>(raw).map { p ->
                    p.copy(apiKey = SecretCrypto.decrypt(p.apiKey))
                }
            } catch (_: Exception) {
                emptyList()
            }
        }
        val url = prefs[VOICE_ENDPOINT_URL]
        val keyEnc = prefs[VOICE_API_KEY]
        if (url.isNullOrBlank() && keyEnc.isNullOrBlank()) return emptyList()
        return listOf(
            VoiceProfile(
                name = "Default",
                endpointKind = prefs[VOICE_ENDPOINT_KIND] ?: VoiceSettings.ENDPOINT_KIND_MAGPIE,
                endpointUrl = url ?: VoiceSettings.DEFAULT_MAGPIE_URL,
                apiKey = SecretCrypto.decrypt(keyEnc ?: ""),
                model = prefs[VOICE_MODEL] ?: "magpie-tts-zeroshot",
                referenceWavPath = prefs[VOICE_REFERENCE_WAV_PATH] ?: "",
            )
        )
    }

    private suspend fun ensureMigrated() {
        dataStore.edit { prefs ->
            if (!prefs[VOICE_PROFILES_JSON].isNullOrBlank()) return@edit
            val url = prefs[VOICE_ENDPOINT_URL]
            val keyEnc = prefs[VOICE_API_KEY]
            if (url.isNullOrBlank() && keyEnc.isNullOrBlank()) return@edit
            val profile = VoiceProfile(
                name = "Default",
                endpointKind = prefs[VOICE_ENDPOINT_KIND] ?: VoiceSettings.ENDPOINT_KIND_MAGPIE,
                endpointUrl = url ?: VoiceSettings.DEFAULT_MAGPIE_URL,
                apiKey = keyEnc ?: "",
                model = prefs[VOICE_MODEL] ?: "magpie-tts-zeroshot",
                referenceWavPath = prefs[VOICE_REFERENCE_WAV_PATH] ?: "",
            )
            prefs[VOICE_PROFILES_JSON] = json.encodeToString(listOf(profile))
            prefs[VOICE_ACTIVE_PROFILE_ID] = profile.id
        }
    }
}
