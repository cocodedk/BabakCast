import java.io.File
import java.util.Properties
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.gradle.testing.jacoco.tasks.JacocoReport
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
    id("jacoco")
}

// Signing inputs, in precedence order: env vars (CI), Gradle properties, then
// local.properties. Gradle properties are the durable home on a developer machine:
// ~/.gradle/gradle.properties sits outside the project, so Android Studio cannot
// erase it the way it regenerates local.properties — which silently drops signing
// values and makes every later build unsigned. local.properties is still read so
// existing setups keep working.
//
// Both debug and release builds use the same keystore when configured, so update-
// in-place between locally-built debug APKs and the published release APKs works
// without uninstalling and losing app data.
val localSigningProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
fun signingValue(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(name).orNull?.takeIf { it.isNotBlank() }
        ?: localSigningProps.getProperty(name)?.takeIf { it.isNotBlank() }

val signingKeystorePassword = signingValue("KEYSTORE_PASSWORD")
val signingKeyAlias = signingValue("KEY_ALIAS")
val signingKeyPassword = signingValue("KEY_PASSWORD")
// A relative KEYSTORE_PATH is resolved against the project root rather than the
// working directory, so it means the same thing wherever gradle is invoked from.
val signingKeystoreFile = signingValue("KEYSTORE_PATH")?.let { path ->
    File(path).takeIf(File::isAbsolute) ?: rootProject.file(path)
}
val hasSigningConfig = signingKeystoreFile?.exists() == true &&
    signingKeyAlias != null &&
    signingKeystorePassword != null &&
    signingKeyPassword != null

android {
    namespace = "com.cocode.babakcast"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cocode.babakcast"
        minSdk = 24
        targetSdk = 36
        // The version lives in gradle.properties, where the release workflow and
        // F-Droid's checkupdates both read it. See the comment there before bumping it.
        versionCode = providers.gradleProperty("VERSION_CODE").get().toInt()
        versionName = providers.gradleProperty("VERSION_NAME").get()

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // AGP otherwise adds a Google-encrypted dependency list to the APK signing block,
    // and F-Droid rejects any release APK that carries it.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    flavorDimensions += "distribution"
    productFlavors {
        // Today's GitHub release: yt-dlp keeps itself current via a runtime self-update
        // (see YoutubeDLReady), since a GitHub release doesn't need to be reproducible.
        create("github") {
            dimension = "distribution"
            buildConfigField("boolean", "YTDLP_SELF_UPDATE", "true")
        }
        // F-Droid forbids any code that downloads and runs another binary at runtime, so
        // this flavor never calls updateYoutubeDL and ships only the yt-dlp binary bundled
        // with youtubedl-android. Same applicationId as github: this is a build-time
        // switch, not a different app.
        create("fdroid") {
            dimension = "distribution"
            buildConfigField("boolean", "YTDLP_SELF_UPDATE", "false")
        }
    }

    signingConfigs {
        if (hasSigningConfig) {
            create("release") {
                storeFile = signingKeystoreFile!!
                storePassword = signingKeystorePassword
                keyAlias = signingKeyAlias
                keyPassword = signingKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            enableUnitTestCoverage = true
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        release {
            if (hasSigningConfig) {
                signingConfig = signingConfigs.getByName("release")
            }
            // R8 shrinks and optimises the release build. The keep rules it needs
            // (youtubedl-android's Jackson mapper, Jackson, commons-compress) are in
            // proguard-rules.pro, each with a comment on why it's there.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    kotlin {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.extensions.configure(JacocoTaskExtension::class.java) {
                isIncludeNoLocationClasses = true
                excludes = listOf("jdk.internal.*")
            }
        }
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
        jniLibs {
            // youtubedl-android needs native libs extracted to filesystem (not compressed in APK)
            useLegacyPackaging = true
            // The prebuilt .so files (Python, ffmpeg, aria2c) come from youtubedl-android
            // and ffmpeg-kit. AGP strips them with whatever NDK it finds, so a rebuild
            // without that exact NDK produces different bytes -- F-Droid's builder has
            // none unless its recipe pins one. Keeping the symbols leaves the libraries
            // exactly as their AARs ship them, which rebuilds identically anywhere.
            keepDebugSymbols += "**/*.so"
        }
    }
}

jacoco {
    toolVersion = "0.8.11"
}

// The "distribution" flavor dimension means unit tests, and therefore coverage data,
// are per flavor now (testGithubDebugUnitTest / testFdroidDebugUnitTest) — there is no
// bare debug variant to report on. jacocoTestReportGithub/Fdroid report each flavor;
// jacocoTestReport aggregates both for anyone still typing the pre-flavor task name.
//
// Paths below match this project's AGP 9 / Kotlin 2.4 layout, verified against an
// actual build: javac output moved under intermediates/javac/<variant>/compile...,
// and Kotlin's own K2 compiler ("built_in_kotlinc") replaced the older Kotlin Gradle
// plugin's tmp/kotlin-classes/<variant> path this task used to point at.
val jacocoExcludes = listOf(
    "**/R.class",
    "**/R$*.class",
    "**/BuildConfig.*",
    "**/Manifest*.*",
    "**/*Test*.*",
    "**/*_Impl.class",
    "**/Hilt_*.*",
    "**/*Hilt*.*",
    "**/dagger/hilt/**",
    "**/com/google/dagger/**",
    "**/androidx/hilt/**",
    "**/*\$Companion.class"
)

fun registerJacocoReport(flavor: String) {
    val capitalizedFlavor = flavor.replaceFirstChar { it.uppercase() }
    val variant = "${flavor}Debug"
    val capitalizedVariant = "${capitalizedFlavor}Debug"

    tasks.register<JacocoReport>("jacocoTestReport$capitalizedFlavor") {
        dependsOn("test${capitalizedVariant}UnitTest")

        reports {
            xml.required.set(true)
            html.required.set(true)
        }

        val javaClasses = fileTree("$buildDir/intermediates/javac/$variant/compile${capitalizedVariant}JavaWithJavac/classes") {
            exclude(jacocoExcludes)
        }
        val kotlinClasses = fileTree("$buildDir/intermediates/built_in_kotlinc/$variant/compile${capitalizedVariant}Kotlin/classes") {
            exclude(jacocoExcludes)
        }

        classDirectories.setFrom(files(javaClasses, kotlinClasses))
        sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
        executionData.setFrom(
            fileTree(buildDir) {
                include(
                    "jacoco/test${capitalizedVariant}UnitTest.exec",
                    "outputs/unit_test_code_coverage/${variant}UnitTest/test${capitalizedVariant}UnitTest.exec"
                )
            }
        )
    }
}

registerJacocoReport("github")
registerJacocoReport("fdroid")

tasks.register("jacocoTestReport") {
    group = "verification"
    description = "Coverage report for both distribution flavors."
    dependsOn("jacocoTestReportGithub", "jacocoTestReportFdroid")
}

dependencies {
    // Core Android
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    
    // Compose
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    
    // Navigation
    implementation(libs.navigation.compose)
    
    // ViewModel
    implementation(libs.lifecycle.viewmodel.compose)
    
    // Dependency Injection
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    
    // YouTube Download
    implementation(libs.youtubedl.android)
    implementation(libs.youtubedl.android.ffmpeg)
    implementation(libs.youtubedl.android.aria2c)
    
    // Video Processing
    implementation(libs.ffmpeg.kit)
    
    // Networking
    implementation(libs.okhttp)
    
    // Serialization
    implementation(libs.kotlinx.serialization.json)
    
    // Data Storage
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)
    
    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Media playback
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    // Frame-accurate segment trimming (see VideoTrimmer)
    implementation(libs.media3.transformer)
    
    // Testing
    testImplementation(libs.junit)
    testImplementation(libs.okhttp.mockwebserver)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
