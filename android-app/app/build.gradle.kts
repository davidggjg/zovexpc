plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.zovex.pc"
    compileSdk = 35
    ndkVersion = "27.2.12479018"

    defaultConfig {
        applicationId = "com.zovex.pc"
        // Android 10 blocks executing files from the app's own directory, but
        // only for apps that declare targetSdk 29 or higher. The whole
        // runtime is executables in app storage, so 28 is the exemption this
        // design depends on — the same reason Termux still targets 28.
        // Distribution is via GitHub, not Play, so no store floor applies.
        minSdk = 26
        targetSdk = 28
        // CI passes the run number so every build is an upgrade over the last
        // one. Builds from a workstation stay at 1.
        versionCode = (project.findProperty("zovex.versionCode") as String?)?.toInt() ?: 1
        versionName = (project.findProperty("zovex.versionName") as String?) ?: "0.1.0-dev"

        // Stage 1 target hardware: ARM64 only. Do not add armeabi-v7a —
        // Box64 requires a 64-bit host process.
        ndk { abiFilters += "arm64-v8a" }

        externalNativeBuild {
            cmake {
                arguments += listOf("-DANDROID_STL=c++_shared")
                cppFlags += "-std=c++20"
            }
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    // One fixed key for every build, debug and release alike. Android refuses
    // to install an update signed by a different key, so without this the user
    // has to uninstall first and loses their whole C:\ drive. The key is in the
    // repository on purpose: it protects nothing yet, and a lost key would mean
    // no upgrade path for anyone already running the app. Replace it with a
    // secret before any public release.
    signingConfigs {
        create("zovex") {
            storeFile = rootProject.file("keystore/zovex-release.jks")
            storePassword = "zovexpc"
            keyAlias = "zovex"
            keyPassword = "zovexpc"
        }
    }

    buildTypes {
        debug {
            isJniDebuggable = true
            signingConfig = signingConfigs.getByName("zovex")
        }
        release {
            signingConfig = signingConfigs.getByName("zovex")
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    lint {
        // Lint fails the release build over targetSdk 28 because Play
        // requires 33+. This app is distributed through GitHub, and 28 is
        // what lets the runtime execute at all, so the Play floor does not
        // apply. Revisit only if this is ever published to Play, which would
        // mean finding another way to run binaries.
        disable += "ExpiredTargetSdkVersion"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging {
        jniLibs {
            // Wine/Box64 payloads are extracted at runtime, not packed as JNI libs.
            useLegacyPackaging = false
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation(platform("androidx.compose:compose-bom:2024.11.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    // Reads the rootfs tarball, preserving the Unix modes ZIP cannot carry.
    implementation("org.apache.commons:commons-compress:1.27.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
