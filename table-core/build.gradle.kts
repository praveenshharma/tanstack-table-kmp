import com.vanniktech.maven.publish.SonatypeHost
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
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
        commonTest.dependencies {
            implementation(libs.kotlin.test)
        }
    }
}

android {
    namespace = "io.github.tanstacktable.core"
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
// are provided (see docs/PUBLISHING.md). The plugin still applies — it just
// won't have anywhere to push to without group / version / credentials.
val pomGroupId = providers.gradleProperty("ttkmp.groupId").orNull
val pomVersion = providers.gradleProperty("ttkmp.version").orNull
if (pomGroupId != null && pomVersion != null) {
    group = pomGroupId
    version = pomVersion

    mavenPublishing {
        publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL, automaticRelease = false)
        signAllPublications()

        pom {
            name.set("tanstack-table-core")
            description.set(
                "Kotlin Multiplatform port of TanStack Table v8 — headless table engine.",
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
