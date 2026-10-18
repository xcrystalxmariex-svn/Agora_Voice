package com.newoether.agora.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.newoether.agora.R
import com.newoether.agora.data.VoiceSettings
import com.newoether.agora.service.VoiceController
import com.newoether.agora.ui.components.CircularBackButton
import com.newoether.agora.viewmodel.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsVoicePage(viewModel: ChatViewModel, onBack: () -> Unit) {
    val settings = viewModel.settings
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val controller = remember { VoiceController.get(context) }

    val enabled by settings.voiceEnabled.collectAsState(initial = false)
    val autoSpeak by settings.voiceAutoSpeak.collectAsState(initial = false)
    val endpointKind by settings.voiceEndpointKind.collectAsState(
        initial = VoiceSettings.ENDPOINT_KIND_MAGPIE,
    )
    val endpointUrl by settings.voiceEndpointUrl.collectAsState(
        initial = VoiceSettings.DEFAULT_MAGPIE_URL,
    )
    val apiKey by settings.voiceApiKey.collectAsState(initial = "")
    val model by settings.voiceModel.collectAsState(initial = "magpie-tts-zeroshot")
    val referencePath by settings.voiceReferenceWavPath.collectAsState(initial = "")

    val isSpeaking by controller.isSpeaking.collectAsState()
    val lastError by controller.lastError.collectAsState()
    var testing by remember { mutableStateOf(false) }
    var testOk by remember { mutableStateOf(false) }

    LaunchedEffect(enabled, autoSpeak, endpointKind, endpointUrl, apiKey, model, referencePath) {
        controller.updateSettings(
            VoiceSettings(
                enabled = enabled,
                endpointKind = endpointKind,
                endpointUrl = endpointUrl,
                apiKey = apiKey,
                model = model,
                referenceWavPath = referencePath,
                autoSpeak = autoSpeak,
            ),
        )
    }

    val pickWav = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val dest = File(context.filesDir, "voice_reference.wav")
            withContext(Dispatchers.IO) {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    dest.outputStream().use { input.copyTo(it) }
                }
            }
            settings.saveVoiceReferenceWavPath(dest.absolutePath)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_voice)) },
                navigationIcon = {
                    CircularBackButton(
                        onClick = onBack,
                        contentDescription = stringResource(R.string.back),
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 8.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            item {
                SettingsGroup(
                    title = stringResource(R.string.settings_voice),
                    items = listOf(
                        {
                            SettingsItem(
                                headlineContent = {
                                    Text(stringResource(R.string.voice_enable))
                                },
                                supportingContent = {
                                    Text(stringResource(R.string.voice_enable_desc))
                                },
                                trailingContent = {
                                    Switch(
                                        checked = enabled,
                                        onCheckedChange = {
                                            scope.launch { settings.saveVoiceEnabled(it) }
                                        },
                                    )
                                },
                            )
                        },
                        {
                            SettingsItem(
                                headlineContent = {
                                    Text(stringResource(R.string.voice_auto_speak))
                                },
                                supportingContent = {
                                    Text(stringResource(R.string.voice_auto_speak_desc))
                                },
                                trailingContent = {
                                    Switch(
                                        checked = autoSpeak,
                                        onCheckedChange = {
                                            scope.launch { settings.saveVoiceAutoSpeak(it) }
                                        },
                                    )
                                },
                            )
                        },
                    ),
                )
            }

            item {
                SettingsGroup(
                    title = stringResource(R.string.voice_endpoint_group),
                    items = buildList {
                        add {
                            SettingsItem(
                                headlineContent = {
                                    Text(stringResource(R.string.voice_endpoint_kind))
                                },
                                supportingContent = {
                                    Text(stringResource(R.string.voice_endpoint_kind_desc))
                                },
                            )
                        }
                        add {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                FilterChip(
                                    selected =
                                        endpointKind == VoiceSettings.ENDPOINT_KIND_MAGPIE,
                                    onClick = {
                                        scope.launch {
                                            settings.saveVoiceEndpointKind(
                                                VoiceSettings.ENDPOINT_KIND_MAGPIE,
                                            )
                                            settings.saveVoiceEndpointUrl(
                                                VoiceSettings.DEFAULT_MAGPIE_URL,
                                            )
                                        }
                                    },
                                    label = {
                                        Text(stringResource(R.string.voice_kind_magpie))
                                    },
                                )
                                FilterChip(
                                    selected =
                                        endpointKind == VoiceSettings.ENDPOINT_KIND_OPENAI,
                                    onClick = {
                                        scope.launch {
                                            settings.saveVoiceEndpointKind(
                                                VoiceSettings.ENDPOINT_KIND_OPENAI,
                                            )
                                        }
                                    },
                                    label = {
                                        Text(stringResource(R.string.voice_kind_openai))
                                    },
                                )
                            }
                        }
                        add {
                            Column(Modifier.fillMaxWidth()) {
                                SettingsItem(
                                    headlineContent = {
                                        Text(stringResource(R.string.voice_endpoint_url))
                                    },
                                )
                                OutlinedTextField(
                                    value = endpointUrl,
                                    onValueChange = {
                                        scope.launch { settings.saveVoiceEndpointUrl(it) }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    singleLine = true,
                                    placeholder = {
                                        Text(
                                            if (endpointKind ==
                                                VoiceSettings.ENDPOINT_KIND_MAGPIE
                                            ) {
                                                VoiceSettings.DEFAULT_MAGPIE_URL
                                            } else {
                                                "https://api.openai.com/v1/audio/speech"
                                            },
                                        )
                                    },
                                )
                            }
                        }
                        add {
                            Column(Modifier.fillMaxWidth()) {
                                SettingsItem(
                                    headlineContent = {
                                        Text(stringResource(R.string.voice_api_key))
                                    },
                                    supportingContent = {
                                        Text(stringResource(R.string.voice_api_key_desc))
                                    },
                                )
                                OutlinedTextField(
                                    value = apiKey,
                                    onValueChange = {
                                        scope.launch { settings.saveVoiceApiKey(it) }
                                    },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                )
                            }
                        }
                        if (endpointKind == VoiceSettings.ENDPOINT_KIND_OPENAI) {
                            add {
                                Column(Modifier.fillMaxWidth()) {
                                    SettingsItem(
                                        headlineContent = {
                                            Text(stringResource(R.string.voice_model))
                                        },
                                    )
                                    OutlinedTextField(
                                        value = model,
                                        onValueChange = {
                                            scope.launch { settings.saveVoiceModel(it) }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        singleLine = true,
                                        placeholder = { Text("tts-1") },
                                    )
                                }
                            }
                        }
                    },
                )
            }

            item {
                val hasRef = referencePath.isNotBlank() && File(referencePath).exists()
                SettingsGroup(
                    title = stringResource(R.string.voice_reference_group),
                    items = listOf(
                        {
                            SettingsItem(
                                headlineContent = {
                                    Text(
                                        if (hasRef) {
                                            File(referencePath).name
                                        } else {
                                            stringResource(R.string.voice_reference_none)
                                        },
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        if (hasRef) {
                                            stringResource(R.string.voice_reference_set_desc)
                                        } else {
                                            stringResource(R.string.voice_reference_desc)
                                        },
                                    )
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { pickWav.launch("audio/*") }) {
                                            Icon(
                                                Icons.Default.UploadFile,
                                                contentDescription = stringResource(
                                                    R.string.voice_reference_choose,
                                                ),
                                            )
                                        }
                                        if (hasRef) {
                                            IconButton(
                                                onClick = {
                                                    scope.launch {
                                                        settings.saveVoiceReferenceWavPath("")
                                                    }
                                                },
                                            ) {
                                                Icon(
                                                    Icons.Default.Delete,
                                                    contentDescription = stringResource(
                                                        R.string.voice_reference_remove,
                                                    ),
                                                )
                                            }
                                        }
                                    }
                                },
                            )
                        },
                    ),
                )
            }

            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(
                        onClick = {
                            testing = true
                            testOk = false
                            scope.launch {
                                try {
                                    controller.speak(
                                        "__voice_test__",
                                        "Hello! This is a test of the voice settings.",
                                    )
                                    testOk = true
                                } finally {
                                    testing = false
                                }
                            }
                        },
                        enabled = !testing && enabled && apiKey.isNotBlank() &&
                            (
                                endpointKind != VoiceSettings.ENDPOINT_KIND_MAGPIE ||
                                    referencePath.isNotBlank()
                                ),
                    ) {
                        if (testing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(R.string.voice_test))
                    }
                    Spacer(Modifier.height(8.dp))
                    when {
                        testing -> Text(
                            stringResource(R.string.voice_testing),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        lastError != null -> Text(
                            lastError.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        testOk && !isSpeaking -> Text(
                            stringResource(R.string.voice_test_ok),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }
}
