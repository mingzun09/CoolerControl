import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

// 正式签名配置放在仓库外，见 keystore.properties（已 .gitignore）
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        FileInputStream(keystorePropsFile).use { load(it) }
    }
}

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.cooler.control"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.cooler.control"
        minSdk = 29
        targetSdk = 36
        versionCode = 19
        versionName = "0.19.0"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    // 仅编译期可见，运行时的 Xposed 类由 LSPosed 提供
    compileOnly(project(":xposed-stub"))
    implementation("org.jetbrains.compose.runtime:runtime:1.10.3")
    implementation("org.jetbrains.compose.foundation:foundation:1.10.3")
    implementation("org.jetbrains.compose.ui:ui:1.10.3")

    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("androidx.core:core-ktx:1.17.0")

    implementation("top.yukonga.miuix.kmp:miuix-ui:0.9.0")
    implementation("top.yukonga.miuix.kmp:miuix-preference:0.9.0")

    testImplementation("junit:junit:4.13.2")
}

tasks.withType<Test>().configureEach {
    val tmpDir = layout.buildDirectory.dir("test-tmp").get().asFile
    tmpDir.mkdirs()
    workingDir = tmpDir
    systemProperty("java.io.tmpdir", tmpDir.absolutePath)
}