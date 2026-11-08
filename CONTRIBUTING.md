# Contributing to Sight Buddy

Thanks for wanting to help. Sight Buddy is an assistive vision app for blind and low-vision people, so every change is judged by one question: does it make the app easier and more reliable to use **without looking at the screen**?

You don't have to write code to help. Testing with a screen reader, reporting what is confusing, and improving translations are just as valuable.

## Ways to help

- **Report a bug or a confusing moment.** Use the issue templates. Say what you did, what you heard, and what you expected to hear.
- **Pick up an issue.** Look for `good first issue` and `help wanted`. Comment on the issue before you start, so two people don't do the same work.
- **Test with TalkBack.** If you use a screen reader daily, your reports are the most useful thing this project can get.
- **Translate.** Strings live in `app/src/main/res/values/strings.xml`. Talk to us in an issue before adding a language: a language needs speech, text recognition and AI answers to work together, not only the buttons.

## Building

Requirements: JDK 17+ and the Android SDK (API 36). The Gradle wrapper does the rest.

```bash
./gradlew assembleDevDebug testDevDebugUnitTest lintDevDebug
```

This is exactly what CI runs on every pull request; please run it before you push. The APK to install is `app/build/outputs/apk/dev/debug/app-dev-arm64-v8a-debug.apk` (arm64 phones, Android 11 or newer). Lint fails only on issues not already listed in `app/lint-baseline.xml`.

`docs/ARCHITECTURE.md` explains how the app fits together. Start there.

## What a good change looks like

- **Small and focused.** One problem per pull request. Easier to review, easier to revert.
- **Tested.** Logic that can be pulled out of Android goes into plain Kotlin with a unit test (see `app/src/test/`). A fixed bug keeps a test that fails without the fix.
- **Heard, not just seen.** Describe in the pull request how you tested it with TalkBack: what you swiped to, what was read out, what happened.
- **Explained.** Comments say *why*, not *what*. Commit messages say what changes for the person using the app.

## Accessibility rules

These are not optional:

- Every control has a label TalkBack can read. Don't put "double tap" in labels: TalkBack adds its own hint.
- Don't move TalkBack's focus unexpectedly, and don't make text that changes under the focus.
- Don't use `announceForAccessibility`. It is deprecated in Android 16. Speech goes through `TTSService`, which can route it through the screen reader.
- Sounds and vibration follow the user's Sound effects and Vibration switches (`core/Cues.kt`).
- New screens work in high contrast and with large text.

## Privacy rules

Sight Buddy promises that nothing leaves the phone unless the user asks for it. So:

- **No analytics, tracking or advertising SDKs.** Pull requests that add them will be closed.
- **No new network calls** without discussing them in an issue first. Anything sent off the phone has to be described in the privacy policy before it ships.
- **No logging of what the user said, read or saw** in release builds. Use `PrivateLog` for that.

## Licensing

The project is [MIT licensed](LICENSE). By opening a pull request you agree that your contribution is licensed under the MIT licence too.

New dependencies and models must have a licence compatible with MIT (Apache-2.0, MIT, BSD). **GPL and AGPL are not.** For example, the popular Ultralytics YOLO models are AGPL-3.0 and can't be used here. Add any new component to `THIRD_PARTY_NOTICES.md` and `app/src/main/assets/licenses/third_party_notices.txt`.

## A note on issue numbers in the code

Some code comments mention "issue #N". Those numbers refer to the maintainers' private tracker from before the repository opened, not to the issues here. The comment next to the number explains the reasoning, so the number itself can be ignored.

## Security

Please don't report security problems in public issues. See [SECURITY.md](SECURITY.md).

## Code of Conduct

Everyone taking part is expected to follow the [Code of Conduct](CODE_OF_CONDUCT.md).
