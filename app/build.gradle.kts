import java.net.URI
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
val sherpaOnnxAar = file("libs/sherpa-onnx-$sherpaOnnxVersion.aar")
val downloadSherpaOnnx = tasks.register("downloadSherpaOnnx") {
    outputs.file(sherpaOnnxAar)
    doLast {
        if (!sherpaOnnxAar.exists() || sherpaOnnxAar.length() < 40_000_000L) {
            sherpaOnnxAar.parentFile.mkdirs()
            val url = "https://github.com/k2-fsa/sherpa-onnx/releases/download/" +
                "v$sherpaOnnxVersion/sherpa-onnx-$sherpaOnnxVersion.aar"
            logger.lifecycle("Downloading $url")
            URI(url).toURL().openStream().use { input ->
                sherpaOnnxAar.outputStream().use { output -> input.copyTo(output) }
            }
        }
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
        versionCode = 10
        versionName = "2.1.0"

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
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "STT_MODEL_BASE_URL", "\"${escaped(sttModelBaseUrl)}\"")
        }
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            // GitHub-release APKs: arm64 only keeps the download reasonable.
            // (x86/emulator users build from source with this line removed.)
            ndk { abiFilters += listOf("arm64-v8a") }
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

tasks.named("preBuild") { dependsOn(downloadSherpaOnnx) }

dependencies {
    // Local Whisper STT runtime (both flavors)
    implementation(files(sherpaOnnxAar))

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
