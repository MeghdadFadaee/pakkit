plugins { id("com.android.application") }
android {
    namespace = "meghdad.pakkit.fixture"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig { applicationId = "meghdad.pakkit.fixture"; minSdk = 31; targetSdk = 36; versionCode = 1; versionName = "1.0-test" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
