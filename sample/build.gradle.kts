import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // The same three iOS targets as `:table-compose`. Each emits a `framework`
    // binary named `SampleApp`; the iOS shell (`iosApp/`) links this framework
    // and calls `MainViewControllerKt.MainViewController()` to mount the Compose
    // UI in a UIViewController.
    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { target ->
        target.binaries.framework {
            baseName = "SampleApp"
            // Built as a dynamic framework so Compose Multiplatform's iOS runtime
            // (Skiko + its native dependencies — skshaper, skunicode, …) is
            // self-contained inside the .framework dylib; a static framework
            // leaves Skiko's C symbols unresolved at `swiftc` link time.
            isStatic = false
        }
    }

    sourceSets {
        commonMain.dependencies {
            implementation(project(":table-compose"))
            // Compose Multiplatform — the example screens use foundation /
            // material3 widgets, all multiplatform.
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
        }
        androidMain.dependencies {
            // Android-only: ComponentActivity + setContent live here.
            implementation(libs.androidx.activity.compose)
        }
    }
}

android {
    namespace = "io.github.tanstacktable.sample"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()
    // KMP modules don't use the default `src/main/AndroidManifest.xml` lookup;
    // the manifest lives under `androidMain/`.
    sourceSets["main"].manifest.srcFile("src/androidMain/AndroidManifest.xml")

    defaultConfig {
        applicationId = "io.github.tanstacktable.sample"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
        }
    }
}
