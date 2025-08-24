plugins {
    id("java")
    id("application")
}

group = "org.rengen.takehome"
version = "ingestor"

repositories {
    mavenCentral()
}

dependencies {

    implementation("io.vertx:vertx-web:4.4.5")
    implementation("io.vertx:vertx-web-client:4.4.5")
    implementation("io.vertx:vertx-core:4.4.5")
    implementation("io.vertx:vertx-config:4.4.5")
    implementation("org.glassfish.tyrus.bundles:tyrus-standalone-client:1.18")
    implementation("javax.json:javax.json-api:1.1.4")
    implementation("org.glassfish:javax.json:1.1.4")

    testImplementation("org.junit.jupiter:junit-jupiter:5.9.3")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testImplementation("org.mockito:mockito-core:5.2.0")
    testImplementation("org.mockito:mockito-junit-jupiter:5.2.0")
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

application {
    mainClass.set("org.rengen.takehome.Main")
}

tasks.test {
    useJUnitPlatform()
}
