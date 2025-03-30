plugins {
    `kotlin-dsl`
}

repositories {
    mavenCentral()
}

// Configure Kotlin JVM target
kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

// Configure Java compatibility for the project
tasks.withType<JavaCompile>().configureEach {
    sourceCompatibility = JavaVersion.VERSION_21.toString()
    targetCompatibility = JavaVersion.VERSION_21.toString()
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "21"
    }
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-databind:2.15.2")
} 