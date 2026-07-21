# Agent Handover: Sight Buddy (Native Android)

**To the next agent:**  
Sight Buddy is a Kotlin + Jetpack Compose accessibility app for AI-assisted vision. All cloud LLM calls go through the Supabase Edge `chat` proxy; the Android app never holds an OpenAI API key.

## 1. Build variants (3-tier environment)

| Variant | Application ID | Supabase | IDs & Integrity | Crashlytics | Where it lives |
|---------|---------------|----------|-----------------|-------------|---------------|
| **devDebug** | `com.drophouse.sightbuddy.debug` | Mock (`ghwkdczqihxwhtynpwmd`) | Mock SSAID, `"test-token"` | No | Local laptop / emulator |
| **prodRelease** (Beta) | `com.drophouse.sightbuddy` | Production | Real SSAID, Play Integrity API | Yes (Firebase) | Play Console Internal Testing → Open Beta |
| **prodRelease** (Release) | `com.drophouse.sightbuddy` | Production | Real SSAID, Play Integrity API | Yes (Firebase) | Promoted from Beta in Play Console |

Build command: `./gradlew assembleDevDebug` (local) or `./gradlew bundleProdRelease` (CI/Beta/Release).

## 2. Core features (implemented)

- **Object recognition**: EfficientDet-Lite0 (TFLite, COCO). YUV→RGB pipeline, rotation-aware.
- **Find objects**: Voice target + proximity haptics; local matching first; OpenAI fallback via `ObjectCommandResolver` + `OpenAiTransport`.
- **Read text / Text chat**: ML Kit OCR; with LLM on, document Q&A uses **gpt-4o-mini**. With LLM off, local OCR only.
- **Speech-to-text**: local **Whisper base.en int8 + silero VAD** (sherpa-onnx) when model files are present (`filesDir/stt/`, ~154 MB, runtime download from `STT_MODEL_BASE_URL`); falls back to the Android system recognizer otherwise. Mic is tap-to-start/tap-to-stop by default ("Hold to speak" setting restores press-and-hold). Auto-stop: 10 s without speech; 60 s max per utterance. See `core/stt/` (`VoiceInputService`, `WhisperEngine`, `SttModelManager`).
- **Image chat**: Vision messages (base64 JPEG) via **gpt-4o-mini** through the Supabase Edge proxy.
- **Vision utilities**: Color ID (HSV), light level (Y-plane luma).
- **Feedback**: Anonymous feedback via `FeedbackTransport` → Supabase Edge `feedback` function.
- **Accessibility**: TalkBack semantics, TTS with cooldown, haptics, optional high-contrast / button nav (button nav ON by default). High contrast masks the camera preview but the camera keeps running — analyzers need frames.
- **In-app updates**: Flexible update prompt via Play In-App Updates API (prod only).
- **Zero tracking**: no analytics or attribution SDKs. Advertising-ID permissions are stripped in the manifest and Firebase Analytics collection is disabled (`firebase_analytics_collection_enabled=false`); the analytics dependency exists only because Crashlytics ships with it. (An AppsFlyer + consent-gated analytics stack was built in July 2026 for a planned ad campaign, then removed when the project was open-sourced instead — see Worklog Phase 10/12.)

## 3. Architecture

### DI (interface-driven, no framework)

Environment-specific behavior is injected via flavor source sets:

| Interface | dev implementation | prod implementation |
|-----------|-------------------|---------------------|
| `DeviceIdProvider` | `MockDeviceIdProvider` (hardcoded UUID) | `SsaidDeviceIdProvider` (Android SSAID) |
| `IntegrityTokenProvider` | `MockIntegrityTokenProvider` (`"test-token"`) | `PlayIntegrityTokenProvider` (Standard API, warm-up in constructor) |
| `InAppUpdateChecker` | `NoOpUpdateChecker` | `PlayInAppUpdateChecker` (flexible flow) |

Factory functions in `di/EnvironmentModule.kt` (one per flavor): `createDeviceIdProvider(context)`, `createIntegrityTokenProvider(context)`, `createInAppUpdateChecker(context)`.

### Source set layout

```
app/src/main/java/    # shared code (interfaces, all features)
app/src/dev/java/     # mock providers (EnvironmentModule.kt)
app/src/prod/java/    # real providers (EnvironmentModule.kt)
app/src/prod/         # google-services.json (Firebase, Crashlytics)
```

### Play Integrity flow

