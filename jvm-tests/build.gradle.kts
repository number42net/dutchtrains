/**
 * JVM-only test module.
 *
 * The main Android Gradle build cannot run unit tests on ARM64 Linux because AAPT2 is
 * distributed as x86_64-only binaries. This module compiles the pure-JVM-compatible
 * production sources and the unit test sources together without the Android resource
 * pipeline, bypassing AAPT2 entirely.
 *
 * Run with:   ./gradlew :jvm-tests:test
 */
plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

// Android stubs give us android.util.Log etc at compile time; the real implementation
// is never called in unit tests (Log methods return 0/null by default on JVM).
val androidSdkPath = sequenceOf(
    System.getenv("ANDROID_HOME"),
    System.getenv("ANDROID_SDK_ROOT"),
    "${System.getProperty("user.home")}/Library/Android/sdk",
    "/opt/android-sdk",
).firstOrNull { !it.isNullOrBlank() }

val androidJar: FileCollection = files(
    androidSdkPath?.let { "$it/platforms/android-35/android.jar" }
        ?: "/opt/android-sdk/platforms/android-35/android.jar"
)

dependencies {
    compileOnly(androidJar)
    testRuntimeOnly(androidJar)

    // javax.inject stubs for @Inject / @Singleton annotations used in production sources
    compileOnly("javax.inject:javax.inject:1")

    // Network layer (required to compile TripRepositoryImpl + API interfaces)
    implementation(libs.retrofit)
    implementation(libs.okhttp)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coroutines.android)

    // Test dependencies
    testImplementation(libs.junit)
    testImplementation(libs.mockk)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockwebserver)
}

kotlin {
    jvmToolchain(17)
}

// ── Source sets ────────────────────────────────────────────────────────────────
// Only the production files that the tests actually exercise are included.
// Deliberately excludes files with Android Service / Hilt / Notification deps.
val appSrc = "${rootProject.projectDir}/app/src/main/kotlin/net/number42/dutchtrains"

sourceSets {
    main {
        kotlin {
            srcDir("$appSrc/service")          // TrainChangeModel.kt, FollowChangeDetector.kt
            srcDir("$appSrc/data/api")          // NsTripsService, VirtualTrainService + dto/
            srcDir("$appSrc/data/repository")   // TripRepository, TripRepositoryImpl
            srcDir("$appSrc/domain/model")      // Leg, Trip, Station, TrainMaterial
        }
    }
    test {
        kotlin {
            srcDir("${rootProject.projectDir}/app/src/test/kotlin")
        }
    }
}

// Exclude files that drag in Android Service / Hilt / Notification / R class deps.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    val excluded = setOf(
        "TrainFollowService.kt",
        "NotificationHelper.kt",
        "NsApiKeyInterceptor.kt",
        "StationRepositoryImpl.kt",
    )
    exclude { it.file.name in excluded }
}

tasks.test {
    useJUnit()
    testLogging {
        events("passed", "failed", "skipped")
    }
}
