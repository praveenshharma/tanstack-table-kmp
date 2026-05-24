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

// Maven Central publishing. Dormant until POM_GROUP_ID and POM_ARTIFACT_VERSION
// are provided (see docs/PUBLISHING.md). The plugin still applies — it just
// won't have anywhere to push to without group / version / credentials.
val pomGroupId = providers.gradleProperty("POM_GROUP_ID").orNull
val pomVersion = providers.gradleProperty("POM_ARTIFACT_VERSION").orNull
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
            url.set(providers.gradleProperty("POM_URL").get())
            licenses {
                license {
                    name.set("MIT License")
                    url.set("https://opensource.org/licenses/MIT")
                    distribution.set("repo")
                }
            }
            developers {
                developer {
                    id.set(providers.gradleProperty("POM_DEVELOPER_ID").get())
                    name.set(providers.gradleProperty("POM_DEVELOPER_NAME").get())
                    email.set(providers.gradleProperty("POM_DEVELOPER_EMAIL").orNull)
                }
            }
            scm {
                val scmUrl = providers.gradleProperty("POM_SCM_URL").get()
                url.set(scmUrl)
                connection.set("scm:git:$scmUrl.git")
                developerConnection.set("scm:git:$scmUrl.git")
            }
        }
    }
}
