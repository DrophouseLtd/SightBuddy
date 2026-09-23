import java.net.URI
import java.security.MessageDigest
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.crashlytics)
}

val localProperties = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

fun localOrEnv(name: String): String =
    (System.getenv(name)
        ?: localProperties.getProperty(name)
        ?: project.findProperty(name) as? String
        ?: "").trim()

fun escaped(value: String): String = value.replace("\"", "\\\"")

// ---------------------------------------------------------------------------
// Local Whisper STT (sherpa-onnx). The AAR is fetched from the official GitHub
// release at build time (48 MB — kept out of git). Model files are downloaded
// by the app at runtime from STT_MODEL_BASE_URL (empty = download disabled).
// ---------------------------------------------------------------------------
val sherpaOnnxVersion = "1.13.4"
// The published release's checksum. Native code fetched over the network goes
// into every build, so it is checked before it is used: a replaced release, or
// anything tampered with on the way, fails the build instead of shipping.
val sherpaOnnxSha256 = "03f9c4df965f21c71269365a7951a7f23b5696fddd093fa318c80d65550ab780"
val sherpaOnnxAar = file("libs/sherpa-onnx-$sherpaOnnxVersion.aar")

fun sha256Of(f: File): String =
    MessageDigest.getInstance("SHA-256").let { digest ->
        f.inputStream().use { input ->
            val buffer = ByteArray(1 shl 16)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }
val downloadSherpaOnnx = tasks.register("downloadSherpaOnnx") {
    outputs.file(sherpaOnnxAar)
    doLast {
        val minBytes = 40_000_000L

        fun verify(f: File) {
            val actual = sha256Of(f)
            if (actual != sherpaOnnxSha256) {
                f.delete()
                throw GradleException(
                    "The sherpa-onnx runtime is not the release this project expects." + "\n" +
                        "  expected SHA-256: $sherpaOnnxSha256" + "\n" +
                        "  got:              $actual" + "\n" +
                        "  The file has been deleted. If the upstream release genuinely " +
                        "changed, update sherpaOnnxSha256 in app/build.gradle.kts.",
                )
            }
        }

        if (sherpaOnnxAar.exists() && sherpaOnnxAar.length() >= minBytes) {
            verify(sherpaOnnxAar)
            return@doLast
        }

        val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/" +
            "v$sherpaOnnxVersion/sherpa-onnx-$sherpaOnnxVersion.aar"
        sherpaOnnxAar.parentFile.mkdirs()
        // Download to a temp file and only move it into place once complete, so an
        // interrupted download can never leave a corrupt AAR behind. Retried,
        // because this is a ~48 MB fetch and clones are often on poor connections.
        val partial = File(sherpaOnnxAar.parentFile, "${sherpaOnnxAar.name}.part")
        var lastError: Exception? = null
        for (attempt in 1..3) {
            try {
                logger.lifecycle("Downloading sherpa-onnx runtime (attempt $attempt/3): $url")
                partial.delete()
                URI(url).toURL().openStream().use { input ->
                    partial.outputStream().use { output -> input.copyTo(output) }
                }
                if (partial.length() < minBytes) {
                    error("incomplete download (${partial.length()} bytes)")
                }
                verify(partial)
                partial.renameTo(sherpaOnnxAar)
                return@doLast
            } catch (e: Exception) {
                lastError = e
                logger.lifecycle("  failed: ${e.message}")
            }
        }
        partial.delete()
        throw GradleException(
            "Could not download the sherpa-onnx runtime after 3 attempts.\n" +
                "  URL: $url\n" +
                "  Fix: check your connection, or download it manually to " +
                "${sherpaOnnxAar.path}\n" +
                "  Cause: ${lastError?.message}",
        )
    }
}
// Default to the public model release so clones work out of the box;
// override via local.properties/env to self-host.
val sttModelBaseUrl = localOrEnv("STT_MODEL_BASE_URL").ifEmpty {
    "https://github.com/DrophouseLtd/sightbuddy-stt-models/releases/download/whisper-base-en-v1"
}

android {
    namespace = "com.example.sightbuddy"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.drophouse.sightbuddy"
        minSdk = 30
        targetSdk = 36
        versionCode = 14
        versionName = "2.3.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            val ksFile = localOrEnv("KEYSTORE_FILE")
            if (ksFile.isNotBlank()) {
                storeFile = file(ksFile)
                storePassword = localOrEnv("KEYSTORE_PASSWORD")
                keyAlias = localOrEnv("KEY_ALIAS")
                keyPassword = localOrEnv("KEY_PASSWORD")
            }
        }
    }

    flavorDimensions += "environment"

    productFlavors {
        create("dev") {
            dimension = "environment"
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-dev"
            buildConfigField("String", "STT_MODEL_BASE_URL", "\"${escaped(sttModelBaseUrl)}\"")
            // The OCR debug overlay and session logs (OcrDebug). Off here; the
            // testing branch feature/ocr-debug-overlay keeps it on.
            buildConfigField("boolean", "OCR_DEBUG_OVERLAY", "false")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "STT_MODEL_BASE_URL", "\"${escaped(sttModelBaseUrl)}\"")
            buildConfigField("boolean", "OCR_DEBUG_OVERLAY", "false")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Official builds (CI / local with a keystore configured) sign with the
            // upload key. Without one — OSS clones, local release testing — fall
            // back to debug signing so `assembleProdRelease` still produces an
            // installable APK. Play always re-signs with the real key anyway.
            signingConfig = if (localOrEnv("KEYSTORE_FILE").isNotBlank()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // GitHub-release APKs: arm64 only keeps the download reasonable.
    // (x86/emulator users build from source with x86_64 added to include().)
    // Splits only affect APK outputs — the AAB still carries every ABI, so
    // Play can serve 32-bit and x86 devices their own native libs.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a")
            isUniversalApk = false
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
}

// Don't fail when google-services.json is absent (dev flavor, or OSS clones
// without a Firebase project). Crashlytics simply stays inactive.
googleServices {
    missingGoogleServicesStrategy =
        com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy.IGNORE
}

// Uploading the R8 mapping file requires a real Firebase project. Without a
// google-services.json (any clone of this repo) that task fails and takes the
// whole release build with it — so skip it. Official builds ship the file and
// still get deobfuscated crash reports. Only prod has one: the dev flavor's
// release build (an R8 build installable beside the Play app) skips it too.
val hasGoogleServices = file("src/prod/google-services.json").exists()
tasks.matching { it.name.startsWith("uploadCrashlyticsMappingFile") }.configureEach {
    enabled = hasGoogleServices && name.contains("Prod")
}

tasks.named("preBuild") { dependsOn(downloadSherpaOnnx) }

dependencies {
    // Local Whisper STT runtime (both flavors)
    implementation(files(sherpaOnnxAar))

    // On-device Gemma (LiteRT-LM)
    implementation(libs.litertlm)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    // CameraX
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)

    // TensorFlow Lite + Task Vision
    implementation(libs.tensorflow.lite)
    implementation(libs.tensorflow.lite.task.vision)

    // OkHttp
    implementation(libs.okhttp)

    // Google ML Kit Text Recognition
    implementation(libs.google.mlkit.text.recognition)

    // Firebase Crashlytics (prod flavor only). No Analytics — crash diagnostics
    // only, no advertising ID, no behavioural tracking.
    "prodImplementation"(platform(libs.firebase.bom))
    "prodImplementation"(libs.firebase.crashlytics)

}
