plugins {
    alias(libs.plugins.android.application)
}

val signKey: String? = System.getenv("space_sign_key")
val signAlias: String? = System.getenv("space_sign_alias")
val signStorePassword: String? = System.getenv("space_sign_store_password")
val signKeyPassword: String? = System.getenv("space_sign_key_password")

android {
    namespace = "com.storyteller_f.space_launcher"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.storyteller_f.space_launcher"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        val signStorePath = if (signKey != null) File(System.getProperty("user.home"), "signing_key.jks") else null
        if (signStorePath != null && signAlias != null && signStorePassword != null && signKeyPassword != null) {
            create("release") {
                keyAlias = signAlias
                keyPassword = signKeyPassword
                storeFile = signStorePath
                storePassword = signStorePassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val releaseSignConfig = signingConfigs.findByName("release")
            if (releaseSignConfig != null) signingConfig = releaseSignConfig
        }
        create("daily") {
            initWith(getByName("release"))
            applicationIdSuffix = ".daily"
            versionNameSuffix = "-daily"
            matchingFallbacks += listOf("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}