1. **App**: `PlayIntegrityTokenProvider` calls `StandardIntegrityManager.prepareIntegrityToken()` in the constructor (background warm-up). Each `getToken()` call requests a fresh token via the warmed provider.
2. **Server**: `_shared/integrity.ts` verifies the token via Google's `decodeIntegrityToken` API using the `GOOGLE_SERVICE_ACCOUNT_KEY` secret. Checks package name + `MEETS_DEVICE_INTEGRITY`.
3. **Dev bypass**: `"test-token"` is accepted only when the Supabase project has `ALLOW_TEST_INTEGRITY_BYPASS=true` (set on the dev project only).

### LLM / Supabase

- **`OpenAiTransport`**: POSTs to `/functions/v1/chat` with `install_id` + `integrity_token` via `IntegrityTokenProvider`.
- **`FeedbackTransport`**: POSTs to `/functions/v1/feedback` with same auth pattern.
- **Quota**: Edge function manages `public.llm_quota`; app handles HTTP 429.
- **Rate limiting**: In-memory burst limiter (12 req/min per install_id) in the chat function.
- **JWT verification**: `verify_jwt = true` on both Edge functions — Supabase validates the anon key JWT before forwarding.
- **Delete data**: `DeleteDataTransport` → Edge `delete-my-data` removes `llm_quota` + `app_feedback`, sets `install_restrictions` (2-day AI block). `chat` / `feedback` return `403` + `install_restricted`.
- **Repo layout**: `supabase/migrations/`, `supabase/functions/chat/`, `supabase/functions/feedback/`, `supabase/functions/delete-my-data/`, `supabase/functions/_shared/`, `supabase/config.toml`.

## 4. Technical stack

| Area | Implementation |
|------|----------------|
| UI | Jetpack Compose, Material 3 |
| State | `StateFlow` / `collectAsState`; chat VMs are activity-scoped |
| Camera | CameraX; capacity-1 `Channel` for frames (`CameraXManager`) |
| Local AI | TFLite (EfficientDet-Lite0), ML Kit text recognition |
| Cloud LLM | OpenAI **gpt-4o-mini** via Supabase Edge `chat`; OkHttp |
| Crash reporting | Firebase Crashlytics (prod only) + Play Vitals |
| Integrity | Play Integrity Standard API (prod) → server-side verification |
| In-app updates | Play In-App Updates API, flexible flow (prod only) |
| Build | AGP 9.1, Kotlin 2.2, Gradle version catalog |
| CI/CD | GitHub Actions (`.github/workflows/staging.yml`) |
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

There is **no API key in the build**: cloud AI is bring-your-own-key, entered by
the user at runtime and stored encrypted on-device (`ApiKeyStore`).

### GitHub Actions secrets

`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`

### Supabase secrets

```bash
npx supabase secrets set OPENAI_API_KEY=sk-...
npx supabase secrets set GOOGLE_SERVICE_ACCOUNT_KEY='{ ... }'
# Dev project only (enables devDebug "test-token"):
npx supabase secrets set ALLOW_TEST_INTEGRITY_BYPASS=true
```

### Deploy backend

```bash
npx supabase link
npx supabase db push
npx supabase functions deploy chat
npx supabase functions deploy feedback
npx supabase functions deploy delete-my-data
```

## 6. Rollout procedure

1. Merge to `staging` branch → CI builds `prodRelease` AAB
2. Download artifact, upload to Play Console Internal Testing Track
3. Staged rollout: 1% → 10% → 100% over 48 hours
4. **Halt criteria**: crash rate > 0.1%, missing TalkBack labels, Supabase 5xx errors
5. If halted: fix forward to v+1, re-run CI, restart staged rollout

## 7. Security model

- Zero tracking: no analytics/attribution SDKs; AD_ID permissions stripped; Firebase Analytics collection disabled.
- OpenAI key: **server-side only** (Supabase Edge secret)
- Supabase anon key: embedded in BuildConfig (public by design, gated by RLS)
- Play Integrity: Standard API on device → server-side verification via Google API; `"test-token"` disabled on prod Edge unless `ALLOW_TEST_INTEGRITY_BYPASS=true`
- JWT verification: enabled on all Edge functions
- Rate limiting: 12 requests/minute per install_id (in-memory, chat function)
- R8 minification: enabled on release builds
- No certificate pinning (standard HTTPS)
- Zero PII retention (images and audio processed in memory only)

## 8. Files to read first

`MainActivity.kt`, `OpenAiTransport.kt`, `di/DeviceIdProvider.kt`, `di/IntegrityTokenProvider.kt`, `di/InAppUpdateChecker.kt`, `di/EnvironmentModule.kt` (both flavors), `app/build.gradle.kts`, `supabase/functions/_shared/integrity.ts`, `supabase/functions/chat/index.ts`, `.github/workflows/staging.yml`.
