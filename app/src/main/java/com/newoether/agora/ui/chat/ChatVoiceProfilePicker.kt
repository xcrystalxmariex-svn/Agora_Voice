package com.newoether.agora.ui.chat

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.newoether.agora.service.VoiceProfileStore
import kotlinx.coroutines.launch

/**
 * Per-chat voice profile selector, opened from the chat top bar's options menu.
 * "App default" clears the chat's override so it follows the default profile
 * (or the single endpoint in Settings > Voice when no profiles are saved).
 */
@Composable
fun ChatVoiceProfileDialog(chatId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val store = remember { VoiceProfileStore(context) }
    val scope = rememberCoroutineScope()

    val profiles by store.profiles.collectAsState(initial = emptyList())
    val overrides by store.chatOverrides.collectAsState(initial = emptyMap())
    val defaultId by store.defaultProfileId.collectAsState(initial = null)
    val overrideId = overrides[chatId]?.takeIf { id -> profiles.any { it.id == id } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Voice for this chat") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (profiles.isEmpty()) {
                    Text(
                        "No voice profiles saved yet. Add them in Settings > Voice.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                VoiceProfileOption(
                    label = "App default" +
                        (profiles.firstOrNull { it.id == defaultId }?.let { " (${it.name})" } ?: ""),
                    selected = overrideId == null,
                    onClick = {
                        scope.launch { store.clearChatOverride(chatId) }
                        onDismiss()
                    },
                )
                profiles.forEach { profile ->
                    VoiceProfileOption(
                        label = profile.name,
                        selected = overrideId == profile.id,
                        onClick = {
                            scope.launch { store.setChatOverride(chatId, profile.id) }
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun VoiceProfileOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
