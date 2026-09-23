# ── OkHttp / Okio ────────────────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# ── TensorFlow Lite ──────────────────────────────────────────────────────────
-keep class org.tensorflow.lite.** { *; }
-keepclassmembers class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# ── ML Kit Text Recognition ──────────────────────────────────────────────────
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**

# ── Firebase (Crashlytics + Analytics component discovery; required for R8 release)
-keepattributes SourceFile,LineNumberTable
-keep public class * extends java.lang.Exception
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.firebase.crashlytics.** { *; }
-keep class com.google.firebase.analytics.** { *; }
-keep class com.google.firebase.components.ComponentRegistrar
-keep class * implements com.google.firebase.components.ComponentRegistrar { *; }
-keep class com.google.android.datatransport.** { *; }

# ── Google Play Integrity ────────────────────────────────────────────────────
-keep class com.google.android.play.core.integrity.** { *; }

# ── sherpa-onnx (JNI calls back into these classes; must survive R8) ─────────
-keep class com.k2fsa.sherpa.onnx.** { *; }
-dontwarn com.k2fsa.sherpa.onnx.**

# ── LiteRT-LM (on-device Gemma) ──────────────────────────────────────────────
# The AAR ships no consumer rules, and its JNI code reads these classes by name
# (SamplerConfig.getTopK/getTemperature, InputData$Text.getText, the message
# callbacks). R8 otherwise strips the getters Kotlin never calls, and the first
# on-device answer fails in release builds only.
-keep class com.google.ai.edge.litertlm.** { *; }
-dontwarn com.google.ai.edge.litertlm.**

# ── JSON parsing (org.json is platform API, but keep safety) ─────────────────
-keep class org.json.** { *; }

# ── App transport class (direct OpenAI HTTP client; keep for release parity) ───
-keep class com.example.sightbuddy.core.OpenAiTransport { *; }

# ── Logging ─────────────────────────────────────────────────────────────────
# Release builds keep warnings and errors only. Anything carrying user content
# goes through PrivateLog (debug builds only); this is the backstop for a stray
# Log.i that slips through.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}
