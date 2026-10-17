# Voice patch integration report

Integrated from `agora-voice-patch(2).zip` into this Agora source tree:
- Added VoiceConfig.kt, VoiceController.kt, SettingsVoicePage.kt, MessageSpeakAction.kt.
- Added seven voice preference keys and SettingsManager flows/save functions.
- Added Voice settings category and SettingsTwoPane destination.
- Added English voice strings.

Chat wiring completed:
- Added `AssistantSpeakAction` to the assistant message action row beside the existing copy/regenerate controls. It uses Agora's actual `ChatMessage.text` field and the existing `ChatViewModel`.
- Added `VoiceAutoSpeakEffect` to `ChatApp`, driven by the latest model message / streaming message and the chat's `isLoading` state.
- Threaded an optional `ChatViewModel` through `ChatApp` → `MessageList` → `MessageItem` → `AssistantMessageContent`; the optional default keeps the read-only remote conversation call site compatible.

Build status:
The source has not been build-verified. The earlier `./gradlew assembleFossDebug` attempt could not download its configured Gradle distribution because network DNS access to services.gradle.org was unavailable.
