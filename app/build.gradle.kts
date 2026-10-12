plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.andene.fern"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.andene.fern"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        // On-device benchmarks (DeviceScaleBenchmark). Debug builds are debuggable, which slows
        // them; the numbers still compare runs on one device, so that error is suppressed.
        testInstrumentationRunner = "androidx.benchmark.junit4.AndroidBenchmarkRunner"
        testInstrumentationRunnerArguments["androidx.benchmark.suppressErrors"] = "DEBUGGABLE,EMULATOR,LOW-BATTERY,UNLOCKED"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        // CrashReporter stamps reports with the app version from BuildConfig.
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.5.14"
    }

    testOptions {
        // Robolectric runs the Android-dependent tests (storage, Compose UI) on the JVM and needs
        // the merged resources and manifest to do so.
        unitTests.isIncludeAndroidResources = true
        // One JVM per test class. Sharing one JVM, the Compose UI tests intermittently never went
        // idle (AppNotIdleException) once enough Robolectric classes had run before them -
        // reproducible only in a full run, never with the class alone or after any single other
        // class, and with the toolbar code unchanged - so state leaking between test classes,
        // not the code under test. Isolating classes removes the leak path.
        unitTests.all {
            it.forkEvery = 1
            // The layered-format round trips (LayeredFormatsTest) export at the 4096 px edge: the
            // image, a ~100 MB .psd and its 16-bit decoded layers together exceed Gradle's default
            // 512 MB test heap.
            it.maxHeapSize = "2g"
            // Scale benchmarks (ScaleBenchmark) only run when asked: ./gradlew testDebugUnitTest -Pbenchmark
            // --tests '*ScaleBenchmark'. A million strokes needs more heap than the default.
            if (project.hasProperty("benchmark")) {
                it.systemProperty("fern.benchmark", "true")
                it.maxHeapSize = "4g"
            }
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    // MediaPipe maps its model straight from the APK, which needs it stored uncompressed (roadmap item 146).
    androidResources {
        noCompress += "task"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("androidx.activity:activity-compose:1.9.1")
    // Predicts where the pen is heading so ink can be drawn slightly ahead, hiding input latency.
    implementation("androidx.input:input-motionprediction:1.0.0")
    // Front-buffered rendering for low-latency ink (FrontBufferedInk).
    implementation("androidx.graphics:graphics-core:1.0.2")
    // QR code encoding for the QR code tool (roadmap item 144); the core library only, no platform services.
    implementation("com.google.zxing:core:3.5.3")
    // Hand landmark detection for the hand pose scanner (roadmap item 146): runs on the device,
    // no platform services; the model ships in assets/hand_landmarker.task.
    implementation("com.google.mediapipe:tasks-vision:0.10.14")

    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")
    testImplementation("androidx.test:core-ktx:1.6.1")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    // Hosts composables under test in an empty activity; must be on the debug runtime classpath.
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation("androidx.benchmark:benchmark-junit4:1.2.4")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
}
