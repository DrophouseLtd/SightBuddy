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
// Production Supabase (from local.properties or CI env)
// ---------------------------------------------------------------------------
val prodSupabaseUrl = localOrEnv("SUPABASE_URL")
val prodSupabaseKey = localOrEnv("SUPABASE_PUBLISHABLE_KEY")
val prodChatUrl = if (prodSupabaseUrl.isEmpty()) "" else "${prodSupabaseUrl.trimEnd('/')}/functions/v1/chat"
val prodFeedbackUrl = if (prodSupabaseUrl.isEmpty()) "" else "${prodSupabaseUrl.trimEnd('/')}/functions/v1/feedback"
val prodDeleteDataUrl = if (prodSupabaseUrl.isEmpty()) "" else "${prodSupabaseUrl.trimEnd('/')}/functions/v1/delete-my-data"

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
    "https://github.com/DrophouseLtd/sightbuddy/releases/download/stt-models-v1"
}

// ---------------------------------------------------------------------------
// Mock Supabase (hardcoded — safe to commit)
// ---------------------------------------------------------------------------
val devSupabaseUrl = "https://ghwkdczqihxwhtynpwmd.supabase.co"
val devSupabaseKey = "sb_publishable_KG59DggNXuusz4p0RVDldQ_7-3kCerO"
val devChatUrl = "$devSupabaseUrl/functions/v1/chat"
val devFeedbackUrl = "$devSupabaseUrl/functions/v1/feedback"
val devDeleteDataUrl = "$devSupabaseUrl/functions/v1/delete-my-data"

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
        versionCode = 6
        versionName = "1.1.4"

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
            buildConfigField("String", "SUPABASE_URL", "\"${escaped(devSupabaseUrl)}\"")
            buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${escaped(devSupabaseKey)}\"")
            buildConfigField("String", "SUPABASE_CHAT_URL", "\"${escaped(devChatUrl)}\"")
            buildConfigField("String", "SUPABASE_FEEDBACK_URL", "\"${escaped(devFeedbackUrl)}\"")
            buildConfigField("String", "SUPABASE_DELETE_DATA_URL", "\"${escaped(devDeleteDataUrl)}\"")
            buildConfigField("String", "STT_MODEL_BASE_URL", "\"${escaped(sttModelBaseUrl)}\"")
        }
        create("prod") {
            dimension = "environment"
            buildConfigField("String", "SUPABASE_URL", "\"${escaped(prodSupabaseUrl)}\"")
            buildConfigField("String", "SUPABASE_PUBLISHABLE_KEY", "\"${escaped(prodSupabaseKey)}\"")
            buildConfigField("String", "SUPABASE_CHAT_URL", "\"${escaped(prodChatUrl)}\"")
            buildConfigField("String", "SUPABASE_FEEDBACK_URL", "\"${escaped(prodFeedbackUrl)}\"")
            buildConfigField("String", "SUPABASE_DELETE_DATA_URL", "\"${escaped(prodDeleteDataUrl)}\"")
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
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
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

// Google Services plugin: don't fail when google-services.json is absent (dev flavor)
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

    // Firebase Crashlytics (prod flavor only)
    "prodImplementation"(platform(libs.firebase.bom))
    "prodImplementation"(libs.firebase.crashlytics)
    "prodImplementation"(libs.firebase.analytics)

    // Google Play Integrity (prod flavor only)
    "prodImplementation"(libs.play.integrity)

    // In-app updates (prod flavor only)
    "prodImplementation"(libs.play.app.update)
    "prodImplementation"(libs.play.app.update.ktx)
}
