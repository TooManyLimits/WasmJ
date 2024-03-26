plugins {
    `java-library`
    `maven-publish`
    id("java")
}

java.sourceCompatibility = JavaVersion.VERSION_17
java.targetCompatibility = JavaVersion.VERSION_17

group = "io.github.toomanylimits"
version = "0.0.1"
description = "WasmJ"

repositories {
    mavenLocal()
    maven {
        url = uri("https://repo1.maven.org/maven2/")
    }
    maven {
        url = uri("https://repo.maven.apache.org/maven2/")
    }
    mavenCentral()
    gradlePluginPortal()
}

dependencies {
    implementation("org.ow2.asm:asm:9.6")
    implementation("org.ow2.asm:asm-util:9.4")
    implementation("org.ow2.asm:asm-tree:9.4")
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}

//https://kotlinlang.org/docs/gradle-configure-project.html#4c2b91a9
sourceSets.main {
    java.srcDirs("src/main/java")
}
sourceSets.test {
    java.srcDirs("src/test/java")
}

tasks.withType<JavaCompile>() {
    options.encoding = "UTF-8"
}

tasks.withType<Javadoc>() {
    options.encoding = "UTF-8"
}
