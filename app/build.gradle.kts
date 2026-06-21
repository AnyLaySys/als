plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "sui.k.als"
    compileSdk= 37
    defaultConfig {
        applicationId = "sui.k.als"
        minSdk = 33
        targetSdk = 37
        versionCode = 13
        versionName = "26.6.21"
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            setProguardFiles(listOf(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            ))
            ndk {
                debugSymbolLevel = "none"
            }
            packaging {
                jniLibs {
                    useLegacyPackaging = false
                }
                resources {
                    excludes += "/META-INF/{AL2.0,LGPL2.1}"
                    excludes += "/META-INF/*.kotlin_module"
                    excludes += "**/DebugProbesKt.bin"
                    excludes += "/META-INF/androidx.*"
                    excludes += "/META-INF/com.android.*"
                    excludes += "/META-INF/kotlin-*"
                    excludes += "/kotlin/**"
                    excludes += "/*.properties"
                    excludes += "/META-INF/*.version"
                    excludes += "/META-INF/*.txt"
                }
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_25
        targetCompatibility = JavaVersion.VERSION_25
    }
    buildFeatures {
        compose = true
        buildConfig = false
        resValues = false
        aidl = false
    }
    bundle {
        @Suppress("UnstableApiUsage")
        language { enableSplit = true }
        @Suppress("UnstableApiUsage")
        density { enableSplit = true }
        @Suppress("UnstableApiUsage")
        abi { enableSplit = true }
    }
    @Suppress("UnstableApiUsage")
    experimentalProperties["android.experimental.art-profile-r8-rewriting"] = true
    @Suppress("UnstableApiUsage")
    experimentalProperties["android.experimental.r8.fullMode"] = true
}
dependencies {
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.material)
    implementation(libs.androidx.preference)
    implementation(libs.termux.app) {
        exclude(group = "com.github.termux.termux-app", module = "termux-shared")
    }
    implementation(project(":x11"))
    implementation(libs.guava.listenablefuture)
}
