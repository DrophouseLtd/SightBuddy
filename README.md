# Sight Buddy

**AI-assisted vision in your pocket — an Android accessibility app for people with low vision, built camera-first, voice-first, and TalkBack-first.**

> **Status: development ended, open-sourced (July 2026).**
> Sight Buddy ran as an open beta on Google Play in the UK and Europe. We shut down the paid cloud backend after concluding the product wasn't commercially viable — the honest story is below. Because the app was designed local-first, **everything except cloud chat still works**: object detection, OCR reading, colour and light scanning, and fully on-device speech recognition run with no server at all.

<p>
  <img src="Assets/sightbuddyicon.png" width="96" alt="Sight Buddy icon">
</p>

## What it does

| Mode | What it does |
|------|--------------|
| **Image chat** | Describes the camera scene; answer follow-up questions by voice (cloud LLM) |
| **Text chat** | Reads printed text aloud (on-device OCR); voice Q&A about the capture (cloud LLM) |
| **Discover objects** | Names what the camera sees as you move (on-device TFLite) |
| **Find objects** | Say "keys" or "cup" — directional audio and haptics guide you to it |
| **Scan colour** | Speaks the colour at the centre of the frame |
| **Scan light** | Reports bright, dim, or dark lighting |

Every surface works with TalkBack, spoken announcements, haptics, high-contrast themes, adjustable speech rate (1×–3×), and button navigation instead of swipes.

## The post-mortem: why we stopped

We built Sight Buddy because everyday vision tasks — reading a label, finding dropped keys, knowing whether the lights are on — still depend on sighted help or clunky tools. The beta worked, real users used it, and the accessibility-first design decisions held up.

What didn't hold up was the business:

- **The economics of cloud AI for this audience are unforgiving.** The most valuable features (scene Q&A, document Q&A) ride on per-request LLM inference that someone has to pay for, indefinitely. Our users — visually impaired people — are exactly the audience that should *not* be gated behind a subscription, and a small beta gave no path to covering inference costs with goodwill alone.
- **We stopped at the moment of maximum honesty.** We had the next step fully built: attribution SDKs integrated, a GDPR consent flow implemented, ad campaigns planned for TikTok and Google. Before spending the first pound on acquisition we ran the numbers — acquisition cost against zero revenue per user and an ongoing per-user inference bill — and killed the plan instead of funding a treadmill. The tracking stack was removed the same week it was written; this repository ships with the original zero-tracking design.
- **What we'd do differently:** validate willingness-to-pay (or a grant/partnership funding model — councils, charities, assistive-tech distributors) *before* building the cloud features, and lean even harder into local-first. The on-device half of this app costs nothing to run forever; that half was the right product.

The silver lining of local-first architecture: sunsetting the backend didn't brick anyone's app. Existing installs keep every offline feature.

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

- **Offline features** (object detection, OCR reading, colour/light, Whisper STT) work with zero configuration. Whisper model files download on first run from this repo's `stt-models-v1` release.
- **Cloud chat features** require deploying your own backend: create a Supabase project, deploy [`supabase/functions/`](supabase/functions/) (`chat`, `feedback`, `delete-my-data`), set the `OPENAI_API_KEY` secret, run the migration, and point `SUPABASE_URL` / `SUPABASE_PUBLISHABLE_KEY` in `local.properties` at it. Without a backend the app simply reports cloud chat as unavailable — everything else runs.
- Release signing expects `KEYSTORE_*` entries in `local.properties` (see [AGENT_HANDOVER.md](AGENT_HANDOVER.md)).

## Licenses

Code is [MIT](LICENSE) © 2026 Drophouse Ltd.

Bundled/downloaded third-party components keep their own licenses: OpenAI Whisper models (MIT; ONNX export via sherpa-onnx, Apache-2.0), Silero VAD (MIT), sherpa-onnx runtime (Apache-2.0), EfficientDet-Lite0 (Apache-2.0, [legal/](legal/)), tutorial & sound-effect audio generated with Voicertool (free incl. commercial use; terms archived in `app/src/main/assets/licenses/`).
