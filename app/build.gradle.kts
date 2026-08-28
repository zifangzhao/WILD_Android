plugins {
    id("com.android.application")
    id("com.google.gms.google-services")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.wild.android"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.wild.android"
        // Firebase Authentication requires Android 6.0 (API 23) or newer.
        minSdk = 23
        targetSdk = 35
        versionCode = 5
        versionName = "0.1.4"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    buildTypes {
        debug {
            // Keep debug installs on the same package so phone testing uses the
            // same discovery cache, permissions, and launcher entry as the
            // user-facing app instead of creating a separate ".dev" variant.
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    buildFeatures {
        compose = true
    }

    androidResources {
        // Older Android package parsers can choke on newer compile-SDK manifest metadata
        // even when the APK itself still targets a lower minSdk.
        additionalParameters += "--no-compile-sdk-metadata"
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    // Keep the cloud gateway SDKs on a single compatible Firebase release set.
    val firebaseBom = platform("com.google.firebase:firebase-bom:34.17.0")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.3")
    implementation("androidx.activity:activity-compose:1.9.1")
    implementation("androidx.navigation:navigation-compose:2.7.7")

    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation(firebaseBom)

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    // Foundation for the WILD phone-as-gateway service. These dependencies do
    // not enable remote control by themselves: access remains governed by
    // Firebase Authentication and locked Firestore / callable-function rules.
    implementation("com.google.firebase:firebase-auth")
    // Keep the public API available to every build. The self-updating
    // implementation is debug-only so a future Play release cannot contain it.
    implementation("com.google.firebase:firebase-appdistribution-api:16.0.0-beta20")
    debugImplementation("com.google.firebase:firebase-appdistribution:16.0.0-beta20")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-functions")
    implementation("com.google.firebase:firebase-messaging")
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation("org.jetbrains.kotlin:kotlin-test-junit:2.2.10")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.13")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
