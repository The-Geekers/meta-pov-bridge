plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "fr.thefrenchgeekers.metapov"
    compileSdk = 36

    defaultConfig {
        applicationId = "fr.thefrenchgeekers.metapov"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"

        manifestPlaceholders["mwdat_application_id"] =
            project.findProperty("MWDAT_APPLICATION_ID")?.toString() ?: ""
        manifestPlaceholders["mwdat_client_token"] =
            project.findProperty("MWDAT_CLIENT_TOKEN")?.toString() ?: ""
    }

    buildFeatures {
        compose = true
        buildConfig = true
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
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.10.0")

    implementation("com.meta.wearable:mwdat-core:1.0.0")
    implementation("com.meta.wearable:mwdat-camera:1.0.0")
    implementation("com.meta.wearable:mwdat-mockdevice:1.0.0")

    implementation("io.github.thibaultbee.srtdroid:srtdroid-core:1.9.5")
    implementation("io.github.thibaultbee.srtdroid:srtdroid-ktx:1.9.5")
}
