import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.google.services)
}

// API keys live in local.properties (gitignored). local.properties keys are NOT exposed via
// providers.gradleProperty(), so load the file explicitly and default to "" when absent.
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun secret(key: String): String = localProps.getProperty(key) ?: ""

android {
    namespace = "app.drivedelta"
    compileSdk = 35

    defaultConfig {
        applicationId = "app.drivedelta"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true

        manifestPlaceholders["MAPS_API_KEY"] = secret("MAPS_API_KEY")
        buildConfigField("String", "ROADS_API_KEY", "\"${secret("ROADS_API_KEY")}\"")
        buildConfigField("String", "PLACES_API_KEY", "\"${secret("PLACES_API_KEY")}\"")
    }

    buildTypes {
        debug {
            // No applicationIdSuffix: the google-services plugin matches google-services.json's
            // package_name against the full applicationId, and SETUP.md registers a single package
            // (app.drivedelta). A ".debug" suffix would fail the debug build with "no matching client".
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    sourceSets["main"].java.srcDirs("src/main/kotlin")
    sourceSets["test"].java.srcDirs("src/test/kotlin")
}

// Room schema export: emit versioned schema JSON under app/schemas for migration tracking.
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

kotlin {
    // Compile targeting JVM 17 bytecode using whatever JDK runs Gradle (Android Studio's JBR 21
    // is fine). Deliberately NOT jvmToolchain(17): that demands a JDK *exactly* 17 be installed or
    // downloadable, which fails on a machine that only has the newer bundled JBR.
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    // Lifecycle
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.navigation.compose)
    implementation(libs.androidx.hilt.work)
    ksp(libs.androidx.hilt.compiler)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // WorkManager
    implementation(libs.androidx.work.runtime.ktx)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)

    // Google Sign-In
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)

    // Location / Maps / Places
    implementation(libs.play.services.location)
    implementation(libs.maps.compose)
    implementation(libs.play.services.maps)
    implementation(libs.places)

    // Network / serialization
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.android)

    // Charts
    implementation(libs.vico.compose.m3)

    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.turbine)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
