import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

group = "com.github.takuji31"
version = "1.0-SNAPSHOT"

buildscript {
    var kotlinVersion: String by extra
    kotlinVersion = "1.2.20"

    repositories {
        mavenCentral()
    }
    
    dependencies {
        classpath(kotlinModule("gradle-plugin", kotlinVersion))
    }
    
}

apply {
    plugin("kotlin")
}

val kotlinVersion: String by extra

repositories {
    mavenCentral()
}

dependencies {
    compile(kotlinModule("stdlib-jdk8", kotlinVersion))
    compile("com.github.spullara.mustache.java:compiler:0.9.5")
}

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
}

