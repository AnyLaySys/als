import java.util.zip.*

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

abstract class ExtractTermuxClasses : DefaultTask() {
    @get:InputFiles
    abstract val sourceAar: ConfigurableFileCollection

    @get:OutputFile
    abstract val outputJar: RegularFileProperty

    @TaskAction
    fun extract() {
        ZipFile(sourceAar.singleFile).use { archive ->
            val entry = checkNotNull(archive.getEntry("classes.jar"))
            val output = outputJar.get().asFile
            output.parentFile.mkdirs()
            archive.getInputStream(entry).use { input ->
                output.outputStream().use(input::copyTo)
            }
        }
    }
}

val terminalEmulatorAar = configurations.create("terminalEmulatorAar") {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val terminalEmulatorClassesDirectory = layout.buildDirectory.dir("generated/termux")
val terminalEmulatorClasses =
    terminalEmulatorClassesDirectory.map { it.file("terminal-emulator.jar") }

val extractTerminalEmulatorClasses =
    tasks.register<ExtractTermuxClasses>("extractTerminalEmulatorClasses") {
        sourceAar.from(terminalEmulatorAar)
        outputJar.set(terminalEmulatorClasses)
    }

android {
    namespace = "sui.k.als"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }
    buildToolsVersion = "37.0.0"
    ndkVersion = "30.0.15729638"
    defaultConfig {
        applicationId = "sui.k.als"
        minSdk = 35
        targetSdk = 37
        versionCode = 21
        versionName = "26.8.27"
        ndk {
            abiFilters.add("arm64-v8a")
        }
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            setProguardFiles(
                listOf(
                    getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro"
                )
            )
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
        aidl = true
        compose = true
        buildConfig = false
        resValues = false
    }
    bundle {
        language { enableSplit = true }
        density { enableSplit = true }
        abi { enableSplit = true }
    }
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
        exclude(group = "com.github.termux.termux-app", module = "terminal-emulator")
    }
    terminalEmulatorAar(libs.termux.emulator)
    implementation(files(terminalEmulatorClasses) {
        builtBy(extractTerminalEmulatorClasses)
    })
}