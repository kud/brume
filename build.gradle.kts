// The Kotlin and Android plugin jars are loaded here once; :core applies `org.jetbrains.kotlin.jvm`
// and :design `com.android.library` from the same jars, without a version.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
