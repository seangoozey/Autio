plugins { id("com.android.application") }
dependencies {
    implementation("androidx.media:media:1.8.0")
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-projected:1.7.0")
}
android {
    namespace = "com.autio.notes"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.autio.notes"
        minSdk = 29
        targetSdk = 37
        versionCode = 9
        versionName = "0.9-host-session"
        testInstrumentationRunner = "com.autio.notes.RecorderSmokeTest"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
