plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
}

android {
    namespace = "android.test.settings"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion
    
    // AIDL 및 Java stub 소스 경로 추가
    sourceSets {
        getByName("main") {
            aidl.srcDirs("src/main/aidl")
            java.srcDirs("src/main/java", "src/main/kotlin")
        }
    }
    
    // AIDL 기능 활성화
    buildFeatures {
        aidl = true
    }

    // Disable BlockedPrivateApi lint error to allow reflection for hidden APIs
    // The reflection is wrapped in try-catch for graceful runtime fallback
    lint {
        disable += "BlockedPrivateApi"
        checkReleaseBuilds = true
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "android.test.settings"
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = 35
        versionCode = flutter.versionCode
        versionName = flutter.versionName
        
        // Native library 압축 비활성화 (서명 후 설치 오류 방지)
        ndk {
            debugSymbolLevel = "FULL"
        }
    }
    
    packagingOptions {
        jniLibs {
            useLegacyPackaging = true  // .so 파일 압축 없이 저장
        }
    }

    buildTypes {
        release {
            // TODO: Add your own signing config for the release build.
            // Signing with the debug keys for now, so `flutter run --release` works.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
}

dependencies {
    // SDK android.jar가 이미 Hidden API 버전으로 교체되어 별도 설정 불필요
}

flutter {
    source = "../.."
}
