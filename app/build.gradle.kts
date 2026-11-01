import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.ksp)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val appId: String = providers.gradleProperty("app.applicationId").get()
val appLabel: String = providers.gradleProperty("app.label").get()

// Release signing comes from CI (README, "Releases"). Without the variables no release signing
// config exists, and the CI release job refuses to publish an unsigned final release.
val releaseKeystore: String? = providers.environmentVariable("BRUME_KEYSTORE_PATH").orNull

android {
    namespace = "app.beansontoast.brume"
    compileSdk = 35

    defaultConfig {
        applicationId = appId
        minSdk = 29
        targetSdk = 35
        versionCode = providers.environmentVariable("BRUME_VERSION_CODE").orElse("1").get().toInt()
        versionName = providers.environmentVariable("BRUME_VERSION_NAME").orElse("0.0.0-dev").get()
        resValue("string", "app_name", appLabel)
    }

    signingConfigs {
        // Public by design (password "android"): every CI debug build carries the same signature,
        // so a newer debug APK installs over the last. Never a release key.
        getByName("debug") {
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").get()
            }
        }
    }

    // Two builds of the same app (docs/updates.md). `github` carries the in-app updater, and with it
    // INTERNET and REQUEST_INSTALL_PACKAGES, used only to check and download Brume's own releases
    // from GitHub. `fdroid` is Brume as it always was, with no network at all. checkNoNetwork* and
    // GuardsTest enforce both.
    flavorDimensions += "distribution"
    productFlavors {
        create("github") {
            dimension = "distribution"
        }
        create("fdroid") {
            dimension = "distribution"
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            signingConfig = signingConfigs.getByName("debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("release")
            vcsInfo.include = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

    lint {
        // Lifecycle 2.8's detector crashes under Kotlin 2 UAST (pastille's note); Brume uses no LiveData.
        disable += "NullSafeMutableLiveData"
    }

    // F-Droid and reproducible builds reject the Google-encrypted dependency metadata block.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            // Design renders (src/test/.../renders) run only with BRUME_RENDERS=<dir>, one JVM per class:
            // Robolectric's native canvas can abort a JVM shared by several renders (pastille's note).
            if (System.getenv("BRUME_RENDERS") != null) {
                it.forkEvery = 1
                it.maxHeapSize = "2g"
                it.outputs.upToDateWhen { false }
            }
            it.testLogging {
                events("failed")
                exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            }
        }
    }

    sourceSets {
        // MigrationTestHelper reads the exported Room schemas as assets (Robolectric sees the debug ones; pastille does the same).
        getByName("debug").assets.srcDir("$projectDir/schemas")
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

// Network by construction (pastille's check, stricter): every variant's merged manifest is read
// before it assembles or lints. An `fdroid` variant fails on any network permission; a `github`
// variant may ask only for the updater's two (INTERNET, REQUEST_INSTALL_PACKAGES), never another.
androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val check = tasks.register<NoNetworkCheck>("checkNoNetwork$name") {
            manifest.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST))
            updater.set(variant.flavorName == "github")
        }
        afterEvaluate {
            tasks.named("assemble$name") { dependsOn(check) }
            tasks.findByName("lint$name")?.dependsOn(check)
        }
    }
}

abstract class NoNetworkCheck : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val manifest: RegularFileProperty

    /** The github flavour: the updater's two permissions are allowed, nothing else. */
    @get:Input
    abstract val updater: Property<Boolean>

    @TaskAction
    fun check() {
        val text = manifest.get().asFile.readText()
        val asked = Regex("""<uses-permission[^>]*android:name="([^"]+)"""").findAll(text).map { it.groupValues[1] }.toSet()
        val allowed = if (updater.get()) UPDATER_PERMISSIONS else emptySet()
        val forbidden = asked.filter { it in NETWORK_PERMISSIONS && it !in allowed }
        check(forbidden.isEmpty()) { "This Brume build must not ask for $forbidden (${manifest.get().asFile})" }
    }

    private companion object {
        val UPDATER_PERMISSIONS = setOf("android.permission.INTERNET", "android.permission.REQUEST_INSTALL_PACKAGES")
        val NETWORK_PERMISSIONS = setOf(
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.ACCESS_WIFI_STATE",
            "android.permission.CHANGE_NETWORK_STATE",
            "android.permission.CHANGE_WIFI_STATE",
            "android.permission.REQUEST_INSTALL_PACKAGES",
        )
    }
}

dependencies {
    implementation(project(":core"))
    implementation(project(":design"))
    implementation(libs.activity.compose)
    implementation(libs.core.ktx)
    implementation(libs.core.splashscreen)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.savedstate)
    implementation(libs.coroutines.android)
    // The updater reads GitHub's release list; only the github build carries it.
    "githubImplementation"(libs.serialization.json)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    testImplementation(libs.junit)
    testImplementation(libs.room.testing)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.coroutines.test)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)
}
