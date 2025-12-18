plugins {
    kotlin("jvm") version "2.1.20"
    id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
    maven("https://central.sonatype.com/repository/maven-snapshots/")
    maven("https://packages.jetbrains.team/maven/p/kotlin/kotlin-dev/")
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://www.jetbrains.com/intellij-repository/snapshots")
    maven("https://packages.jetbrains.team/maven/p/kotlin/kotlin-dev/")
}

dependencies {
    intellijPlatform {
        intellijIdea("2025.1")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }
    testImplementation(kotlin("test"))
    implementation(kotlin("stdlib"))
    // Работа с Git
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.4.0.202509020913-r")
    // Kotlin compiler + PSI
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.1.20")
    implementation("org.jetbrains.kotlin:kotlin-stdlib:2.1.20")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(21)
}

tasks.named("patchPluginXml") {
    // версия IDEA для скачивания
    doFirst {
        println("IntelliJ SDK: 2025.1 будет использоваться")
    }
}