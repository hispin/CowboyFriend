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
        versionCode=1
        versionName="1.0"

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
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    implementation(libs.circleimageview)
    //implementation(libs.firebase.firestore)
    //implementation (libs.circleimageview)
    //goole

    //implementation("libs.androidx.credential")
    //superbase
    //implementation(platform(libs.supabase.bom))
    //implementation(libs.auth.kt)
    implementation(platform("io.github.jan-tennert.supabase:bom:3.4.1"))
    //implementation(libs.bom)
    implementation(libs.postgrest.kt)
    implementation(libs.supabase.postgrest.kt)
    implementation(libs.auth.kt)
    implementation("io.ktor:ktor-client-android:3.4.1")
    //implementation(libs.ktor.client.android)
    implementation("javax.inject:javax.inject:1@jar")
    //implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
    implementation("androidx.hilt:hilt-lifecycle-viewmodel-compose:1.3.0")
    implementation("androidx.fragment:fragment-ktx:1.8.9")
}