plugins { id("com.android.application") }

android {
    buildFeatures { buildConfig = true }
    buildTypes {
        getByName("debug") {
            val online = providers.gradleProperty("localOnlineVoice").orNull == "true"
            buildConfigField("boolean", "LOCAL_ONLINE_VOICE", online.toString())
            if (online) versionNameSuffix = "-online-test"
        }
        getByName("release") { buildConfigField("boolean", "LOCAL_ONLINE_VOICE", "false") }
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
