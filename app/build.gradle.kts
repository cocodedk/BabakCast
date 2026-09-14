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
    compileSdk = 36

    defaultConfig {
        applicationId = "com.cocode.babakcast"
        minSdk = 24
        targetSdk = 36
        // CI sets VERSION_CODE and VERSION_NAME; local builds use defaults
        versionCode = System.getenv("VERSION_CODE")?.toIntOrNull() ?: 1
        versionName = System.getenv("VERSION_NAME") ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
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
            isMinifyEnabled = false
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
        // youtubedl-android needs native libs extracted to filesystem (not compressed in APK)
        jniLibs {
            useLegacyPackaging = true
        }
    }
}

jacoco {
    toolVersion = "0.8.11"
}

tasks.register<JacocoReport>("jacocoTestReport") {
    dependsOn("testDebugUnitTest")

    reports {
        xml.required.set(true)
        html.required.set(true)
    }

    val excludes = listOf(
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

    val javaClasses = fileTree("$buildDir/intermediates/javac/debug/classes") {
        exclude(excludes)
    }
    val kotlinClasses = fileTree("$buildDir/tmp/kotlin-classes/debug") {
        exclude(excludes)
    }

    classDirectories.setFrom(files(javaClasses, kotlinClasses))
    sourceDirectories.setFrom(files("src/main/java", "src/main/kotlin"))
    executionData.setFrom(
        fileTree(buildDir) {
            include(
                "jacoco/testDebugUnitTest.exec",
                "outputs/unit_test_code_coverage/debugUnitTest/testDebugUnitTest.exec"
            )
        }
    )
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
