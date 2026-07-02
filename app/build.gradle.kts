plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.google.gms.google-services")
    //TODO make general kotlin_version
    kotlin("plugin.serialization") version "1.9.0"
}



android {
    namespace="com.israel.cowboyfriend"
    compileSdk=36

    defaultConfig {
        applicationId="com.israel.cowboyfriend"
        minSdk=26
        targetSdk=36
        versionCode=6
        versionName="6.0"

        testInstrumentationRunner="androidx.test.runner.AndroidJUnitRunner"
    }


    buildTypes {
        release {
            isMinifyEnabled=false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility=JavaVersion.VERSION_11
        targetCompatibility=JavaVersion.VERSION_11
    }
    kotlinOptions {
        jvmTarget="11"
    }
    buildFeatures {
        viewBinding=true
    }
//    buildFeatures {
//        compose=true
//    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)
    //implementation(libs.firebase.common.ktx)
    implementation(libs.firebase.firestore)
    implementation(libs.androidx.annotation)
    implementation(libs.androidx.lifecycle.livedata.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.runtime)
    implementation(libs.play.services.location)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.circleimageview)


    ///////supbase libraries
    // Core Supabase BOM (Bill of Materials)
    implementation(platform("io.github.jan-tennert.supabase:bom:3.6.0"))
    //implementation(libs.bom)
    implementation(libs.postgrest.kt)

    // Add specific modules you need
    implementation(libs.supabase.postgrest.kt)
    implementation(libs.auth.kt)
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt:3.6.0")

    implementation(libs.ktor.client.okhttp)
    /////////////////

    //implementation(libs.ktor.client.android)
    implementation("javax.inject:javax.inject:1@jar")
    //implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
    implementation("androidx.hilt:hilt-lifecycle-viewmodel-compose:1.3.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")

    //Image Processing
    implementation("com.github.bumptech.glide:glide:5.0.7")

    //map box
    implementation("com.mapbox.maps:android-ndk27:11.14.4")
    implementation("com.mapbox.mapboxsdk:mapbox-sdk-geojson:6.9.0")
    implementation("com.google.android.gms:play-services-maps:20.0.0")

    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation("com.google.android.material:material:1.14.0")
}