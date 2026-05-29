plugins {
    id("com.android.application")
}

android {
    namespace = "com.codex.whatsappguard"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.codex.whatsappguard"
        minSdk = 26
        //noinspection EditedTargetSdkVersion
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
