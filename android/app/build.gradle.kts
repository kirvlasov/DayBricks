import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

val releaseSigningPropertiesFile = rootProject.file("../.signing/keystore.properties")
val releaseSigningProperties = Properties().apply {
    if (releaseSigningPropertiesFile.isFile) {
        releaseSigningPropertiesFile.inputStream().use { load(it) }
    }
}

val versionProperties = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val appVersionCode = versionProperties.getProperty("VERSION_CODE")?.toIntOrNull()
    ?.takeIf { it > 0 } ?: error("VERSION_CODE must be a positive integer")
val appVersionName = versionProperties.getProperty("VERSION_NAME")
    ?.takeIf { it.matches(Regex("\\d+\\.\\d+\\.\\d+(?:[-+][0-9A-Za-z.-]+)?")) }
    ?: error("VERSION_NAME must use semantic versioning")

android {
    namespace = "app.daybricks.planner"
    compileSdk = 37
    defaultConfig {
        applicationId = "app.daybricks.planner"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    signingConfigs {
        if (releaseSigningPropertiesFile.isFile) {
            create("release") {
                fun required(name: String): String = releaseSigningProperties.getProperty(name)
                    ?: error("Missing $name in ${releaseSigningPropertiesFile.path}")
                storeFile = releaseSigningPropertiesFile.parentFile.resolve(required("storeFile"))
                storePassword = required("storePassword")
                keyAlias = required("keyAlias")
                keyPassword = required("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (releaseSigningPropertiesFile.isFile) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    // Every supported locale must be installed because the app can switch languages at runtime.
    bundle { language { enableSplit = false } }
    lint { abortOnError = true; checkReleaseBuilds = true }
    testOptions { unitTests.isReturnDefaultValues = true }
    sourceSets.getByName("test").kotlin.directories += "src/testShared/java"
    sourceSets.getByName("androidTest").kotlin.directories += "src/testShared/java"
}
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencyLocking { lockAllConfigurations() }
val verificationTools by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}
tasks.register("verifyDependencies") {
    group = "verification"
    description = "Verify platform-specific build tools for Linux CI and macOS developers."
    doLast { verificationTools.resolve() }
}

dependencies {
    verificationTools("com.android.tools.build:aapt2:${libs.versions.aapt2.get()}:linux")
    verificationTools("com.android.tools.build:aapt2:${libs.versions.aapt2.get()}:osx")
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    implementation(libs.compose.material3)
    implementation(libs.adaptive)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.runtime)
    implementation(libs.lifecycle.viewmodel)
    implementation(libs.lifecycle.savedstate)
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.coroutines)
    implementation(libs.serialization.json)
    implementation(libs.okhttp)
    debugImplementation(libs.compose.tooling)
    debugImplementation(libs.compose.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(libs.mockwebserver)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.androidx.test)
    androidTestImplementation(libs.test.runner)
}
