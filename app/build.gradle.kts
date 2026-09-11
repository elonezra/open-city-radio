plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "com.opencity.radio"
    compileSdk = 35
    defaultConfig { applicationId = "com.opencity.radio"; minSdk = 29; targetSdk = 35; versionCode = 1; versionName = "0.2.0" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isReturnDefaultValues = true }
}
dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.documentfile:documentfile:1.0.1")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-session:1.5.1")
    implementation("io.coil-kt:coil:2.7.0")
    implementation("io.coil-kt:coil-svg:2.7.0")
    implementation("io.coil-kt:coil-gif:2.7.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
