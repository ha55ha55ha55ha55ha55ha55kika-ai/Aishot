plugins {
    id("com.android.application") version "7.0.4" apply false
    kotlin("android") version "1.7.20" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        jcenter()
    }
}
