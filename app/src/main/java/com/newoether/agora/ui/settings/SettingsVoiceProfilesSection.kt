package com.newoether.agora.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.newoether.agora.data.VoiceSettings
import com.newoether.agora.service.VoiceProfile
import com.newoether.agora.service.VoiceProfileStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Voice profiles" section for SettingsVoicePage: list, add, edit, delete,
 * and choose the default profile. Insert this composable inside the
 * existing SettingsVoicePage column.
 */
@Composable
fun SettingsVoiceProfilesSection() {
    val context = LocalContext.current
    val store = remember { VoiceProfileStore(context) }
    val scope = rememberCoroutineScope()

    val profiles by store.profiles.collectAsState(initial = emptyList())
    val defaultId by store.defaultProfileId.collectAsState(initial = null)

    var editorTarget by remember { mutableStateOf<VoiceProfile?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Voice profiles", style = MaterialTheme.typography.titleMedium)
        Text(
            "Save multiple voice endpoints and pick a default. " +
                "Individual chats can override this.",
            style = MaterialTheme.typography.bodySmall,
        )

        profiles.forEach { profile ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = profile.id == defaultId,
                        onClick = { scope.launch { store.setDefaultProfile(profile.id) } },
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(profile.name, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            listOf(
                                if (profile.endpointKind == VoiceSettings.ENDPOINT_KIND_OPENAI) "OpenAI speech" else "Magpie zeroshot",
                                profile.model,
                                profile.endpointUrl,
                            ).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    IconButton(onClick = {
                        editorTarget = profile
                        showEditor = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                    IconButton(onClick = {
                        scope.launch {
                            store.deleteProfile(profile.id)
                            profileReferenceFile(context, profile.id).delete()
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }

        Button(onClick = {
            editorTarget = null
            showEditor = true
        }) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Add voice profile")
        }
    }

    if (showEditor) {
        VoiceProfileEditorDialog(
            existing = editorTarget,
            onDismiss = { showEditor = false },
            onSave = { profile ->
                scope.launch {
                    if (editorTarget == null) store.addProfile(profile)
                    else store.updateProfile(profile)
                }
                showEditor = false
            },
        )
    }
}

/** Per-profile reference WAV location inside app-private storage. */
internal fun profileReferenceFile(context: android.content.Context, profileId: String): File =
    File(File(context.filesDir, "voice_profiles"), "$profileId.wav")

@Composable
private fun VoiceProfileEditorDialog(
    existing: VoiceProfile?,
    onDismiss: () -> Unit,
    onSave: (VoiceProfile) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // A new profile gets its id up front so a picked reference WAV can be stored under it.
    val draftId = remember { existing?.id ?: java.util.UUID.randomUUID().toString() }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var kind by remember { mutableStateOf(existing?.endpointKind ?: VoiceSettings.ENDPOINT_KIND_MAGPIE) }
    var endpoint by remember { mutableStateOf(existing?.endpointUrl ?: VoiceSettings.DEFAULT_MAGPIE_URL) }
    var key by remember { mutableStateOf(existing?.apiKey ?: "") }
    var model by remember { mutableStateOf(existing?.model ?: "") }
    var referencePath by remember { mutableStateOf(existing?.referenceWavPath ?: "") }

    val pickWav = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val dest = profileReferenceFile(context, draftId)
            withContext(Dispatchers.IO) {
                dest.parentFile?.mkdirs()
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
            }
            referencePath = dest.absolutePath
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Add voice profile" else "Edit voice profile") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == VoiceSettings.ENDPOINT_KIND_MAGPIE,
                        onClick = { kind = VoiceSettings.ENDPOINT_KIND_MAGPIE },
                        label = { Text("Magpie zeroshot") },
                    )
                    FilterChip(
                        selected = kind == VoiceSettings.ENDPOINT_KIND_OPENAI,
                        onClick = { kind = VoiceSettings.ENDPOINT_KIND_OPENAI },
                        label = { Text("OpenAI speech") },
                    )
                }
                OutlinedTextField(value = endpoint, onValueChange = { endpoint = it }, label = { Text("Endpoint URL") }, singleLine = true)
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("API key (blank = use main voice key)") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                )
                OutlinedTextField(value = model, onValueChange = { model = it }, label = { Text("Model") }, singleLine = true)
                if (kind == VoiceSettings.ENDPOINT_KIND_MAGPIE) {
                    Text(
                        if (referencePath.isBlank()) "No reference WAV (uses main voice reference)"
                        else "Reference WAV set for this profile",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(onClick = { pickWav.launch("audio/*") }) { Text("Choose WAV") }
                        if (referencePath.isNotBlank()) {
                            TextButton(onClick = { referencePath = "" }) { Text("Clear") }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank() && endpoint.isNotBlank(),
                onClick = {
                    onSave(
                        (existing ?: VoiceProfile(id = draftId, name = name)).copy(
                            name = name.trim(),
                            endpointKind = kind,
                            endpointUrl = endpoint.trim(),
                            apiKey = key.trim(),
                            model = model.trim(),
                            referenceWavPath = referencePath,
                        )
                    )
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
