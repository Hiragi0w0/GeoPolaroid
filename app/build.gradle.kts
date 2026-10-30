import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

// Maps SDK のAPIキーはソースに直書きせず local.properties の MAPS_API_KEY から読み込む。
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY").orEmpty()

// release 署名の鍵ファイルとパスワードも local.properties から読み込む（リポジトリには含めない）。
// 4項目すべて未設定なら署名なしの release ビルドになる。一部だけの設定は誤りとして止める。
val releaseSigningKeys = listOf(
    "RELEASE_STORE_FILE",
    "RELEASE_STORE_PASSWORD",
    "RELEASE_KEY_ALIAS",
    "RELEASE_KEY_PASSWORD",
)
val releaseSigning: Map<String, String> = releaseSigningKeys
    .mapNotNull { key -> localProperties.getProperty(key)?.takeIf { it.isNotBlank() }?.let { key to it } }
    .toMap()
if (releaseSigning.isNotEmpty() && releaseSigning.size < releaseSigningKeys.size) {
    throw GradleException(
        "local.properties の release 署名設定が不足しています: " +
            releaseSigningKeys.filterNot { it in releaseSigning }.joinToString(),
    )
}
val releaseStoreFile = releaseSigning["RELEASE_STORE_FILE"]?.let { rootProject.file(it) }
if (releaseStoreFile != null && !releaseStoreFile.isFile) {
    throw GradleException("RELEASE_STORE_FILE の鍵ファイルが見つかりません: $releaseStoreFile")
}

android {
    namespace = "com.hiragi0w0.geopolaroid"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.hiragi0w0.geopolaroid"
        // MediaStore の RELATIVE_PATH とストレージ権限不要の書き込みを使うため Android 10 以上
        minSdk = 29
        targetSdk = 37
        versionCode = 1
        versionName = "0.2.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("boolean", "HAS_MAPS_API_KEY", mapsApiKey.isNotBlank().toString())
    }

    signingConfigs {
        if (releaseStoreFile != null) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = releaseSigning.getValue("RELEASE_STORE_PASSWORD")
                keyAlias = releaseSigning.getValue("RELEASE_KEY_ALIAS")
                keyPassword = releaseSigning.getValue("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation("com.google.android.gms:play-services-location:21.4.0")
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.maps.compose)
    implementation(libs.coil.compose)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.sqlite.jdbc)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
