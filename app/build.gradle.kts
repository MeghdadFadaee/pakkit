plugins { alias(libs.plugins.android.application) }
android {
    namespace = "meghdad.pakkit"
    compileSdk { version = release(36) { minorApiLevel = 1 } }
    defaultConfig {
        applicationId = "meghdad.pakkit"
        minSdk = 31
        targetSdk = 36
        versionCode = 100001
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val releaseKey = providers.environmentVariable("PAKKIT_KEYSTORE").orNull
    val releaseStorePassword = providers.environmentVariable("PAKKIT_KEYSTORE_PASSWORD").orNull
    val releaseAlias = providers.environmentVariable("PAKKIT_KEY_ALIAS").orNull
    val releaseKeyPassword = providers.environmentVariable("PAKKIT_KEY_PASSWORD").orNull
    if (listOf(releaseKey, releaseStorePassword, releaseAlias, releaseKeyPassword).all { it != null }) {
        signingConfigs.create("production") {
            storeFile = file(requireNotNull(releaseKey)); storePassword = releaseStorePassword
            keyAlias = releaseAlias; keyPassword = releaseKeyPassword
        }
    }
    buildTypes { release { signingConfig = signingConfigs.findByName("production"); isMinifyEnabled = true; isShrinkResources = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "../proguard-rules.pro") } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
}
dependencies {
    implementation(project(":shared"))
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}

abstract class StageFixtureApk : DefaultTask() {
    @get:InputFile abstract val fixtureFile: RegularFileProperty
    @get:OutputDirectory abstract val outputDirectory: DirectoryProperty
    @TaskAction fun stage() {
        val output = outputDirectory.file("fixture.apk").get().asFile
        output.parentFile.mkdirs()
        fixtureFile.get().asFile.copyTo(output, overwrite = true)
    }
}
val stageTestFixture by tasks.registering(StageFixtureApk::class) {
    dependsOn(":qaFixture:assembleDebug")
    fixtureFile.set(project(":qaFixture").layout.buildDirectory.file("outputs/apk/debug/qaFixture-debug.apk"))
    outputDirectory.set(layout.buildDirectory.dir("generated/testAssets"))
}
androidComponents.onVariants(androidComponents.selector().withBuildType("debug")) { variant ->
    variant.androidTest?.sources?.assets?.addGeneratedSourceDirectory(stageTestFixture, StageFixtureApk::outputDirectory)
}
