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

    signingConfigs {
        // A fixed key kept in the repository so that every new APK installs over the previous
        // one (and keeps its data). Fine for a personal, side-loaded app; not for a store release.
        create("sideload") {
            storeFile = file("jelly-sideload.jks")
            storePassword = "jellycalendar"
            keyAlias = "jelly"
            keyPassword = "jellycalendar"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("sideload")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("sideload")
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
}
