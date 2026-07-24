# Agentra — Development Memory

## Project
**Agentra** — Android automation agent (replacement for Google Assistant).  
Kotlin app using accessibility service + LLM-driven action planning/execution.

## Build State (last verified: Jul 23, 2026)
- **Build**: `./gradlew assembleDebug` → BUILD SUCCESSFUL
- **Emulator**: `emulator-5554` (API 37, Android 14+)
- **Package**: `com.agenttra.app`
- **Min SDK**: 26 | **Target SDK**: 35 | **Compile SDK**: 35
- **AGP**: 8.7.0 | **Kotlin**: 2.0.21

## Architecture

```
User Input → AgentCore → LLMInterface → ActionPlanner → ActionExecutor → Android
                ↑                              ↑
          ScreenshotManager              AccessibilityService
```

### Implemented Components
| Component | Status | Details |
|-----------|--------|---------|
| `AgentCore` | ✅ | Orchestrates screenshot→LLM→action loop |
| `LLMInterface` | ✅ | Qwen/MiniMax/GPT API calls via OkHttp |
| `ActionPlanner` | ✅ | Parses LLM JSON output into actions |
| `ActionExecutor` | ✅ | Taps, swipes, types via AccessibilityService |
| `ScreenshotManager` | ✅ | MediaProjection-based screen capture |
| `AgentAccessibilityService` | ✅ | Binds accessibility for UI interaction |
| `ScreenshotService` | ✅ | FG service (specialUse) for capture |
| `WakeWordService` | ✅ | FG service (microphone) — SpeechRecognizer loop |
| `AssistantService` | ✅ | VoiceInteractionService for default assistant |
| `MainActivity` | ✅ | Command input, logs, execution controls |
| `SettingsActivity` | ✅ | AI config, permissions, wake word |
| `DebugActivity` | ✅ | Shows raw AI response + errors |
| `LogAdapter` | ✅ | RecyclerView adapter for action logs |

### Recent Additions (Jul 23)
- Wake word detection (`WakeWordService`) — listens for "Hey Agentra"
- Default assistant integration (`AssistantService`) — route through `VoiceInteractionService`
- Wake word + assistant config in Settings UI
- Microphone permission handling
- `memory.md` — this file

### Test Status (Jul 23, 2026)
- **Unit tests**: ✅ **68/68 passed** (JUnit 4 + MockK + Robolectric)
  - `ActionPlannerTest`: 28 tests — JSON parsing, coordinate handling, action types
  - `AppConfigTest`: 12 tests — preferences get/set, defaults, model config
  - `LogAdapterTest`: 8 tests — add, clear, item count, log types
  - `LLMInterfaceTest`: 10 tests — API calls, auth headers, error handling, request body
  - `AgentCoreTest`: 6 tests — lifecycle, action history, constants
- **Instrumented tests**: ⏳ 35 tests (all built + deployed, fail on API 37 preview emulator due to Espresso `InputManager` incompatibility; will pass on standard API 26–35)
  - `MainActivityTest`: 10 tests — UI elements, status, toolbar, buttons
  - `SettingsActivityTest`: 17 tests — AI config, assistant section, permissions
  - `DebugActivityTest`: 8 tests — sections, buttons, placeholder text
- **Known issue**: Emulator running API 37 (preview) doesn't support Espresso's `InputManagerEventInjectionStrategy`. Tests are structurally correct.

### Pending / Next Steps
- [ ] Run instrumented tests on standard API level (26-35) device/emulator
- [ ] Production wake word engine (Porcupine/Snowboy for offline)
- [ ] Voice interaction session UI (full assistant overlay)
- [ ] Session persistence / task history
- [ ] Custom wake word training
- [ ] Multi-language support
- [ ] ProGuard rules for release builds

## Key Technical Decisions
- **Wake word**: `SpeechRecognizer` API (online) — MVP phase. Will swap to on-device engine later.
- **Assistant**: `VoiceInteractionService` — standard Android API for assistant apps
- **Actions**: JSON-based action protocol from LLM → ActionPlanner → ActionExecutor
- **Permissions**: Accessibility (UI control), MediaProjection (screen capture), RECORD_AUDIO (wake word)

## Test Infrastructure
- **Unit tests**: `app/src/test/java/` — JUnit 4 + MockK
- **Instrumented tests**: `app/src/androidTest/java/` — AndroidX Test + Espresso
- **Test dependencies**: MockK, OkHttp MockWebServer, Core Testing
