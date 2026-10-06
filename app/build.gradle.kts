import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

// --------------------------------------------------------------------
// Reproducible release keystore committed with the repo. Production/Play
// builds should override it with their own key via STORE_PASSWORD +
// my-upload-key.jks (see RELEASE.md).
// --------------------------------------------------------------------
val fallbackKeystoreFile = file("${rootDir}/chort-release.jks")
val fallbackKeystorePassword = "chortrelease"
val fallbackKeyAlias = "chort"

/**
 * Short hash of the git revision this build is compiled from, stamped into
 * BuildConfig.GIT_COMMIT so an APK can always be traced back to its source
 * commit. Falls back to "unknown" outside a git checkout.
 */
fun resolveGitCommit(): String = try {
  providers.exec {
    commandLine("git", "rev-parse", "--short", "HEAD")
    workingDir = rootDir
  }.standardOutput.asText.get().trim().ifBlank { "unknown" }
} catch (e: Exception) {
  "unknown"
}

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.tokpulse.social"
    minSdk = 24
    targetSdk = 36

    // ------------------------------------------------------------------
    // VERSION BUMP (was versionCode 20200 / versionName "2.2.0").
    // The previous APK shipped with the exact same versionCode/versionName
    // as the source, which made stale builds indistinguishable from new
    // ones on-device. Bump BOTH on every release.
    // ------------------------------------------------------------------
    versionCode = 20400
    versionName = "2.4.0"

    // Commit stamp compiled into BuildConfig.GIT_COMMIT so any APK can be
    // traced back to the exact git revision it was built from.
    buildConfigField("String", "GIT_COMMIT", "\"${resolveGitCommit()}\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    // ------------------------------------------------------------------
    // DETERMINISTIC RELEASE SIGNING
    // Previously this silently fell back to a debug keystore, so "release"
    // APKs were debug-signed (CN=Android Debug) while the README advertised a
    // production certificate. Resolution order is now explicit and logged:
    //   1. KEYSTORE_PATH env var                  -> explicit keystore file
    //   2. STORE_PASSWORD + my-upload-key.jks     -> official production key
    //   3. chort-release.jks (committed, documented) -> reproducible builds
    // ------------------------------------------------------------------
    create("release") {
      val explicitKeystore = System.getenv("KEYSTORE_PATH")
        ?.takeIf { it.isNotBlank() }
        ?.let { file(it) }
      val officialKeystore = file("${rootDir}/my-upload-key.jks")
      val envStorePass = System.getenv("STORE_PASSWORD")

      val chosen = when {
        explicitKeystore != null && explicitKeystore.exists() ->
          explicitKeystore to (envStorePass ?: fallbackKeystorePassword)
        envStorePass != null && officialKeystore.exists() ->
          officialKeystore to envStorePass
        fallbackKeystoreFile.exists() ->
          fallbackKeystoreFile to fallbackKeystorePassword
        else -> error(
          "No release keystore available. Set KEYSTORE_PATH or STORE_PASSWORD, " +
            "or restore ${fallbackKeystoreFile.name}."
        )
      }

      storeFile = chosen.first
      storePassword = chosen.second
      keyAlias = System.getenv("KEY_ALIAS")
        ?: if (chosen.first == fallbackKeystoreFile) fallbackKeyAlias else "upload"
      keyPassword = System.getenv("KEY_PASSWORD") ?: chosen.second
      enableV1Signing = true
      enableV2Signing = true
      enableV3Signing = true

      logger.lifecycle("[Chort] Release signing keystore: ${chosen.first.name}")
    }
    create("debugConfig") {
      // debug.keystore is git-ignored and absent on fresh clones, which used to
      // break `assembleDebug`. Re-use the committed keystore when it's missing.
      val useFallback = fallbackKeystoreFile.exists()
      storeFile = if (useFallback) fallbackKeystoreFile else file("${rootDir}/debug.keystore")
      storePassword = if (useFallback) fallbackKeystorePassword else "android"
      keyAlias = if (useFallback) fallbackKeyAlias else "androiddebugkey"
      keyPassword = if (useFallback) fallbackKeystorePassword else "android"
      enableV1Signing = true
      enableV2Signing = true
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.getByName("release")
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
  lint {
    checkReleaseBuilds = false
    abortOnError = false
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // implementation(libs.androidx.camera.camera2)
  // implementation(libs.androidx.camera.core)
  // implementation(libs.androidx.camera.lifecycle)
  // implementation(libs.androidx.camera.view)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.coil.svg)
  implementation(libs.coil.video)
  implementation(libs.converter.moshi)
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.common)
  implementation(libs.firebase.ai)
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.storage)
  implementation(libs.androidx.credentials)
  implementation(libs.androidx.credentials.play.services)
  implementation(libs.googleid)
  implementation(libs.facebook.android.sdk)
  implementation(libs.firebase.appcheck.recaptcha)
  implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
