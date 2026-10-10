import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.ksp)
  id("com.google.dagger.hilt.android")
  alias(libs.plugins.errorprone)
}

android {
  namespace = "com.example.googlehomeapisampleapp"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.example.googlehomeapisampleapp"
    minSdk = 29
    targetSdk = 36
    versionCode = 64
    versionName = "1.10.20-multi-light-widget"

    // Store the Playground OAuth Client ID in local.properties and access it via project properties.
    // If local.properties doesn't exist in your app root folder, just create it
    // e.g. add these lines to your local.properties
    // PLAYGROUND_OAUTH_CLIENT_ID=your-oauth-client-id
    val localProperties = Properties()
    val localPropertiesFile = rootProject.file("local.properties")
    if (localPropertiesFile.exists()) {
      localPropertiesFile.inputStream().use { localProperties.load(it) }
    }
    // Note: PLAYGROUND_OAUTH_CLIENT_ID is only required for the Google Home Playground simulation
    // to retrieve the auth code via a web redirect (Custom Tabs).
    // In a production partner app, you would likely retrieve this code silently from your own
    // backend, which would not require this Client ID to be stored in the mobile app.
    val playgroundOauthClientIdRaw =
      localProperties.getProperty("PLAYGROUND_OAUTH_CLIENT_ID")
        ?: project.findProperty("PLAYGROUND_OAUTH_CLIENT_ID") as? String
        ?: "abc" // Default client ID for GHP Playground environment
    val playgroundOauthClientId = playgroundOauthClientIdRaw.replace("\"", "")
    buildConfigField("String", "PLAYGROUND_OAUTH_CLIENT_ID", "\"$playgroundOauthClientId\"")
  }
  lint { disable += "NullSafeMutableLiveData" }

  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.getByName("debug")
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    }
    create("compact") {
      initWith(getByName("release"))
      isMinifyEnabled = true
      isShrinkResources = true
      signingConfig = signingConfigs.getByName("debug")
      matchingFallbacks += listOf("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
  buildFeatures {
    compose = true
    buildConfig = true
  }

}

dependencies {
  // Library dependencies:
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.ui)
  implementation(libs.androidx.ui.graphics)
  implementation(libs.androidx.ui.tooling.preview)
  implementation(libs.androidx.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.browser)
  implementation(libs.androidx.glance.appwidget)
  implementation(libs.androidx.glance.material3)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.play.services)
  implementation(libs.androidx.work.runtime.ktx)
  // Home API SDK dependency:
  implementation(libs.play.services.home)
  implementation(libs.play.services.home.types)
  implementation(libs.play.services.auth)
  implementation(libs.dagger.hilt.android)
  ksp(libs.hilt.android.compiler)
  ksp("org.jetbrains.kotlin:kotlin-metadata-jvm:2.4.0")
  implementation(libs.androidx.hilt.navigation.compose)
  ksp(libs.androidx.hilt.compiler)
  implementation(libs.errorprone.annotations)
  testImplementation("junit:junit:4.13.2")

}
