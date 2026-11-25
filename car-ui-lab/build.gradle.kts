plugins { id("com.android.application") }
dependencies {
    implementation("androidx.car.app:app:1.7.0")
    implementation("androidx.car.app:app-projected:1.7.0")
    implementation("androidx.media:media:1.8.0")
}
android {
    namespace = "com.autio.notes.uilab"
    compileSdk = 37
    defaultConfig {
        applicationId = "com.autio.notes.uilab"
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.1-ui-lab"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
