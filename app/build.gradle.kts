import java.time.LocalDate
import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Optional release signing: create keystore.properties (storeFile, storePassword,
// keyAlias, keyPassword) locally or via CI secrets. Without it, the release APK
// is signed with the debug key so it still installs.
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "dev.trove.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.trove.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        // Version format: yyyy.mm.build
        versionName = buildVersionName()
        vectorDrawables { useSupportLibrary = true }
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            signingConfig = if (keystoreProps.isNotEmpty()) {
                signingConfigs.getByName("release")
            } else {
                signingConfigs.getByName("debug")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/rome-utils-2.1.0.jar"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

fun buildVersionName(): String {
    val now = LocalDate.now()
    val y = now.year
    val m = now.monthValue.toString().padStart(2, '0')
    // x = number of releases in this month (git commits since month start)
    val monthStart = "$y-$m-01"
    val release = runCatching {
        ProcessBuilder("git", "rev-list", "--count", "--since=$monthStart", "HEAD")
            .redirectErrorStream(true)
            .start()
            .inputStream.bufferedReader().readText().trim().toInt()
            .coerceAtLeast(1)
    }.getOrDefault(now.dayOfMonth)
    return "$y.$m.$release"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.androidx.datastore.preferences)
    implementation(libs.okhttp)
    implementation(libs.jsoup)
    implementation(libs.rome)
    implementation(libs.rome.modules)
    implementation(libs.readability4j)
    implementation(libs.androidx.work)
    implementation(libs.androidx.graphics.shapes)
    testImplementation("junit:junit:4.13.2")
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.material.color.utilities)
}

// Prints the computed version name (yyyy.mm.release) — used by CI to tag releases.
tasks.register("printVersionName") {
    doLast {
        println("VERSION_NAME=" + android.defaultConfig.versionName)
    }
}
