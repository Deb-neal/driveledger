import java.util.Properties

plugins { id("com.android.application") }

val releaseSigningFile = rootProject.file("release-signing.properties")
val releaseSigning = Properties().apply {
    if (releaseSigningFile.exists()) releaseSigningFile.inputStream().use { load(it) }
}

// This token limits access to the relay; it is extractable from a public APK.
// The OpenAI provider key must never be supplied to the Android build.
val voiceConfigFile = rootProject.file("voice-release.properties")
val voiceConfig = Properties().apply {
    if (voiceConfigFile.exists()) voiceConfigFile.inputStream().use { load(it) }
}
val voiceEndpoint = voiceConfig.getProperty("endpoint", "")
val voiceToken = voiceConfig.getProperty("relayToken", "")
if (voiceConfigFile.exists()) {
    require(voiceEndpoint.matches(Regex("https://[a-z0-9][a-z0-9.-]+/transcribe"))) {
        "Voice endpoint must be an HTTPS host with /transcribe and no credentials, query or port"
    }
    require(voiceToken.matches(Regex("[a-f0-9]{64}"))) { "Relay token must be 64 lowercase hex characters" }
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
        getByName("debug") {
            val online = providers.gradleProperty("localOnlineVoice").orNull == "true"
            buildConfigField("boolean", "LOCAL_ONLINE_VOICE", online.toString())
            buildConfigField("boolean", "ONLINE_VOICE", (online || voiceConfigFile.exists()).toString())
            buildConfigField("String", "VOICE_ENDPOINT", "\"${if (online) "http://127.0.0.1:18765/transcribe" else voiceEndpoint}\"")
            buildConfigField("String", "VOICE_TOKEN", "\"${if (online) "" else voiceToken}\"")
            if (online) versionNameSuffix = "-online-test"
            else if (voiceConfigFile.exists()) versionNameSuffix = "-cloud-test"
        }
        getByName("release") {
            buildConfigField("boolean", "LOCAL_ONLINE_VOICE", "false")
            buildConfigField("boolean", "ONLINE_VOICE", voiceConfigFile.exists().toString())
            buildConfigField("String", "VOICE_ENDPOINT", "\"$voiceEndpoint\"")
            buildConfigField("String", "VOICE_TOKEN", "\"$voiceToken\"")
            if (releaseSigningFile.exists()) signingConfig = signingConfigs.getByName("submission")
        }
    }
    namespace = "com.iancompany.voiceledger"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "com.iancompany.voiceledger"
        minSdk = 28
        targetSdk = 34
        versionCode = 1
        versionName = "0.3.0-phase3"
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
    implementation(files("libs/sherpa-onnx-2.0.1.aar"))
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
}
