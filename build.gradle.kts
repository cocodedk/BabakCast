// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.hilt.android) apply false
}

tasks.register("buildSmoke") {
    group = "verification"
    description = "Build debug, run unit tests, and lint to ensure a clean build."
    // The "distribution" flavor dimension (github/fdroid) means there is no bare
    // testDebugUnitTest/lintDebug task anymore; both flavors are named explicitly so
    // CI keeps covering both, not just whichever one happens to configure first.
    dependsOn(
        ":app:assembleGithubDebug",
        ":app:assembleFdroidDebug",
        ":app:testGithubDebugUnitTest",
        ":app:testFdroidDebugUnitTest",
        ":app:lintGithubDebug",
        ":app:lintFdroidDebug"
    )
}
