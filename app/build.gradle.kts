import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.nopeek.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nopeek.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        // Task 2 (S1 spike): instrumentation is the only way to reach the real
        // AndroidKeyStore — a JVM unit test runs against the stubbed android.jar.
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            // Credentials live in untracked keystore.properties (gitignored); the
            // keystore itself sits outside the repo (~/.android/Nopeek.keystore).
            // Never hardcode passwords here — the history was scrubbed once already.
            val propsFile = rootProject.file("keystore.properties")
            if (propsFile.exists()) {
                val props = Properties().apply {
                    propsFile.inputStream().use { load(it) }
                }
                props.getProperty("storeFile")?.let { storeFile = rootProject.file(it) }
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // Plan 12.1 Steps 1–2: minify + resource shrink for < 20 MB APK.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    buildFeatures { compose = true }

    // Robolectric-driven Compose tests (ComposeHarnessSmokeTest,
    // InteractiveComponentContractTest) launch androidx.activity.ComponentActivity,
    // which is contributed to the merged debug manifest by ui-test-manifest. Without
    // the merged manifest and resources on the JVM test classpath Robolectric cannot
    // resolve that activity and every such test dies with
    // "Unable to resolve activity for Intent ... ComponentActivity".
    testOptions { unitTests.isIncludeAndroidResources = true }

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

    testOptions {
        // Robolectric needs the merged manifest and resources so Compose can launch an activity
        // and resolve fonts on the JVM — the UI contract tests then gate without a device.
        unitTests {
            isIncludeAndroidResources = true
        }
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
    // Compose UI contract tests on the JVM: no device is attached during the hackathon window,
    // so the gate that keeps dead controls and unreadable text from shipping has to run here.
    testImplementation(composeBom)
    testImplementation("androidx.compose.ui:ui-test-junit4")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("androidx.test.ext:junit:1.2.1")
    testImplementation("org.robolectric:robolectric:4.14.1")
    // Must be debugImplementation, not testImplementation: Robolectric unit tests read the *debug
    // variant's merged manifest*, and this is what registers ComponentActivity for createComposeRule.
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    // Task 2: hardware attestation can only be probed on device.
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    // Android unit tests get the stubbed android.jar org.json that throws "not mocked";
    // the real implementation comes from this test dependency (used by IdlParser).
    testImplementation("org.json:json:20240303")
}
