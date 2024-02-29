rootProject.name = "WasmJ"

pluginManagement {
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
}