plugins { alias(libs.plugins.android.library) }

android {
    namespace = "com.febricahyaa.fluxlab.model"
    compileSdk = 36
    defaultConfig { minSdk = 28 }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin { compilerOptions { allWarningsAsErrors.set(true) } }

dependencies {
    implementation(libs.kotlinx.coroutines.android)
}
