# Architecture notes: Sight Buddy (native Android)

Kotlin + Jetpack Compose accessibility app for AI-assisted vision. **There is no backend.** Everything runs on-device except optional cloud AI, which calls OpenAI **directly using the user's own API key** (BYOK). Earlier versions used a Supabase proxy; that was removed when the project was open-sourced.

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
| **Discover objects** | EfficientDet-Lite0 (TFLite, COCO). YUV→RGB without a JPEG round-trip, rotation-aware |
| **Find objects** | Voice target + proximity haptics and directional earcons; local label matching first, optional LLM fallback via `ObjectCommandResolver` |
| **Text chat** | ML Kit OCR on-device; character-indexed playback (`TextScriptPlayer`); optional LLM Q&A about the captured text |
| **Image chat** | Vision request (base64 JPEG) to OpenAI with the user's key |
| **Scan colour** | Median of a centre patch sampled straight from the YUV planes, mapped to colour names (incl. brown/beige); on-screen focus frame |
| **Scan light** | Y-plane luma average |
| **Speech-to-text** | Whisper `base.en` int8 + Silero VAD via sherpa-onnx (`core/stt/`). Models are opt-in (~154 MB, downloaded on request); falls back to the Android system recogniser until present |

Each carousel feature can be hidden in Settings, with a guard that keeps at least one enabled.

## 3. Architecture

### Bring-your-own-key

- [`ApiKeyStore`](app/src/main/java/com/example/sightbuddy/core/ApiKeyStore.kt) — the user's OpenAI key, encrypted with AES-256-GCM using a non-exportable Android Keystore key.
- [`OpenAiTransport`](app/src/main/java/com/example/sightbuddy/core/OpenAiTransport.kt) — POSTs directly to `api.openai.com/v1/chat/completions`. Distinct spoken errors for 401 (bad key) and 429 (rate limit). The key is never logged.
- **Key presence is the feature flag**: with a key saved, Image chat appears and Text chat gains AI Q&A; remove it and the app is fully local.
- Model is read **per request** from `SettingsManager`, so the Settings picker (Fast / Balanced / Most capable) applies with no restart.

### Speech input

`VoiceInputService` is the single entry point. With models present it records 16 kHz mono via `AudioRecord`, gates on Silero VAD (10 s no-speech auto-stop, 60 s cap) and transcribes with Whisper; otherwise it proxies to the system recogniser. Same flow surface either way (`recognizedText`, `isListening`, `events`).

**Mic-first capture** ("ask straight away"): pressing Ask with nothing captured records the question, snaps the frame on release, then pairs frame + transcription in an order-independent `LaunchedEffect`. The request itself runs in a separate scope — consuming the trigger changes the effect's keys, so running it inline would cancel the call mid-flight.

### Flavor split (no DI framework)

Only Crashlytics differs, via one factory function per source set:

```
app/src/main/java/   # everything shared
app/src/dev/java/    # createCrashReporter() → no-op
app/src/prod/java/   # createCrashReporter() → FirebaseCrashlytics
app/src/prod/        # google-services.json (not committed; builds fine without)
```

### Audio discipline

Async responses are guarded by `featureStillActive(mode)` so a late API reply can't speak after the user leaves; opening Settings, switching feature, or backgrounding cuts audio and cancels in-flight requests. App speech is suppressed while Settings/Help/onboarding are open — Settings' own confirmations opt in via `speak(force = true)`.

### Camera pipeline

`CameraXManager` uses a capacity-1 `Channel` with `DROP_OLDEST` and explicit frame ownership accounting (emitted/consumed/dropped/closed) to prevent backpressure stalls. Every mode closes its frame exactly once in a `finally`.

## 4. Stack

| Area | Implementation |
|---|---|
| UI | Jetpack Compose, Material 3 |
| State | `StateFlow` / `collectAsState` |
| Camera | CameraX |
| On-device AI | TFLite (EfficientDet-Lite0), ML Kit OCR, sherpa-onnx (Whisper + Silero VAD) |
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

- No accounts, no analytics, no advertising SDKs; AD_ID permissions are stripped in the manifest and Firebase Analytics collection is disabled.
- Camera frames and audio are processed in memory and never uploaded by the app.
- The only outbound data is user-initiated AI requests (to OpenAI, on the user's own key) and anonymous crash reports (opt-out in Settings → Privacy).
- The speech models are fetched once from a public GitHub release.

## 7. Files worth reading first

`MainActivity.kt` (orchestration), `core/ApiKeyStore.kt`, `core/OpenAiTransport.kt`, `core/stt/VoiceInputService.kt`, `core/SettingsManager.kt`, `ui/screens/HomeScreen.kt`, `app/build.gradle.kts`.
