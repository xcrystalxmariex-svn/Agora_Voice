package com.newoether.agora.ui.chat.message

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.service.VoiceController
import com.newoether.agora.viewmodel.ChatViewModel
import com.newoether.agora.data.forDisplay
import com.newoether.agora.model.Participant

/**
 * Speaker icon shown on assistant message rows.
 *
 * Add next to the existing copy / regenerate actions in the assistant message
 * action row (in this package). Tap speaks the message, tap again stops.
 */
@Composable
fun AssistantSpeakAction(
    messageId: String,
    text: String,
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val controller = remember { VoiceController.get(context) }
    val enabled by viewModel.settings.voiceEnabled.collectAsState(initial = false)
    if (!enabled) return
    val voiceSettings = rememberChatVoiceSettings(viewModel)
    val playingId by controller.playingMessageId.collectAsState()
    val playingThis = playingId == messageId
    IconButton(
        onClick = {
            if (playingThis) controller.stop() else controller.speak(messageId, text, voiceSettings)
        },
        modifier = modifier.size(32.dp),
    ) {
        Icon(
            imageVector = if (playingThis) Icons.Default.Stop else Icons.AutoMirrored.Filled.VolumeUp,
            contentDescription = stringResource(
                if (playingThis) R.string.voice_stop else R.string.voice_speak
            ),
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Auto-speak effect: place once in the chat screen (e.g. ChatApp) that has the
 * latest streaming assistant message. Fires exactly once per finished message
 * when Settings > Voice > "Speak replies automatically" is on.
 */
@Composable
fun VoiceAutoSpeakEffect(
    latestAssistantMessageId: String?,
    latestAssistantText: String?,
    isStreaming: Boolean,
    viewModel: ChatViewModel,
) {
    val context = LocalContext.current
    val controller = remember { VoiceController.get(context) }
    val enabled by viewModel.settings.voiceEnabled.collectAsState(initial = false)
    val autoSpeak by viewModel.settings.voiceAutoSpeak.collectAsState(initial = false)
    val voiceSettings = rememberChatVoiceSettings(viewModel)
    var lastSpokenId by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(latestAssistantMessageId, isStreaming) {
        if (enabled && autoSpeak && !isStreaming &&
            latestAssistantMessageId != null &&
            latestAssistantText != null &&
            latestAssistantMessageId != lastSpokenId
        ) {
            lastSpokenId = latestAssistantMessageId
            controller.speak(latestAssistantMessageId, latestAssistantText, voiceSettings)
        }
    }
}


/**
 * Convenience host for ChatApp: collects the latest assistant message and drives
 * [VoiceAutoSpeakEffect]. Keeps the chat screen under the 800-line Kotlin source limit.
 */
@Composable
fun VoiceAutoSpeakHost(viewModel: ChatViewModel) {
    val isLoading by viewModel.isLoading.collectAsState()
    val generationSnapshot by viewModel.generationSnapshot.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val customProviders by viewModel.settings.customProviders.collectAsState()
    val displayMessages = messages.map { it.forDisplay(customProviders) }
    val latestAssistant = generationSnapshot.streamingMessage?.forDisplay(customProviders)
        ?.takeIf { it.participant == Participant.MODEL }
        ?: displayMessages.lastOrNull { it.participant == Participant.MODEL }
    VoiceAutoSpeakEffect(
        latestAssistantMessageId = latestAssistant?.id,
        latestAssistantText = latestAssistant?.text,
        isStreaming = isLoading,
        viewModel = viewModel,
    )
}
