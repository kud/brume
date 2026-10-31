import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Laid out the way Gamme's Kotlin kit will be (package app.beansontoast.design), with only what
// Brume v0.1 uses, written to Gamme's spec.md. Adopting the kit later replaces this module's contents.
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "app.beansontoast.design"
    compileSdk = 35
    defaultConfig { minSdk = 29 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }
    buildFeatures { compose = true }
    lint { disable += "NullSafeMutableLiveData" }
}

dependencies {
    api(platform(libs.compose.bom))
    api(libs.compose.ui)
    api(libs.compose.foundation)
    api(libs.compose.material3)
    api(libs.compose.material.icons.core)
    // Not used directly: they pin the graph to the versions the offline cache holds (pastille's set).
    implementation(libs.core.ktx)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.savedstate)
    implementation(libs.coroutines.android)
}
