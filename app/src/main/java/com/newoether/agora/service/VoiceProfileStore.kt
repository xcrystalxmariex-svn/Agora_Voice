package com.newoether.agora.service

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

private val Context.voiceProfileDataStore by preferencesDataStore(
    name = "voice_profiles_store",
)

/**
 * Stores multiple voice endpoint profiles plus:
 *  - the global default profile id
 *  - an optional per-chat override map (conversationId -> profileId)
 *
 * Per-chat selection is kept in DataStore (not Room) so no database
 * migration is required.
 */
class VoiceProfileStore(private val context: Context) {

    private object Keys {
        val PROFILES = stringPreferencesKey("voice_profiles_json")
        val DEFAULT_ID = stringPreferencesKey("voice_default_profile_id")
        val CHAT_OVERRIDES = stringPreferencesKey("voice_chat_overrides_json")
    }

    private val overridesSerializer = MapSerializer(String.serializer(), String.serializer())

    private fun decodeOverrides(raw: String): Map<String, String> =
        try {
            Json.decodeFromString(overridesSerializer, raw)
        } catch (_: Exception) {
            emptyMap()
        }

    private fun encodeOverrides(map: Map<String, String>): String =
        Json.encodeToString(overridesSerializer, map)

    // ---------------- Flows ----------------

    val profiles: Flow<List<VoiceProfile>> =
        context.voiceProfileDataStore.data.map { prefs ->
            VoiceProfileCodec.decode(prefs[Keys.PROFILES])
        }

    val defaultProfileId: Flow<String?> =
        context.voiceProfileDataStore.data.map { prefs ->
            prefs[Keys.DEFAULT_ID]
        }

    /** conversationId -> profileId */
    val chatOverrides: Flow<Map<String, String>> =
        context.voiceProfileDataStore.data.map { prefs ->
            prefs[Keys.CHAT_OVERRIDES]
                ?.let { decodeOverrides(it) } ?: emptyMap()
        }

    /**
     * Resolves the effective profile for a chat: per-chat override wins,
     * then the default profile, then null (fall back to legacy single-endpoint
     * behaviour in SettingsManager / VoiceController).
     */
    fun profileForChat(chatId: String?): Flow<VoiceProfile?> =
        combine(profiles, chatOverrides, defaultProfileId) { list, overrides, defaultId ->
            if (chatId != null) {
                overrides[chatId]?.let { id -> list.firstOrNull { it.id == id } }
                    ?: list.firstOrNull { it.id == defaultId }
                    ?: list.firstOrNull { it.isDefault }
                    ?: list.firstOrNull()
            } else {
                list.firstOrNull { it.id == defaultId }
                    ?: list.firstOrNull { it.isDefault }
                    ?: list.firstOrNull()
            }
        }

    // ---------------- Mutations ----------------

    suspend fun addProfile(profile: VoiceProfile) {
        context.voiceProfileDataStore.edit { prefs ->
            val list = VoiceProfileCodec.decode(prefs[Keys.PROFILES]).toMutableList()
            list.add(profile)
            prefs[Keys.PROFILES] = VoiceProfileCodec.encode(list)
            if (profile.isDefault || list.size == 1) {
                prefs[Keys.DEFAULT_ID] = profile.id
            }
        }
    }

    suspend fun updateProfile(profile: VoiceProfile) {
        context.voiceProfileDataStore.edit { prefs ->
            val list = VoiceProfileCodec.decode(prefs[Keys.PROFILES])
                .map { if (it.id == profile.id) profile else it }
            val defaulted = if (profile.isDefault) {
                list.map { if (it.id == profile.id) it else it.copy(isDefault = false) }
            } else list
            prefs[Keys.PROFILES] = VoiceProfileCodec.encode(defaulted)
            if (profile.isDefault) prefs[Keys.DEFAULT_ID] = profile.id
        }
    }

    suspend fun deleteProfile(profileId: String) {
        context.voiceProfileDataStore.edit { prefs ->
            val list = VoiceProfileCodec.decode(prefs[Keys.PROFILES])
                .filterNot { it.id == profileId }
            prefs[Keys.PROFILES] = VoiceProfileCodec.encode(list)
            if (prefs[Keys.DEFAULT_ID] == profileId) {
                list.firstOrNull()?.let { prefs[Keys.DEFAULT_ID] = it.id }
                    ?: prefs.remove(Keys.DEFAULT_ID)
            }
            // Clean up any chat overrides pointing at the deleted profile.
            val overrides = prefs[Keys.CHAT_OVERRIDES]
                ?.let { decodeOverrides(it) }?.filterValues { it != profileId }.orEmpty()
            prefs[Keys.CHAT_OVERRIDES] =
                encodeOverrides(overrides)
        }
    }

    suspend fun setDefaultProfile(profileId: String?) {
        context.voiceProfileDataStore.edit { prefs ->
            if (profileId == null) prefs.remove(Keys.DEFAULT_ID)
            else prefs[Keys.DEFAULT_ID] = profileId
        }
    }

    suspend fun setChatOverride(chatId: String, profileId: String) {
        context.voiceProfileDataStore.edit { prefs ->
            val current = prefs[Keys.CHAT_OVERRIDES]
                ?.let { decodeOverrides(it) }?.toMutableMap() ?: mutableMapOf()
            current[chatId] = profileId
            prefs[Keys.CHAT_OVERRIDES] =
                encodeOverrides(current.toMap())
        }
    }

    suspend fun clearChatOverride(chatId: String) {
        context.voiceProfileDataStore.edit { prefs ->
            val current = prefs[Keys.CHAT_OVERRIDES]
                ?.let { decodeOverrides(it) }?.toMutableMap() ?: mutableMapOf()
            current.remove(chatId)
            if (current.isEmpty()) prefs.remove(Keys.CHAT_OVERRIDES)
            else prefs[Keys.CHAT_OVERRIDES] =
                encodeOverrides(current.toMap())
        }
    }
}
