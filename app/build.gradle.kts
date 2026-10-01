plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.volo.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.volo.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
    }

    buildFeatures { compose = true }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    configurations.all {
        // Ruling (ledger Row 3): clientlib-ktx 2.1.1 transitively demands androidx activity 1.13 /
        // core 1.18 (compileSdk 36 + AGP 8.9.1). Pin to the versions this project already builds
        // with; the ktx APIs used (registerForActivityResult, lifecycle.whenResumed) exist here.
        resolutionStrategy {
            force("androidx.activity:activity:1.10.1")
            force("androidx.activity:activity-ktx:1.10.1")
            force("androidx.activity:activity-compose:1.10.1")
            force("androidx.core:core:1.15.0")
            force("androidx.core:core-ktx:1.15.0")
        }
        // navigationevent (AGP 8.9.1+, Kotlin 2.1 classes) breaks the Kotlin 2.0.21 analyzer and
        // is not referenced by the ktx association APIs we use.
        exclude(group = "androidx.navigationevent")
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.9.2")
    implementation("androidx.work:work-runtime-ktx:2.10.1")
    implementation("androidx.room:room-runtime:2.7.1")
    implementation("androidx.room:room-ktx:2.7.1")
    ksp("androidx.room:room-compiler:2.7.1")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("com.solanamobile:mobile-wallet-adapter-clientlib:2.1.1")
    implementation("com.solanamobile:mobile-wallet-adapter-clientlib-ktx:2.1.1")
    implementation("com.solanamobile:mobile-wallet-adapter-common:2.1.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    // Android unit tests get the stubbed android.jar org.json that throws "not mocked";
    // the real implementation comes from this test dependency (used by IdlParser).
    testImplementation("org.json:json:20240303")
}
