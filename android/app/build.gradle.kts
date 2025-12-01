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

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
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
    
    packaging {
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
    // Java Stub 파일이 Hidden API 접근을 제공함 (AIDL + Stub 방식)
    // libs/android.jar는 더 이상 필요 없음
}

flutter {
    source = "../.."
}
