plugins { id("com.android.library") }
android {
    namespace = "meghdad.pakkit.core"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig { minSdk = 31 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
dependencies {
    api(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
