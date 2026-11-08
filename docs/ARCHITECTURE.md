# Architecture notes: Sight Buddy (native Android)

Kotlin + Jetpack Compose accessibility app for AI-assisted vision. **There is no backend.** Everything runs on-device: the AI answers come from **Gemma 4 E2B on the phone** when it is downloaded, or from OpenAI **directly using the user's own API key** (BYOK) when the user allows it. Earlier versions used a Supabase proxy; that was removed when the project was open-sourced.

## 1. Build variants

| Variant | Application ID | Crashlytics | Signing |
|---------|----------------|-------------|---------|
| **devDebug** | `com.drophouse.sightbuddy.debug` | No | debug |
| **prodRelease** | `com.drophouse.sightbuddy` | Yes | upload key, or debug if no keystore configured |

```bash
./gradlew assembleDevDebug      # local development
./gradlew assembleProdRelease   # release APK (direct download)
./gradlew bundleProdRelease     # release AAB (Google Play)
```

Release builds are **arm64-only** (`minSdk 30` ⇒ every supported device is arm64), which keeps the direct-download APK reasonable.

## 2. Features

| Feature | Implementation |
|---|---|
| **Discover objects** | EfficientDet-Lite0 (COCO) on MediaPipe Tasks. YUV→RGB without a JPEG round-trip, rotation-aware |
| **Find objects** | Voice target + proximity haptics and directional earcons; local label matching first, optional LLM fallback via `ObjectCommandResolver` |
| **Text chat** | ML Kit OCR on-device; character-indexed playback (`TextScriptPlayer`); optional LLM Q&A about the captured text |
| **Image chat** | Photo + question to OpenAI (user's key, Use API on, online) or to Gemma 4 E2B on the device |
| **Memories** | Save chat stores the chat on the phone (`core/MemoryStore.kt`); Gemma names it; list, read, rename, share, delete. Share sends a chat as plain text through Android's share sheet |
| **Scan colour** | Median of a centre patch sampled straight from the YUV planes, mapped to colour names (incl. brown/beige); on-screen focus frame |
| **Scan light** | Y-plane luma average |
| **Speech-to-text** | Chosen in Settings: Whisper `base.en` int8 via sherpa-onnx (default), Gemma 4 E2B, or the Android recogniser; OpenAI transcription when enabled. English only on-device |
| **Model setup** | `core/ModelSetup.kt` picks a tier from memory and free space (Gemma + Whisper / Whisper / nothing; Finnish: nothing) and offers it with sizes and free space. Downloads run under `ModelDownloadService` |

Each carousel feature can be hidden in Settings, with a guard that keeps at least one enabled.

## 3. Architecture

### Bring-your-own-key

- [`ApiKeyStore`](app/src/main/java/com/example/sightbuddy/core/ApiKeyStore.kt) — the user's OpenAI key, encrypted with AES-GCM using a non-exportable Android Keystore key.
- [`OpenAiTransport`](app/src/main/java/com/example/sightbuddy/core/OpenAiTransport.kt) — POSTs directly to `api.openai.com/v1/chat/completions`. Distinct spoken errors for 401 (bad key) and 429 (rate limit). The key is never logged.
- **Use API** switch: with a key saved and the switch on, OpenAI answers when online; with it off, nothing goes to OpenAI and Gemma (if downloaded) answers instead. See "On-device AI".
- Model is read **per request** from `SettingsManager`, so the Settings picker (Fast / Balanced / Most capable) applies with no restart.

### On-device AI

- **Gemma 4 E2B** (`core/llm/LocalGemma.kt`) on LiteRT-LM: image questions,
  text questions, the Find objects fallback, speech, chat names. Downloaded
  from Hugging Face `litert-community` (Apache 2.0), pinned, resumable.
- **One router:** `OpenAiTransport.executeChatCompletion` sends each request to
  OpenAI (Use API on, key stored, online) or to Gemma (`LocalAnswerer`, the
  same OpenAI-shaped body). Logged under `AI-ROUTE`.
- **Answers are spoken as they are written:** Gemma's answer to a chat
  question streams (LiteRT-LM's `sendMessageAsync` callback); the app splits
  it into sentences (`SentenceStream`) and speaks each as soon as it is
  complete (`AnswerSpeaker`), through the app's voice or the screen reader.
  The whole answer is kept for the chat, Save chat and Share. OpenAI's
  answers come whole.
- **Use API** (Settings > Advanced > OpenAI): while off, nothing can be sent to
  OpenAI whatever key is stored; see `ApiGateTest`.
- AI features appear when either path is available: the key alone is no longer
  the feature flag.

### Speech input

`VoiceInputService` is the single entry point. With models present it records 16 kHz mono via `AudioRecord`, uses Silero VAD only to tell whether anything was said (silence does not end a recording: the user stops it, or the 60 s cap does) and transcribes with Whisper; otherwise it proxies to the system recogniser. Same flow surface either way (`transcripts`, `isListening`, `events`). Transcripts are one-time events, not state: the app collects them again each time it returns to the foreground, and state replayed the last question.

**Mic-first capture** ("ask straight away"): pressing Ask with nothing captured and Automatic capture on records the question, takes the picture once the recording ends (in Text chat a full-size photo: of the page, after guiding to it as Find page does, with Ask finds the page on; of everything in view with it off), then pairs picture + transcription in an order-independent `LaunchedEffect`. The request itself runs in a separate scope — consuming the trigger changes the effect's keys, so running it inline would cancel the call mid-flight.

### Flavor split (no DI framework)

Only Crashlytics differs, via one factory function per source set:

```
app/src/main/java/   # everything shared
app/src/dev/java/    # createCrashReporter() → no-op
app/src/prod/java/   # createCrashReporter() → FirebaseCrashlytics
app/src/prod/        # google-services.json (not committed; builds fine without)
```

### Audio discipline

Async responses are guarded by `featureStillActive(mode)` so a late API reply can't speak after the user leaves; opening Settings, switching feature, or backgrounding cuts audio and cancels in-flight requests. App speech is suppressed while Settings/Help/onboarding are open — Settings' own confirmations opt in via `speak(force = true)`. `TTSService` is the one place the app speaks: with the Speak with screen reader setting on, a screen reader running and the app in front, each line goes to the screen reader instead of the app's voice, through two invisible live-region nodes (`SpeechRoute`, `ScreenReaderSpeech`, `ScreenReaderLines`), each line whole; nothing goes to either during a recording. Text longer than Android's text-to-speech takes (4,000 characters) is not read by the app's voice, which says it is too long; the text stays on screen. When the app's language differs from the phone's, every window tags the text it hands to the screen reader with the app's language (`ui/ScreenReaderLocale.kt`, which wraps the Compose view's accessibility node provider), so TalkBack reads Finnish with a Finnish voice; stretches already tagged with another language keep theirs.

### Camera pipeline

`CameraXManager` uses a capacity-1 `Channel` with `DROP_OLDEST` and explicit frame ownership accounting (emitted/consumed/dropped/closed) to prevent backpressure stalls. Every mode closes its frame exactly once in a `finally`.

## 4. Stack

| Area | Implementation |
|---|---|
| UI | Jetpack Compose, Material 3 |
| State | `StateFlow` / `collectAsState` |
| Camera | CameraX |
| On-device AI | MediaPipe Tasks (EfficientDet-Lite0), ML Kit OCR, ML Kit Language Identification (on the phone, offline: text read aloud in its own language's voice), sherpa-onnx (Whisper + Silero VAD) |
| Cloud AI | OpenAI Chat Completions, direct, user's key |
| Crash reporting | Firebase Crashlytics (prod only, **no Analytics**, opt-out in Settings) |
| Build | AGP 9.1, Kotlin 2.2, Gradle version catalog |
| CI | GitHub Actions — builds and signs the AAB + APK, attaches them to a release |
| minSdk / targetSdk | 30 / 36 |

## 5. Configuration

### `local.properties` (never committed)

All entries are optional — a clone builds and runs without any of them.

```properties
sdk.dir=...

# Override the Whisper model download source (defaults to the public
# sightbuddy-stt-models release).
STT_MODEL_BASE_URL=<release URL serving the model files>

# Release signing. Omit these and release builds fall back to debug signing.
KEYSTORE_FILE=<path-to-keystore.jks>
KEYSTORE_PASSWORD=<password>
KEY_ALIAS=<alias>
KEY_PASSWORD=<password>
```

There is **no API key in the build** — cloud AI is bring-your-own-key, entered at runtime and stored encrypted on-device.

### GitHub Actions secrets

`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

## 6. Privacy model

- No accounts, no analytics of ours, no advertising SDKs; AD_ID permissions are stripped in the manifest and Firebase Analytics collection is disabled. Google's on-device libraries (ML Kit for text and language, MediaPipe Tasks for object detection) send Google anonymous usage and performance data (package name, app version, timings); there is no switch for it, and the privacy policy says so.
- Camera frames and audio are processed in memory and never uploaded by the app.
- The only outbound data is user-initiated AI requests (to OpenAI, on the user's own key) and anonymous crash reports (opt-out in Settings → Privacy).
- The speech models are fetched once from a public GitHub release.

## 7. Files worth reading first

`AppController.kt` (state and orchestration), `AppScreens.kt` (the screens), `MainActivity.kt` (wiring), `core/ApiKeyStore.kt`, `core/OpenAiTransport.kt`, `core/stt/VoiceInputService.kt`, `core/SettingsManager.kt`, `ui/screens/HomeScreen.kt`, `app/build.gradle.kts`.
