plugins {
    id("java")
}

group = "org.example"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
    maven("https://jitpack.io")

}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    compileOnly("org.spigotmc:spigot-api:1.12.2-R0.1-SNAPSHOT"){isTransitive = false}
    compileOnly(files("libs/LangGeShiPingAPI-1.0-SNAPSHOT.jar"))
    compileOnly(files("libs/SX-Attribute-2.0.2.jar"))
}

tasks.test {
    useJUnitPlatform()
}