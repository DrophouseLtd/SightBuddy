# Sight Buddy

**AI-assisted vision in your pocket — an Android accessibility app for people with low vision, built camera-first, voice-first, and TalkBack-first.**

> **Status: alive on Google Play as a free, fully local app — active development ended (July 2026).**
> Sight Buddy ran as an open beta in the UK and Europe with a paid cloud-AI backend. We concluded the product wasn't commercially viable and shut the backend down — the honest story is below. But because the app was designed local-first, we didn't have to kill it: **v2.0.0 removed the cloud features and stays published on Play, free forever** — object detection, OCR reading, colour and light scanning, and fully on-device Whisper speech recognition, with zero servers, zero cost, and zero data collection.

## What it does

| Mode | What it does |
|------|--------------|
| **Read text** | Reads printed text aloud with seekable playback (on-device OCR) |
| **Discover objects** | Names what the camera sees as you move (on-device TFLite) |
| **Find objects** | Say "keys" or "cup" — directional audio and haptics guide you to it |
| **Scan colour** | Speaks the colour at the centre of the frame |
| **Scan light** | Reports bright, dim, or dark lighting |

Every surface works with TalkBack, spoken announcements, haptics, high-contrast themes, adjustable speech rate (1×–3×), and button navigation instead of swipes.

*(The v1.x beta also had cloud-LLM scene and document Q&A — "Image chat" / "Text chat". That code is still in the repo, and the Supabase Edge backend that powered it is in [`supabase/`](supabase/); wire up your own project and OpenAI key to revive it.)*

## The post-mortem: why we stopped

We built Sight Buddy because everyday vision tasks — reading a label, finding dropped keys, knowing whether the lights are on — still depend on sighted help or clunky tools. The beta worked, real users used it, and the accessibility-first design decisions held up.

What didn't hold up was the business:

- **The economics of cloud AI for this audience are unforgiving.** The most valuable features (scene Q&A, document Q&A) ride on per-request LLM inference that someone has to pay for, indefinitely. Our users — visually impaired people — are exactly the audience that should *not* be gated behind a subscription, and a small beta gave no path to covering inference costs with goodwill alone.
- **The spreadsheet said no.** Before spending on user acquisition we ran the numbers — acquisition cost against zero revenue per user and an ongoing per-user inference bill — and there was no honest case for monetising. We stopped there.
- **What we'd do differently:** validate willingness-to-pay (or a grant/partnership funding model — councils, charities, assistive-tech distributors) *before* building the cloud features, and lean even harder into local-first. The on-device half of this app costs nothing to run forever; that half was the right product.

The silver lining of local-first architecture: sunsetting the backend didn't brick the product. We cut the cloud features, shipped v2.0.0, and **left the app on the store** — the on-device half costs nothing to run and keeps serving the people it was built for, indefinitely.

## What's technically interesting here

If you're mining this repo for parts, these are the good bits:

- **On-device Whisper speech-to-text on Android** — [`app/src/main/java/com/example/sightbuddy/core/stt/`](app/src/main/java/com/example/sightbuddy/core/stt/). Whisper base.en (int8) + Silero VAD via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), wrapped in a push-to-talk/tap-to-talk service with silence auto-stop (10 s) and a hard cap (60 s). Model files (~154 MB) auto-download once from this repo's release assets; the service transparently falls back to the system `SpeechRecognizer` until they're present. This replaced the OEM recognizer lottery with consistent, offline transcription.
- **A zero-key client security model** — the APK contains no OpenAI key. All LLM calls go through a Supabase Edge proxy ([`supabase/functions/chat/`](supabase/functions/chat/index.ts)) that verifies **Play Integrity** tokens server-side, enforces per-install daily token quotas and burst rate limits, and supports a user-facing delete-my-data flow. The client holds only a public anon key.
- **Accessible Compose UI patterns** — every control TalkBack-labelled, a full-screen high-contrast mode that masks the camera preview *without* stopping frame analysis, carousel navigation with an optional button-based alternative, and TTS with per-announcement cooldowns and a character-indexed text playback player (seek/pause survives speech-rate changes by design).
- **A disciplined CameraX frame pipeline** — YUV→RGB conversion without lossy JPEG round-trips, rotation-aware TFLite input, and a capacity-1 frame `Channel` with explicit ownership/closure accounting to prevent backpressure stalls (the war stories are in [Worklog.md](Worklog.md)).
- **Environment isolation without a DI framework** — dev/prod product flavors swap mock vs. real implementations (device IDs, Play Integrity, in-app updates) through four factory functions. Boring, explicit, testable.

Deeper architecture notes: [AGENT_HANDOVER.md](AGENT_HANDOVER.md). Full development history: [Worklog.md](Worklog.md).

## Building it

Requirements: JDK 17+, Android SDK 36. No Android Studio required (`gradlew` handles everything; the sherpa-onnx AAR downloads automatically at build time).

```bash
# Local development build (mock backend expectations, no keys needed)
./gradlew assembleDevDebug
```

- Everything the shipped app does (object detection, OCR reading, colour/light, Whisper STT) works with zero configuration. Whisper model files download on first run from this repo's `stt-models-v1` release.
- **Reviving the retired cloud chat**: the full backend lives in [`supabase/functions/`](supabase/functions/) (`chat`, `feedback`, `delete-my-data`). Deploy it to your own Supabase project with an `OPENAI_API_KEY` secret, re-enable the LLM flag in `SettingsManager`, and point `SUPABASE_URL` / `SUPABASE_PUBLISHABLE_KEY` in `local.properties` at it.
- Release signing expects `KEYSTORE_*` entries in `local.properties` (see [AGENT_HANDOVER.md](AGENT_HANDOVER.md)).

## Licenses

Code is [MIT](LICENSE) © 2026 Drophouse Ltd.

Bundled/downloaded third-party components keep their own licenses: OpenAI Whisper models (MIT; ONNX export via sherpa-onnx, Apache-2.0), Silero VAD (MIT), sherpa-onnx runtime (Apache-2.0), EfficientDet-Lite0 (Apache-2.0, [legal/](legal/)), tutorial & sound-effect audio generated with Voicertool (free incl. commercial use; terms archived in `app/src/main/assets/licenses/`).
