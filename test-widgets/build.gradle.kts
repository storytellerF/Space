plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.storyteller_f.space_launcher.testwidgets"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.storyteller_f.space_launcher.testwidgets"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
