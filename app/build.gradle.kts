plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.custom.galaxylmssender"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.custom.galaxylmssender"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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

dependencies {
    // 외부 무거운 라이브러리 의존성 없이 순수 안드로이드 SDK로만 가볍고 빠르고 오류 없이 빌드됩니다.
}
