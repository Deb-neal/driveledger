import java.util.Properties

plugins { id("com.android.application") }

val releaseSigningFile = rootProject.file("release-signing.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.exists()) releaseSigningFile.inputStream().use { load(it) }
}

android {
    buildFeatures { buildConfig = true }
    if (releaseSigningFile.exists()) {
        signingConfigs.create("submission") {
            fun required(name: String): String = requireNotNull(releaseSigning.getProperty(name)?.takeIf { it.isNotBlank() }) {
                "Missing $name in release-signing.properties"
            }
            storeFile = rootProject.file(required("storeFile"))
            storePassword = required("storePassword")
            keyAlias = required("keyAlias")
            keyPassword = required("keyPassword")
        }
    }
    buildTypes {
        // Legacy relay diagnostics remain disabled; product builds use Pleos ON_DEVICE only.
        all {
            buildConfigField("boolean", "LOCAL_ONLINE_VOICE", "false")
            buildConfigField("boolean", "ONLINE_VOICE", "false")
            buildConfigField("String", "VOICE_ENDPOINT", "\"\"")
            buildConfigField("String", "VOICE_TOKEN", "\"\"")
        }
        getByName("debug") { versionNameSuffix = "-debug" }
        getByName("release") {
            if (releaseSigningFile.exists()) signingConfig = signingConfigs.getByName("submission")
        }
    }
    namespace = "com.iancompany.voiceledger"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "com.iancompany.voiceledger"
        minSdk = 28
        targetSdk = 34
        versionCode = 2
        versionName = "0.4.0-pleos-stt"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("arm64-v8a", "x86_64") }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation("ai.pleos.playground:Vehicle:2.3.2")
    implementation("ai.pleos.playground:SpeechToText:3.3.1")
    implementation(files("libs/sherpa-onnx-2.0.1.aar"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
