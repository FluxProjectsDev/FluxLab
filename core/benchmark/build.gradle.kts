plugins { alias(libs.plugins.android.library) }

android {
    namespace = "com.febricahyaa.fluxlab.benchmark"
    compileSdk = 36
    defaultConfig {
        minSdk = 28
        externalNativeBuild.cmake.arguments += listOf("-DANDROID_STL=c++_static")
        if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) {
            ndk { abiFilters += "arm64-v8a" }
            val launcher = rootProject.file("tools/android-clang-launcher.sh").absolutePath
            externalNativeBuild.cmake.arguments += listOf(
                "-DANDROID_CCACHE=$launcher",
                "-DCMAKE_C_LINKER_LAUNCHER=$launcher",
                "-DCMAKE_CXX_LINKER_LAUNCHER=$launcher",
                "-DCMAKE_TRY_COMPILE_TARGET_TYPE=STATIC_LIBRARY",
                "-DCMAKE_AR=/usr/bin/llvm-ar",
                "-DCMAKE_RANLIB=/usr/bin/llvm-ranlib",
            )
        }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt") } }
    packaging { jniLibs.keepDebugSymbols += "**/*.so" }
    ndkVersion = "28.2.13676358"
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
}

kotlin { compilerOptions { allWarningsAsErrors.set(true) } }

dependencies {
    implementation(project(":core:model"))
    implementation(libs.kotlinx.coroutines.android)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
