import java.io.File
import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.febricahyaa.fluxlab"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.febricahyaa.fluxlab"
        minSdk = 28
        targetSdk = 36
        versionCode = providers.gradleProperty("fluxlabVersionCode").orNull?.toIntOrNull() ?: 1
        versionName = providers.gradleProperty("fluxlabVersionName").orNull ?: "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        create("release") {
            val keystorePath = providers.environmentVariable("FLUXLAB_RELEASE_KEYSTORE_PATH").orNull
            val storePassword = providers.environmentVariable("FLUXLAB_RELEASE_STORE_PASSWORD").orNull
            val keyAlias = providers.environmentVariable("FLUXLAB_RELEASE_KEY_ALIAS").orNull
            val keyPassword = providers.environmentVariable("FLUXLAB_RELEASE_KEY_PASSWORD").orNull
            if (!keystorePath.isNullOrBlank()) storeFile = file(keystorePath)
            this.storePassword = storePassword
            this.keyAlias = keyAlias
            this.keyPassword = keyPassword
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isDebuggable = false
            isJniDebuggable = false
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    buildFeatures { compose = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.keepDebugSymbols += "**/*.so"
    }
    lint {
        abortOnError = true
        checkReleaseBuilds = true
        htmlReport = true
        xmlReport = true
    }
}

val validateReleaseSigningInputs = tasks.register("validateReleaseSigningInputs") {
    group = "verification"
    description = "Validates production release signing inputs without exposing secret values."
    doLast {
        val required = listOf(
            "FLUXLAB_RELEASE_KEYSTORE_PATH",
            "FLUXLAB_RELEASE_STORE_PASSWORD",
            "FLUXLAB_RELEASE_KEY_ALIAS",
            "FLUXLAB_RELEASE_KEY_PASSWORD",
        )
        val environment = System.getenv()
        val missing = required.filter { environment[it].isNullOrBlank() }
        if (missing.isNotEmpty()) {
            throw GradleException(
                "Release signing inputs are missing: ${missing.joinToString()}. " +
                    "Configure the production signing environment; secret values are not printed.",
            )
        }
        val keystorePath = environment.getValue("FLUXLAB_RELEASE_KEYSTORE_PATH")
        if (!File(keystorePath).isFile) {
            throw GradleException(
                "Release signing keystore is unavailable or is not a regular file. " +
                    "Secret values are not printed.",
            )
        }
    }
}

tasks.configureEach {
    if (name.contains("Release") && name != "validateReleaseSigningInputs") {
        dependsOn(validateReleaseSigningInputs)
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors.set(true)
        freeCompilerArgs.add("-Xannotation-default-target=param-property")
    }
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:integration"))
    implementation(project(":core:data"))
    implementation(project(":core:benchmark"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore)
    implementation(libs.androidx.metrics)
    implementation(libs.androidx.room.runtime)
    implementation(libs.kotlinx.coroutines.android)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.test.junit)
    androidTestImplementation(libs.androidx.test.espresso)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
