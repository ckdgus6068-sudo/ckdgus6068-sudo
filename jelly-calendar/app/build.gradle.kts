import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Every GitHub Actions build gets a higher version, so the phone always sees an update as newer
// and the settings screen can show which build is installed. Local builds are 0.2.0.
val ciRun = System.getenv("GITHUB_RUN_NUMBER")?.toIntOrNull() ?: 0

android {
    namespace = "io.github.ckdgus6068.jellycalendar"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.ckdgus6068.jellycalendar"
        minSdk = 26
        targetSdk = 35
        versionCode = 1 + ciRun
        versionName = "0.2.$ciRun"
    }

    // The release key is private: CI writes it from the JELLY_KEYSTORE_BASE64 secret to a file and
    // passes the file and its password in JELLY_KEYSTORE_FILE and JELLY_KEYSTORE_PASSWORD. Every
    // APK signed with it installs over the previous one and keeps its data.
    val releaseKeystore = System.getenv("JELLY_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("JELLY_KEYSTORE_PASSWORD")
                keyAlias = "jelly"
                keyPassword = System.getenv("JELLY_KEYSTORE_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without the key (a fork, or before it is set up) the APK gets the debug key; CI checks
            // the certificate and never publishes such a build.
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    // "구글로 시작하기" in the shared tab: the phone's Google account sheet (Credential Manager).
    implementation(libs.credentials.core)
    implementation(libs.credentials.playservices)
    implementation(libs.googleid)
}
