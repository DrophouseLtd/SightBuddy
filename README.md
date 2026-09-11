# Sight Buddy

**A free, open-source, bring-your-own-key assistive vision app for blind and low-vision users.**

Point your phone's camera to hear your surroundings described, read printed text aloud, find objects, and identify colours and lighting. Most of it runs **entirely on your device**. The optional AI features run on **your own OpenAI API key** — so there's no subscription, no ads, no accounts, and no server of ours in the middle.

<p>
  <a href="#install">Install</a> ·
  <a href="#using-the-ai-features-bring-your-own-key">BYOK setup</a> ·
  <a href="#why-this-is-free">Why it's free</a> ·
  <a href="#whats-technically-interesting-here">Technical notes</a> ·
  <a href="#building-from-source">Build</a>
</p>

---

## What it does

| Mode | What it does | Runs where |
|------|--------------|-----------|
| **Discover objects** | Names what the camera sees as you move | On device |
| **Find objects** | Say "keys" or "cup" — directional sounds and haptics guide you to it | On device; if you've added a key, unclear phrases are disambiguated by AI |
| **Text chat** | Reads printed text aloud with seekable playback; ask questions about it | OCR on device, Q&A via your key |
| **Image chat** | Describes a scene and answers follow-up questions about it | Your key |
| **Scan colour** | Speaks the colour inside the on-screen focus frame | On device |
| **Scan light** | Reports bright, dim, or dark lighting | On device |

**Speech recognition runs on your device too** — optional Whisper models (~154 MB, downloaded once on request) give offline, accent-tolerant transcription. Until you download them the app falls back to Android's built-in recogniser, so it works immediately either way.

Every surface is built accessibility-first: TalkBack labels throughout, spoken announcements, haptics, high-contrast light/dark themes, adjustable speech rate (1×–3×), button navigation instead of swipes, per-feature show/hide, and a choice of tap-to-speak or hold-to-speak.

## Install

