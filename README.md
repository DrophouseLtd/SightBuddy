# Sight Buddy

[![Build](https://github.com/DrophouseLtd/SightBuddy/actions/workflows/ci.yml/badge.svg)](https://github.com/DrophouseLtd/SightBuddy/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**A free, open-source assistive vision app for blind and low-vision Android users.**

Point your phone's camera to hear your surroundings described, read printed text aloud, find objects, and identify colours and lighting. It runs **on your device**: the AI answers can come from a model you download once, with no account and no internet. If you would rather use OpenAI, you can, with **your own API key**. Either way there's no subscription, no ads, no accounts, and no server of ours in the middle.

App page: **[drophouse.uk/products/sightbuddy](https://www.drophouse.uk/products/sightbuddy/)** · Want to help? See **[CONTRIBUTING.md](CONTRIBUTING.md)** and the [open issues](../../issues).

---

## What it does

| Mode | What it does | Runs where |
|------|--------------|-----------|
| **Discover objects** | Names what the camera sees as you move | On device |
| **Find objects** | Say "cup" or "bottle": directional sounds and haptics guide you to it | On device; if you've added a key, unclear phrases are matched by AI |
| **Text chat** | **Capture** reads everything in view at once. **Find page** guides you to a page with a rising tone and "Hold steady", then reads just that page from a full-size photo; an open book is read one page at a time. **Live text** reads text as it comes into view. Ask questions about what was read | Text recognition on device; answers on device or via your key |
| **Image chat** | Describes a scene and answers follow-up questions about it | On device or via your key |
| **Memories** | Save a chat and read it again later | On device; saved chats never leave the phone |
| **Scan colour** | Speaks the colour inside the on-screen focus frame | On device |
| **Scan light** | Reports bright, dim, or dark lighting | On device |

Answers are spoken as they are written, sentence by sentence, so you don't wait in silence for a long answer.

**AI on the phone.** Settings offers a model to download once (about 2.6 GB). With it, Image chat and Text chat answer with no key, no account and no connection. It is offered where the phone can run it well (a phone with 8 GB of memory and about 4.3 GB free); on other phones it stays available under Advanced settings.

**Speech recognition can run on your device too:** optional Whisper models (about 160 MB, downloaded once on request) give offline, accent-tolerant transcription. Until you download them the app uses Android's own speech recognition, which is provided by your phone (usually Google's) and may process what you say online, under that provider's terms.

Every surface is built accessibility-first: TalkBack labels throughout, an option to speak through TalkBack in its own voice and speed, haptics, high-contrast light and dark themes, adjustable speech rate (1× to 3×), a feature bar you can swipe or tap, and per-feature show and hide. Text in another language is read with that language's voice. What the app says is written into the conversation too, so a screen reader can read it back.

The interface is in English and Finnish. When the app's language differs from the phone's, TalkBack still reads the app's words in the app's language, and with the app in Finnish the on-device AI answers in Finnish. More languages are welcome: see the [open issues](../../issues).

## Install

**From Google Play:** search for Sight Buddy, or use the listing link on [drophouse.uk](https://www.drophouse.uk/products/sightbuddy). Easiest option; updates arrive automatically.

**Directly, without Google Play:** every release has an APK attached.

1. Open [Releases](../../releases) and download the `.apk` (arm64: any phone from roughly 2017 onward).
2. Open the file on your phone. Android will ask permission to install from this source: allow it for your browser or file manager.
3. Install, then grant **camera** and **microphone** permissions on first run.

Requires **Android 11 (API 30)** or newer.

**Pick one source and stay with it.** Google Play re-signs the apps it distributes, so the Play build and the APK here are signed with different keys, and Android refuses to replace an installed app with a differently-signed copy. If an install or update stops with a signature error, that is why. Switching sources means uninstalling first, which **erases your saved API key and any models you have downloaded**, including the 2.6 GB AI model.

## The AI features: on your phone, or your own key

Image chat and the AI answers in Text chat need one of two things. Everything else works without either.

**On your phone.** Settings → Model library offers a model sized for your phone. Download it once and the answers come from the phone itself: no account, no key, no connection.

**Or your own OpenAI key.** Answers come from OpenAI instead, billed to your account:

1. Create a key at [platform.openai.com/api-keys](https://platform.openai.com/api-keys).
2. **Set a hard limit** on your OpenAI account (Billing → Limits → "Set a monthly budget", and enable **Enforce hard limit**). A soft limit only emails you; a hard limit stops requests. Do this before you paste the key anywhere.
3. In Sight Buddy: **Settings → OpenAI API key → paste → Save**.

**The key is yours to look after.** Sight Buddy stores it encrypted on your device (Android Keystore) and sends it only to OpenAI, but the account, the spending and the key's safety are between you and OpenAI. If that is not something you want to manage, skip it: the on-device model needs no key, and every other feature works without one.

With both available, the **Use API** switch in Settings decides; with it off, or with no connection, the phone's own model answers. Remove the key any time and the phone's own model keeps working.

**Choosing a model** (Settings, under the key):

| Option | Model | When to use |
|---|---|---|
| Fast | `gpt-4o-mini` | Cheapest, fine for simple questions |
| Balanced | `gpt-4o` | Default: strong vision at low cost |
| Most capable | `gpt-4.1` | Complex questions; slower and pricier |

## Privacy: what leaves the phone

With a key saved, four things can go to OpenAI, and only at the moment you ask for them:

- **Image chat:** the captured photo and your question.
- **Text chat:** the text read from your capture (not the image itself) and your question.
- **Find objects:** the spoken phrase, *only* when the app can't match it to a known object locally. No image is sent.
- **Speech recognition:** only if you switch on OpenAI transcription in Settings, the recorded audio.

**With no key saved, the app makes no AI requests at all.**

Besides that:

- **Model downloads** come from Hugging Face (the AI model) and GitHub (the speech models). Downloading shows those services your IP address, as any download does.
- **Google's on-device libraries send Google anonymous usage and performance data.** Text recognition and language identification (ML Kit) send device model, app version, timings, detected languages and a per-installation identifier; Google documents it [here](https://developers.google.com/ml-kit/android-data-disclosure). The object detector's runtime (MediaPipe Tasks) sends the app's name and version, which task ran and how long it took. Neither includes your images, the text read from them, or what was detected, and neither has a switch to turn it off.
- **Crash diagnostics.** The official builds include Firebase Crashlytics: if the app crashes, an anonymous report (stack trace, device model, OS and app version; no images, audio, text or advertising ID) is sent to Google. Turn it off at **Settings → Privacy → "Send crash reports"**. Builds you compile from source send nothing: Crashlytics is inactive without a `google-services.json`, which isn't in this repo.
- **Saved chats** (Memories) stay in the phone's private storage and are left out of Android's cloud backup.

No analytics of ours, no ads, no accounts. The full policy is on the [app page](https://www.drophouse.uk/products/sightbuddy/).

## Why this is free

Sight Buddy started as a commercial project and ran as an open beta on Google Play. We decided it should not be a paid product, took the server down, and opened the source instead. It is still built and released: this is not an archive.

- **No subscription and no ads, ever.** The app contains no billing code and no advertising SDKs, and the advertising-ID permissions are explicitly stripped from the manifest.
- **Nothing runs through us.** There is no server of ours at all. The AI runs on your phone, or on your own OpenAI account if you choose that.
- **It's yours to fork.** MIT licensed.

## What's technically interesting here

If you're reading this as a portfolio piece, or mining the repo for parts:

- **Guided page scanning with a corner model:** [`features/chat/PageCorners.kt`](app/src/main/java/com/example/sightbuddy/features/chat/PageCorners.kt), `PageLock.kt`, `PageCornerGuide.kt`. A small MIT-licensed MobileNetV3 heatmap model on ONNX Runtime finds the page's four corners in each frame (about 70 ms on a phone). The guidance locks onto one page of an open book, speaks only "Closer" and "Hold steady" with a tone for how much of the page is found, then takes a 12 MP photo and flattens the page with a perspective warp before text recognition.
- **On-device Whisper speech-to-text on Android:** [`core/stt/`](app/src/main/java/com/example/sightbuddy/core/stt/). Whisper `base.en` (int8) and Silero VAD via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx). Models download once and are checked against pinned SHA-256s; until then it falls back to the system recogniser, so the app is never broken while waiting.
- **A 2.6 GB language model running on the phone:** [`core/llm/LocalGemma.kt`](app/src/main/java/com/example/sightbuddy/core/llm/LocalGemma.kt). Gemma via [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM), answering the same OpenAI-shaped request the cloud path uses, streamed sentence by sentence to the speech engine and to TalkBack.
- **"Ask straight away" capture flow:** pressing Ask with nothing captured records your question, takes the picture, and pairs picture and transcription in an order-independent effect before sending both.
- **Encrypted BYOK key storage:** [`ApiKeyStore`](app/src/main/java/com/example/sightbuddy/core/ApiKeyStore.kt) seals the key with an AES-GCM key held in the Android Keystore (hardware-backed where available).
- **A resumable download that cannot be fooled:** the model download only appends when the server's reply says the body starts exactly where the file ends, after a complete 2.6 GB download was once thrown away for being 65 MB too long.
- **Keeping a Compose screen compilable:** the main screen grew past ART's per-method instruction limit, so Android ran it interpreted. Splitting state and logic into a controller, with one composable per screen, took it from 25,712 dex code units to about 4,000. `dexdump` tells you; nothing else will.
- **Environment isolation without a DI framework:** dev and prod flavors swap behaviour through plain factory functions.

Architecture notes: [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md).

## Building from source

Requirements: **JDK 17+** and the **Android SDK (API 36)**. No Android Studio needed: the Gradle wrapper handles everything, and the sherpa-onnx runtime downloads (checksum-verified) at build time.

```bash
./gradlew assembleDevDebug testDevDebugUnitTest lintDevDebug
```

That is the build CI runs on every pull request. Install `app/build/outputs/apk/dev/debug/app-dev-arm64-v8a-debug.apk` on an arm64 phone.

Release builds fall back to debug signing when no keystore is configured, so a clone builds and installs out of the box. To sign with your own key, add `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` to `local.properties`.

## Contributing

Contributions are welcome, especially from blind and low-vision Android users and people who build for them. Read **[CONTRIBUTING.md](CONTRIBUTING.md)** first: it covers how to build, what a good change looks like here, and how to test with TalkBack. Good places to start are the [open issues](../../issues), in particular those labelled `good first issue` and `help wanted`.

Please also read our [Code of Conduct](CODE_OF_CONDUCT.md). To report a security problem, see [SECURITY.md](SECURITY.md); please don't open a public issue for it.

## Credits

Built by [Drophouse Ltd](https://www.drophouse.uk). Development was done with AI pair-programming throughout: **Claude (Anthropic)** via Claude Code, and **Cursor**.

Questions or feedback: **contact@drophouse.uk**.

## Licence

Code is [MIT](LICENSE) © 2026 Drophouse Ltd. Third-party components keep their own licences: see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
