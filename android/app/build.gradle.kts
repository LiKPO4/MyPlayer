plugins {
    id("com.android.application")
    id("kotlin-android")
    id("dev.flutter.flutter-gradle-plugin")
}

val releaseKeystorePath = System.getenv("MYPLAYER_KEYSTORE_PATH")

android {
    namespace = "com.lijialin.myplayer"
    compileSdk = 36
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        applicationId = "com.lijialin.myplayer"
        minSdk = flutter.minSdkVersion
        targetSdk = 36
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    signingConfigs {
        if (!releaseKeystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("MYPLAYER_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MYPLAYER_KEY_ALIAS")
                keyPassword = System.getenv("MYPLAYER_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (releaseKeystorePath.isNullOrBlank()) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.getByName("release")
            }
        }
    }
}

flutter {
    source = "../.."
}

dependencies {
    val media3Version = "1.10.1"

    implementation("androidx.documentfile:documentfile:1.1.0")
    // ViewPager2 supplies the native, interruptible vertical paging physics used by the video feed.
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
}
