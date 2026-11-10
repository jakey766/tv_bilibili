plugins { id("com.android.application"); kotlin("android") }
android {
    namespace = "com.jk.tvreminder"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.jk.tvreminder"
        minSdk = 23
        targetSdk = 35
        versionCode = 4
        versionName = "1.1.2"
    }
    signingConfigs.getByName("debug") { storeFile = rootDir.resolve(".cache/android-user/debug.keystore") }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions { jvmTarget = "17" }
    testOptions { unitTests.isIncludeAndroidResources = true }
    lint { abortOnError = true }
}
dependencies {
    implementation(project(":core"))
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.robolectric:robolectric:4.14.1")
}
tasks.withType<Test>().configureEach {
    systemProperty("robolectric.dependency.repo.url", "https://repo.maven.apache.org/maven2")
    systemProperty("java.io.tmpdir", rootDir.resolve(".cache/tmp").canonicalPath)
}
