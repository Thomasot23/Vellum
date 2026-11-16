plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "org.vellum.panel"
    compileSdk = 37
    defaultConfig {
        applicationId = "org.vellum.panel"
        minSdk = 30
        targetSdk = 37
        versionName = "0.1.0"
        versionCode = releaseVersionCode(versionName!!)
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    bundle { language { enableSplit = false } }
    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility =
            JavaVersion.VERSION_17
    }
    testOptions { unitTests.isIncludeAndroidResources = true }
    packaging {
        resources.excludes +=
            setOf("META-INF/DEPENDENCIES", "META-INF/LICENSE*", "META-INF/NOTICE*")
    }
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material)
    implementation(libs.activity.compose)
    implementation(libs.coroutines)
    implementation(libs.serialization)
    implementation(libs.okhttp)
    implementation(libs.bouncycastle)
    implementation(libs.coil.compose)
    implementation(libs.coil.svg)
    implementation(libs.zxing)
    androidTestImplementation(libs.android.test.junit)
    androidTestImplementation(libs.android.test.runner)
    testImplementation(libs.junit)
    testImplementation(libs.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.compose.test)
    debugImplementation(libs.compose.test.manifest)
}

/**
 * versionCode = major * 10000 + minor * 100 + patch. Android installs an update
 * only when the code increases, so the code is derived from the name and never
 * edited by hand; `scripts/sign-release.sh` refuses a code that was released before.
 */
fun releaseVersionCode(name: String): Int {
    val parts =
        Regex("""(\d+)\.(\d{1,2})\.(\d{1,2})""").matchEntire(name)?.destructured
            ?: error("versionName must be major.minor.patch, got $name")
    val (major, minor, patch) = parts.toList().map(String::toInt)
    return major * 10_000 + minor * 100 + patch
}
