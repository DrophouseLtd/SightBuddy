# Third-party notices

Sight Buddy's own code is MIT licensed (see [LICENSE](LICENSE)). These
components keep their own licenses. The app shows the same list, with the full
licence texts, under **Settings → Privacy → Open-source licences**.

## Inside the app

- AndroidX and Jetpack Compose, by Google — Apache-2.0
- Kotlin standard library and kotlinx.coroutines, by JetBrains — Apache-2.0
- OkHttp and Okio, by Square — Apache-2.0
- TensorFlow Lite and the TensorFlow Lite Task Library, by Google — Apache-2.0
- [LiteRT-LM](https://github.com/google-ai-edge/LiteRT-LM), the runtime for the
  on-device AI model, by Google — Apache-2.0
- [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx), the runtime for
  on-device speech recognition, by the k2-fsa project — Apache-2.0
- ONNX Runtime, included in sherpa-onnx, by Microsoft — MIT
- EfficientDet-Lite0, the object detection model, by Google — Apache-2.0
  (see [legal/EFFICIENTDET_LICENSE.txt](legal/EFFICIENTDET_LICENSE.txt))
- Firebase Crashlytics, by Google, in the Play build only — Apache-2.0
- ML Kit Text Recognition, by Google — under the ML Kit Terms of Service

## Downloaded when the user chooses to

- **Gemma 4 E2B**, the on-device AI model, by Google, converted for LiteRT-LM by
  [LiteRT Community](https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm)
  and downloaded from Hugging Face at a pinned revision — Apache-2.0
- **Whisper base (English)**, the speech recognition model, by OpenAI — MIT.
  Converted to ONNX by the sherpa-onnx project — Apache-2.0
- **Silero VAD**, the voice activity detector, by Silero — MIT

## Sounds

The camera-shutter cue is ["Contarex camera shutter.wav" by
Tonik1105](https://freesound.org/s/520684/), dedicated to the public domain
(CC0). The spoken prompts were generated with
[Voicertool](https://voicertool.com/terms), free for commercial use. The
remaining sounds are original works by Drophouse Ltd, released under this
project's MIT licence. Terms are archived in
[`app/src/main/assets/licenses/`](app/src/main/assets/licenses/).
