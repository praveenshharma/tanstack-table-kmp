plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.dokka)
    alias(libs.plugins.vanniktechMavenPublish) apply false
}

// Aggregate Dokka HTML for the published modules (:table-core, :table-compose).
// Run `./gradlew dokkaHtmlMultiModule` to generate, output lands in build/dokka/htmlMultiModule.
tasks.named("dokkaHtmlMultiModule") {
    // No-op customisation today; placeholder for future logo / footer / module-list overrides.
}
