import org.gradle.api.tasks.JavaExec

plugins {
    java
    application
}

group = "ru.chepenkov"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.bouncycastle:bcprov-jdk18on:1.78.1")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.78.1")
    // Логирование
    implementation("org.slf4j:slf4j-api:2.0.16")
    implementation("ch.qos.logback:logback-classic:1.5.6")

    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

application {
    // можно указать дефолтный mainClass, но мы добавим и отдельные таски
    mainClass.set("ru.chepenkov.KeyGenServer")
}

tasks.register<JavaExec>("runServer") {
    group = "application"
    description = "Run KeyGen Server"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ru.chepenkov.KeyGenServer")
}

tasks.register<JavaExec>("runClient") {
    group = "application"
    description = "Run KeyGen Client"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("ru.chepenkov.KeyGenClient")
}

tasks.test {
    useJUnitPlatform()
}
