plugins { id("com.android.library") }
android {
    namespace = "meghdad.pakkit.core"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig { minSdk = 31 }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
dependencies {
    api("com.google.android.gms:play-services-wearable:19.0.0")
    api("androidx.work:work-runtime-ktx:2.10.1")
    api(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
