import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktechMavenPublish)
}

kotlin {
    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosX64()
    iosArm64()
    iosSimulatorArm64()

    sourceSets {
        commonMain.dependencies {
            // The headless engine. `api` so consumers of :table-compose also see
            // `Table`, `ColumnDef`, `createColumnHelper`, the row-model factories, etc.
            api(project(":table-core"))
            // Compose runtime — `rememberTable` uses `@Composable` / `remember` /
            // `mutableStateOf`; `flexRender` is plain Kotlin.
            implementation(compose.runtime)
            // foundation + ui — `TableGrid` uses `SubcomposeLayout`, `BasicText`,
            // `Modifier.border` / `Modifier.padding`. Deliberately NOT material —
            // the adapter stays theme-neutral so consumers can use Material2,
            // Material3, Cupertino, or a custom theme.
            implementation(compose.foundation)
            implementation(compose.ui)
        }
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "io.github.tanstacktable.compose"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// Maven Central publishing. Dormant until ttkmp.groupId and ttkmp.version
// are provided (see docs/PUBLISHING.md).
val pomGroupId = providers.gradleProperty("ttkmp.groupId").orNull
val pomVersion = providers.gradleProperty("ttkmp.version").orNull
if (pomGroupId != null && pomVersion != null) {
    group = pomGroupId
    version = pomVersion

    mavenPublishing {
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
        signAllPublications()

        pom {
            name.set("tanstack-table-compose")
            description.set(
                "Compose Multiplatform adapter for tanstack-table-core — " +
                    "rememberTable, flexRender, and TableGrid.",
            )
            inceptionYear.set("2026")
            url.set(providers.gradleProperty("ttkmp.url").get())
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set(providers.gradleProperty("ttkmp.developerId").get())
                    name.set(providers.gradleProperty("ttkmp.developerName").get())
                    email.set(providers.gradleProperty("ttkmp.developerEmail").orNull)
                }
            }
            scm {
                val scmUrl = providers.gradleProperty("ttkmp.scmUrl").get()
                url.set(scmUrl)
                connection.set("scm:git:$scmUrl.git")
                developerConnection.set("scm:git:$scmUrl.git")
            }
        }
    }
}
