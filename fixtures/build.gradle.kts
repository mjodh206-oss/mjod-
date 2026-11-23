plugins { id("com.android.application") }
android {
    namespace = "dev.lasq.fixture"
    compileSdk = 35
    defaultConfig { applicationId = "dev.lasq.fixture"; minSdk = 26; targetSdk = 35 }
    flavorDimensions += "editor"
    productFlavors {
        create("source") { dimension = "editor"; applicationIdSuffix = ".source" }
        create("destination") { dimension = "editor"; applicationIdSuffix = ".destination" }
    }
}
