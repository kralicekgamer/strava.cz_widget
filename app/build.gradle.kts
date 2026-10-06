plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Krátký hash commitu, ze kterého se sestavuje; mimo git repozitář „neznámý“.
val gitCommit: String = providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().ifEmpty { "neznámý" } }.getOrElse("neznámý")

android {
    namespace = "cz.kralicekgamer.stravawidget"
    compileSdk = 37

    defaultConfig {
        applicationId = "cz.kralicekgamer.stravawidget"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        versionName = "1.3"

        buildConfigField("long", "BUILD_TIME", "${System.currentTimeMillis()}L")
        buildConfigField("String", "GIT_COMMIT", "\"$gitCommit\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(libs.junit)
    // Android's org.json is only a stub in JVM unit tests.
    testImplementation(libs.json)
}
