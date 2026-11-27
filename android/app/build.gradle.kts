plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val talkerVersionCode = providers.gradleProperty("talkerVersionCode").orElse("1").get().toInt()
val talkerVersionName = providers.gradleProperty("talkerVersionName").orElse("0.1.0").get()
val audioCppSource = providers.gradleProperty("audioCppSourceDir").orElse("../../../../third_party/audio.cpp").get()

android {
    namespace = "com.talker.offline"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.talker.offline"
        minSdk = 26
        targetSdk = 35
        versionCode = talkerVersionCode
        versionName = talkerVersionName
        ndk { abiFilters += "arm64-v8a" }
        externalNativeBuild {
            cmake {
                arguments += listOf("-DAUDIOCPP_SOURCE_DIR=${project.rootDir}/../third_party/audio.cpp")
                cppFlags += listOf("-std=c++17")
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    signingConfigs {
        create("release") {
            val storePath = System.getenv("TALKER_KEYSTORE_PATH")
            val storePass = System.getenv("TALKER_KEYSTORE_PASSWORD")
            val alias = System.getenv("TALKER_KEY_ALIAS")
            val keyPass = System.getenv("TALKER_KEY_PASSWORD")
            if (!storePath.isNullOrBlank()) {
                storeFile = file(storePath)
                storePassword = storePass
                keyAlias = alias
                keyPassword = keyPass
            }
        }
    }
    buildTypes {
        release { isMinifyEnabled = false; signingConfig = signingConfigs.getByName("release") }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
