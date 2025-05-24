plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.xjyzs.aiapi"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.xjyzs.aiapi"
        minSdk = 24
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        androidResources. localeFilters+= listOf("zh")
    }
    signingConfigs {
        val hasSigningInfo = System.getenv("KEY_STORE_PASSWORD") != null &&
            System.getenv("KEY_ALIAS") != null &&
            System.getenv("KEY_PASSWORD") != null &&
            file("${project.rootDir}/keystore.jks").exists()
        if (hasSigningInfo) {
            create("release") {
                storeFile = file("${project.rootDir}/keystore.jks")
                storePassword = System.getenv("KEY_STORE_PASSWORD") ?: ""
                keyAlias = System.getenv("KEY_ALIAS") ?: ""
                keyPassword = System.getenv("KEY_PASSWORD") ?: ""
                enableV1Signing=false
            }
        }
    }

    flavorDimensions += "abi"
    productFlavors {
        val releaseSigningConfig = if (signingConfigs.findByName("release") != null) {
            signingConfigs.getByName("release")
        } else {
            signingConfigs.getByName("debug")
        }
        create("x86") {
            dimension = "abi"
            ndk { abiFilters.add("x86") }
            signingConfig = releaseSigningConfig
        }
        create("x86_64") {
            dimension = "abi"
            ndk { abiFilters.add("x86_64") }
            signingConfig = releaseSigningConfig
        }
        create("arm") {
            dimension = "abi"
            ndk { abiFilters.add("armeabi-v7a") }
            signingConfig = releaseSigningConfig
        }
        create("arm64") {
            dimension = "abi"
            ndk { abiFilters.add("arm64-v8a") }
            signingConfig = releaseSigningConfig
        }
        create("universal") {
            dimension = "abi"
            signingConfig = releaseSigningConfig
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources=true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            packaging {
                resources {
                    excludes += setOf(
                        "DebugProbesKt.bin",
                        "kotlin-tooling-metadata.json",
                        "okhttp3/**",
                        "META-INF/**",
                        "kotlin/**"
                    )
                }
            }
            androidResources {
                noCompress += setOf("so", "arsc")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    kotlinOptions {
        jvmTarget = "21"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    //implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    //implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    implementation(libs.okhttp)
    implementation(libs.gson)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.material.icons.extended)
    //implementation(libs.androidx.material)
    implementation(libs.androidx.lifecycle.process)
}
