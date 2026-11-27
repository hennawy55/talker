plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val talkerVersionCode = providers.gradleProperty("talkerVersionCode").orElse("1").get().toInt()
val talkerVersionName = providers.gradleProperty("talkerVersionName").orElse("0.1.0").get()

android {
    namespace = "com.talker.offline"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.talker.offline"
        minSdk = 26
        targetSdk = 35
        versionCode = talkerVersionCode
        versionName = talkerVersionName
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
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
