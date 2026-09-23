# Sight Buddy

[![Build](https://github.com/DrophouseLtd/SightBuddy/actions/workflows/ci.yml/badge.svg)](https://github.com/DrophouseLtd/SightBuddy/actions/workflows/ci.yml)

**A free, open-source assistive vision app for blind and low-vision users.**

Point your phone's camera to hear your surroundings described, read printed text aloud, find objects, and identify colours and lighting. It runs **on your device**: since 2.3.0 the AI answers can come from a model you download once, with no account and no internet. If you would rather use OpenAI, you can, with **your own API key**. Either way there's no subscription, no ads, no accounts, and no server of ours in the middle.

App page: **[drophouse.uk/products/sightbuddy](https://www.drophouse.uk/products/sightbuddy/)**

---

## What it does

| Mode | What it does | Runs where |
|------|--------------|-----------|
| **Discover objects** | Names what the camera sees as you move | On device |
| **Find objects** | Say "keys" or "cup" — directional sounds and haptics guide you to it | On device; if you've added a key, unclear phrases are disambiguated by AI |
| **Text chat** | Reads printed text aloud with seekable playback; ask questions about it | OCR on device; answers on device or via your key |
| **Image chat** | Describes a scene and answers follow-up questions about it | On device or via your key |
| **Memories** | Save a chat and read it again later | On device; saved chats never leave the phone |
| **Scan colour** | Speaks the colour inside the on-screen focus frame | On device |
| **Scan light** | Reports bright, dim, or dark lighting | On device |

**AI on the phone.** Settings offers a model to download once (about 2.6 GB, English). With it, Image chat and Text chat answer with no key, no account and no connection. It is offered where the phone can run it well; on other phones, and in Finnish, it stays available under Advanced settings.

**Speech recognition can run on your device too** — optional Whisper models (~154 MB, downloaded once on request) give offline, accent-tolerant transcription. Until you download them the app uses Android's own speech recognition, which is provided by your phone (usually Google's) and may process what you say online, under that provider's terms.

Every surface is built accessibility-first: TalkBack labels throughout, spoken announcements, haptics, high-contrast light/dark themes, adjustable speech rate (1×–3×), a feature bar you can swipe or tap, per-feature show/hide, and Finnish as well as English. What the app says is written into the conversation too, so a screen reader can read it back.

## Install

**From Google Play** — search for Sight Buddy, or use the listing link on [drophouse.uk](https://www.drophouse.uk/products/sightbuddy). Easiest option; updates arrive automatically.

**Directly, without Google Play** — every release has an APK attached:

1. Open [Releases](../../releases) and download the `.apk` (arm64 — any phone from roughly 2017 onward).
2. Open the file on your phone. Android will ask permission to install from this source — allow it for your browser or file manager.
3. Install, then grant **camera** and **microphone** permissions on first run.

Requires **Android 11 (API 30)** or newer.

**Older versions** stay available: every past release keeps its APK on its own [Releases](../../releases) entry.

**Pick one source and stay with it.** Google Play re-signs the apps it distributes, so the Play build and the APK here are signed with different keys — and Android refuses to replace an installed app with a differently-signed copy. If an install or update stops with a signature error, that is why. Switching sources means uninstalling first, which **erases your saved API key and any models you have downloaded** — including the 2.6 GB AI model. If you want updates to arrive on their own, install from Google Play.

## The AI features: on your phone, or your own key

Image chat and the AI answers in Text chat need one of two things. Everything else works without either.

**On your phone.** Settings → Model library offers a model sized for your phone. Download it once and the answers come from the phone itself: no account, no key, no connection, nothing sent anywhere.

**Or your own OpenAI key.** Answers come from OpenAI instead, billed to your account:

1. Create a key at [platform.openai.com/api-keys](https://platform.openai.com/api-keys).
2. **Set a hard limit** on your OpenAI account (Billing → Limits → "Set a monthly budget", and enable **Enforce hard limit**). A soft limit only emails you; a hard limit stops requests. Do this before you paste the key anywhere.
3. In Sight Buddy: **Settings → OpenAI API key → paste → Save**.

**The key is yours to look after.** Sight Buddy stores it and sends it only to OpenAI, but the account, the spending and the key's safety are between you and OpenAI: nobody else can cancel a key you lose control of, and nobody else pays the bill. If that is not something you want to manage, skip it — the on-device model above needs no key, and every other feature works without one.

With both available, the **Use API** switch in Settings decides; with it off, or with no connection, the phone's own model answers. Your key is encrypted on your device using the Android Keystore and is sent **only** to OpenAI, never to us. Usage bills to your own OpenAI account — typically a fraction of a penny per question. Remove the key any time to switch the app back to fully-local mode.

**Choosing a model** (Settings, under the key):

| Option | Model | When to use |
|---|---|---|
| Fast | `gpt-4o-mini` | Cheapest, fine for simple questions |
| Balanced | `gpt-4o` | Default — strong vision at low cost |
| Most capable | `gpt-4.1` | Complex questions; slower and pricier |

**A note on privacy.** With a key saved, four things can leave your device, and only at the moment you ask for them:

- **Image chat** — the captured photo and your question.
- **Text chat** — the text read from your capture (not the image itself) and your question.
- **Find objects** — the spoken phrase, *only* when the app can't match it to a known object locally. No image is ever sent.
- **Speech recognition** — only if you switch on OpenAI transcription in Settings: the speech recognition audio.

**Saved chats** (Memories) are written to your phone's private storage and are left out of Android's cloud backup, so they stay on the device they were made on.

**Model downloads** come from Hugging Face (the AI model) and GitHub (the speech models). Downloading one shows those services your IP address, as any download does; nothing about you or your use is sent with it.

That's the complete list. Everything else — object detection, reading text aloud, colour, light, and speech recognition once the models are downloaded — happens on your device and sends nothing. **With no key saved, the app makes no AI requests at all.** Requests go to OpenAI under their privacy policy, billed to your own account.

**Crash diagnostics.** The official builds — from Google Play, or the release APK here — include Firebase Crashlytics. If the app crashes, an anonymous report (stack trace, device model, OS version, app version — no images, audio, text, or advertising ID) is sent to Google so we can fix the fault. Turn it off any time at **Settings → Privacy → "Send crash reports."** Builds you compile from source send nothing: Crashlytics is inactive without a `google-services.json`, which isn't in this repo.

## Why this is free

Sight Buddy started as a commercial project and ran as an open beta on Google Play in the UK and Europe. We decided it should not be a paid product, took the server down, and opened the source instead. It is still built and released — this is not an archive.

What that means in practice:

- **No subscription and no ads — ever.** Not a marketing line: the app contains no billing code and no advertising SDKs, and the advertising-ID permissions are explicitly stripped from the manifest.
- **Nothing runs through us.** There is no server of ours in the middle, because there isn't one at all. The AI runs on your phone, or on your own OpenAI account if you choose that.
- **You own your costs.** On-device answers cost nothing. With a key, usage bills to your account and nobody takes a cut.
- **It's yours to fork.** MIT licensed.

## What's technically interesting here

If you're reading this as a portfolio piece, or mining the repo for parts, these are the bits worth a look:

- **On-device Whisper speech-to-text on Android** — [`core/stt/`](app/src/main/java/com/example/sightbuddy/core/stt/). Whisper `base.en` (int8) + Silero VAD via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), wrapped in a push-to-talk/tap-to-talk service with 10 s silence auto-stop and a 60 s cap. Models download once from a public release; until then it transparently falls back to the system recogniser, so the app is never broken while waiting.
- **"Ask straight away" capture flow** — pressing Ask with nothing captured records your question, snaps the frame at the moment you finish speaking, then pairs frame and transcription in an order-independent effect before sending both.
- **Encrypted BYOK key storage** — [`ApiKeyStore`](app/src/main/java/com/example/sightbuddy/core/ApiKeyStore.kt) seals the key with an AES-GCM key held in the Android Keystore (hardware-backed where available), so the preferences file alone is useless.
- **Accessible Compose patterns** — TalkBack labels on every control, a high-contrast mode that masks the preview *without* stopping frame analysis, and a character-indexed TTS playback engine whose seek/pause survives speech-rate changes by design.
- **A disciplined CameraX pipeline** — YUV→RGB without a lossy JPEG round-trip, rotation-aware TFLite input, and a capacity-1 frame `Channel` with explicit ownership accounting to prevent backpressure stalls.
- **A 2.6 GB language model running on the phone** — [`core/llm/LocalGemma.kt`](app/src/main/java/com/example/sightbuddy/core/llm/LocalGemma.kt). Gemma via [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM), vision on the GPU and audio on the CPU, warmed up before the first question, released when nothing needs it, and answering the same OpenAI-shaped request the cloud path uses — so every feature works either way without knowing which answered.
- **A resumable download that cannot be fooled** — the server may answer a range request with the whole file. The model download only appends when the reply says the body starts exactly where the file ends, after a complete 2.6 GB download was once thrown away for being 65 MB too long.
- **Keeping a Compose screen compilable** — the main screen grew past ART's per-method instruction limit, so Android refused to compile it and ran it interpreted. Splitting the state and logic into a controller, with one composable per screen, took it from 25,712 dex code units to about 4,000. `dexdump` tells you; nothing else will.
- **Environment isolation without a DI framework** — dev/prod flavors swap real vs. mock behaviour through plain factory functions. Boring, explicit, testable.

Architecture notes: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Building from source

Requirements: **JDK 17+**, **Android SDK 36**. No Android Studio needed — the Gradle wrapper handles everything, and the sherpa-onnx runtime downloads automatically at build time.

```bash
./gradlew assembleDevDebug      # local dev build
./gradlew assembleProdRelease   # release APK
```

Release builds fall back to debug signing when no keystore is configured, so a clone builds and installs out of the box. To sign with your own key, add `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` to `local.properties`.

Crash reporting (Firebase Crashlytics) is prod-flavor only and inactive without a `google-services.json`, which is not committed — clones build fine without one.

## Contributing

Issues and pull requests are welcome. It is a small project with a narrow focus; these are the ideas that would genuinely help:

- **More languages.** The interface speaks English and Finnish, but the on-device speech model is `base.en` and the object labels are English. Adding a language means the whole chain together — labels, announcements, text-to-speech and a speech model that serves it.
- **An iOS version.** There isn't one, and the people this app is for are split roughly evenly across platforms. The on-device pieces (Whisper via sherpa-onnx, OCR, object detection) all have iOS equivalents.
- **An Apache-licensed detector.** Worth a note, since the licensing here is easy to get wrong: the popular Ultralytics YOLO models (v5/v8/v11) are **AGPL-3.0**, which would force this whole MIT project to relicense — so they are *not* an option. **YOLOX** (Megvii) is genuinely Apache-2.0 and would be a clean swap for the current EfficientDet-Lite0 detector.

## Credits

Built by [Drophouse Ltd](https://www.drophouse.uk). Development — the on-device speech and AI work, the accessibility work, and opening the source — was done with AI pair-programming throughout: **Claude (Anthropic)** via Claude Code, and **Cursor**.

Questions or feedback: **contact@drophouse.uk**.

## Licence

Code is [MIT](LICENSE) © 2026 Drophouse Ltd. Third-party components keep their own licenses: see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

Third-party components keep their own licences: OpenAI Whisper models (MIT; ONNX export via sherpa-onnx, Apache-2.0), Silero VAD (MIT), sherpa-onnx runtime (Apache-2.0), and EfficientDet-Lite0 (Apache-2.0, see [legal/](legal/)).

Sound effects: the camera-shutter cue is from [Freesound](https://freesound.org/s/520684/) (CC0), the spoken voice prompts were generated with [Voicertool](https://voicertool.com/terms) (free for commercial use), and the remaining UI sounds are original works by Drophouse Ltd. Full attributions are in [`app/src/main/assets/licenses/`](app/src/main/assets/licenses/).
