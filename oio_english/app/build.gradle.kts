import java.io.File

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.oio.english"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.oio.english"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "2.2.6"

    }

    buildFeatures {
        compose = true
    }

    experimentalProperties["android.experimental.disableJdkImageGeneration"] = true

    // APK 命名
    buildTypes {
        debug {
            matchingFallbacks.addAll(listOf("debug"))
        }
        release {
            isMinifyEnabled = false
        }
    }

    // Release APK 重命名 (输出目录为 app/release/)
    tasks.matching { it.name == "packageRelease" }.configureEach {
        doLast {
            val apkDir = projectDir.resolve("release")
            val newName = "OIO_${android.defaultConfig.versionName}.apk"
            apkDir.listFiles()?.filter { it.name.endsWith(".apk") }?.forEach { apk ->
                apk.renameTo(File(apkDir, newName))
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE.md",
            "META-INF/LICENSE-notice.md",
            "META-INF/NOTICE.md",
            "META-INF/INDEX.LIST"
        )
    }
}

dependencies {
    // Compose BOM
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    debugImplementation("androidx.compose.ui:ui-tooling")

    // Room
    val roomVersion = "2.6.1"
    implementation("androidx.room:room-runtime:$roomVersion")
    implementation("androidx.room:room-ktx:$roomVersion")
    ksp("androidx.room:room-compiler:$roomVersion")

    // Apache POI — xlsx 解析
    implementation("org.apache.poi:poi:5.3.0")
    implementation("org.apache.poi:poi-ooxml:5.3.0")

    // Core
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")

    // OkHttp
    implementation("com.squareup.okhttp3:okhttp:4.12.0")



    // Test
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.11.1")
    testImplementation("androidx.test:core:1.5.0")
}
