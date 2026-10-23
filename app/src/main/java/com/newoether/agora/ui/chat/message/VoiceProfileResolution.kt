package com.newoether.agora.ui.chat.message

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.newoether.agora.data.VoiceSettings
import com.newoether.agora.service.VoiceProfileStore
import com.newoether.agora.viewmodel.ChatViewModel

/**
 * The voice settings a chat should speak with: the legacy single-endpoint settings from
 * Settings > Voice, overlaid with the chat's voice profile (its own pick, else the default
 * profile). With no saved profiles this is exactly the legacy configuration.
 */
@Composable
internal fun rememberChatVoiceSettings(viewModel: ChatViewModel): VoiceSettings {
    val context = LocalContext.current
    val settings = viewModel.settings
    val store = remember { VoiceProfileStore(context) }
    val chatId by viewModel.currentConversationId.collectAsState()

    val enabled by settings.voiceEnabled.collectAsState()
    val autoSpeak by settings.voiceAutoSpeak.collectAsState()
    val endpointKind by settings.voiceEndpointKind.collectAsState()
    val endpointUrl by settings.voiceEndpointUrl.collectAsState()
    val apiKey by settings.voiceApiKey.collectAsState()
    val model by settings.voiceModel.collectAsState()
    val referencePath by settings.voiceReferenceWavPath.collectAsState()

    val profileFlow = remember(chatId) { store.profileForChat(chatId) }
    val profile by profileFlow.collectAsState(initial = null)

    val base = VoiceSettings(
        enabled = enabled,
        endpointKind = endpointKind,
        endpointUrl = endpointUrl,
        apiKey = apiKey,
        model = model,
        referenceWavPath = referencePath,
        autoSpeak = autoSpeak,
    )
    return profile?.applyTo(base) ?: base
}