**From Google Play** — search for Sight Buddy, or use the listing link on [drophouse.uk](https://www.drophouse.uk/products/sightbuddy). Easiest option; updates arrive automatically.

**Directly, without Google Play** — every release has an APK attached:

1. Open [Releases](../../releases) and download the `.apk` (arm64 — any phone from roughly 2017 onward).
2. Open the file on your phone. Android will ask permission to install from this source — allow it for your browser or file manager.
3. Install, then grant **camera** and **microphone** permissions on first run.

Requires **Android 11 (API 30)** or newer.

**Pick one source and stay with it.** Google Play re-signs the apps it distributes, so the Play build and the APK here are signed with different keys — and Android refuses to replace an installed app with a differently-signed copy. If an install or update stops with a signature error, that is why. Switching sources means uninstalling first, which **erases your saved API key and any downloaded voice models**. If you want updates to arrive on their own, install from Google Play.

## Using the AI features (bring your own key)

Image chat and the AI answers in Text chat need an OpenAI API key. Everything else works without one.

1. Create a key at [platform.openai.com/api-keys](https://platform.openai.com/api-keys).
2. **Set a spending limit** on your OpenAI account (Billing → Limits). Worth doing before you start.
3. In Sight Buddy: **Settings → OpenAI API key → paste → Save**.

Your key is encrypted on your device using the Android Keystore and is sent **only** to OpenAI, never to us. Usage bills to your own OpenAI account — typically a fraction of a penny per question. Remove the key any time to switch the app back to fully-local mode.

**Choosing a model** (Settings, under the key):

| Option | Model | When to use |
|---|---|---|
| Fast | `gpt-4o-mini` | Cheapest, fine for simple questions |
| Balanced | `gpt-4o` | Default — strong vision at low cost |
| Most capable | `gpt-4.1` | Complex questions; slower and pricier |

**A note on privacy.** With a key saved, three things can leave your device, and only at the moment you ask for them:

- **Image chat** — the captured photo and your question.
- **Text chat** — the text read from your capture (not the image itself) and your question.
- **Find objects** — the spoken phrase, *only* when the app can't match it to a known object locally. No image is ever sent.

That's the complete list. Everything else — object detection, reading text aloud, colour, light, and speech recognition once the models are downloaded — happens on your device and sends nothing. **With no key saved, the app makes no AI requests at all.** Requests go to OpenAI under their privacy policy, billed to your own account.

**Crash diagnostics.** The official builds — from Google Play, or the release APK here — include Firebase Crashlytics. If the app crashes, an anonymous report (stack trace, device model, OS version, app version — no images, audio, text, or advertising ID) is sent to Google so we can fix the fault. Turn it off any time at **Settings → Privacy → "Send crash reports."** Builds you compile from source send nothing: Crashlytics is inactive without a `google-services.json`, which isn't in this repo.

## Why this is free

Sight Buddy started as a commercial project and ran as an open beta on Google Play in the UK and Europe. After a proper market and financial analysis we concluded it couldn't work as a paid product: the core features are already offered free by far better-resourced players (Microsoft's Seeing AI, Be My Eyes, Envision), and the per-request cost of cloud AI has to be paid by someone.

Monetising was not realistic. So rather than quietly shelve the code, we shut down the backend and released the app as a gift to the people it was built for.

What that means in practice:

- **No subscription and no ads — ever.** Not a marketing line: the app contains no billing code and no advertising SDKs, and the advertising-ID permissions are explicitly stripped from the manifest.
- **You own your data and your costs.** With BYOK, your usage goes directly to your own OpenAI account. Nothing routes through a server we control, because there isn't one.
- **It's yours to fork.** MIT licensed.

The silver lining of local-first architecture: shutting down the backend didn't brick anything. The cloud features became BYOK, everything else kept working, and the app stayed on the store.

## What's technically interesting here

If you're reading this as a portfolio piece, or mining the repo for parts, these are the bits worth a look:

- **On-device Whisper speech-to-text on Android** — [`core/stt/`](app/src/main/java/com/example/sightbuddy/core/stt/). Whisper `base.en` (int8) + Silero VAD via [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), wrapped in a push-to-talk/tap-to-talk service with 10 s silence auto-stop and a 60 s cap. Models download once from a public release; until then it transparently falls back to the system recogniser, so the app is never broken while waiting.
- **"Ask straight away" capture flow** — pressing Ask with nothing captured records your question, snaps the frame at the moment you finish speaking, then pairs frame and transcription in an order-independent effect before sending both.
- **Encrypted BYOK key storage** — [`ApiKeyStore`](app/src/main/java/com/example/sightbuddy/core/ApiKeyStore.kt) seals the key with an AES-GCM key held in the Android Keystore (hardware-backed where available), so the preferences file alone is useless.
- **Accessible Compose patterns** — TalkBack labels on every control, a high-contrast mode that masks the preview *without* stopping frame analysis, and a character-indexed TTS playback engine whose seek/pause survives speech-rate changes by design.
- **A disciplined CameraX pipeline** — YUV→RGB without a lossy JPEG round-trip, rotation-aware TFLite input, and a capacity-1 frame `Channel` with explicit ownership accounting to prevent backpressure stalls.
- **Environment isolation without a DI framework** — dev/prod flavors swap real vs. mock behaviour through plain factory functions. Boring, explicit, testable.

Architecture notes: [AGENT_HANDOVER.md](AGENT_HANDOVER.md).

## Building from source

Requirements: **JDK 17+**, **Android SDK 36**. No Android Studio needed — the Gradle wrapper handles everything, and the sherpa-onnx runtime downloads automatically at build time.

```bash
./gradlew assembleDevDebug      # local dev build
./gradlew assembleProdRelease   # release APK
```

Release builds fall back to debug signing when no keystore is configured, so a clone builds and installs out of the box. To sign with your own key, add `KEYSTORE_FILE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS` and `KEY_PASSWORD` to `local.properties`.

Crash reporting (Firebase Crashlytics) is prod-flavor only and inactive without a `google-services.json`, which is not committed — clones build fine without one.

## Contributing

Issues and pull requests are welcome. The project is no longer under active development, so it's a good candidate for anyone who wants to take a piece further. Ideas that would genuinely help:

- **More languages.** Both the speech model and the UI strings are English-only today. The app deliberately ships `base.en` because the whole chain — object labels, announcements, text-to-speech — assumes English; proper localisation means changing all of it together, not just the model.
- **An iOS version.** There isn't one, and the people this app is for are split roughly evenly across platforms. The on-device pieces (Whisper via sherpa-onnx, OCR, object detection) all have iOS equivalents.
- **An Apache-licensed detector.** Worth a note, since the licensing here is easy to get wrong: the popular Ultralytics YOLO models (v5/v8/v11) are **AGPL-3.0**, which would force this whole MIT project to relicense — so they are *not* an option. **YOLOX** (Megvii) is genuinely Apache-2.0 and would be a clean swap for the current EfficientDet-Lite0 detector.

## Credits

Built by [Drophouse Ltd](https://www.drophouse.uk). Development — including the local-STT migration, the accessibility work, and the sunset/open-sourcing process — was done with AI pair-programming throughout: **Claude (Anthropic)** via Claude Code, and **Cursor**.

Questions or feedback: **contact@drophouse.uk**.

## Licence

Code is [MIT](LICENSE) © 2026 Drophouse Ltd.

Third-party components keep their own licences: OpenAI Whisper models (MIT; ONNX export via sherpa-onnx, Apache-2.0), Silero VAD (MIT), sherpa-onnx runtime (Apache-2.0), and EfficientDet-Lite0 (Apache-2.0, see [legal/](legal/)).

Sound effects: the camera-shutter cue is from [Freesound](https://freesound.org/s/520684/) (CC0), the spoken voice prompts were generated with [Voicertool](https://voicertool.com/terms) (free for commercial use), and the remaining UI sounds are original works by Drophouse Ltd. Full attributions are in [`app/src/main/assets/licenses/`](app/src/main/assets/licenses/).
