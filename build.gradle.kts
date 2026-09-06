plugins {
    java
    id("org.springframework.boot") version "3.5.16"
}

group = "com.example.feniksdemo"
version = "0.0.1-SNAPSHOT"

require(JavaVersion.current() == JavaVersion.VERSION_21) {
    "Run Gradle with JDK 21: set JAVA_HOME for the current shell."
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(org.springframework.boot.gradle.plugin.SpringBootPlugin.BOM_COORDINATES))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("com.h2database:h2")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    systemProperty("spring.profiles.active", "test")
}

springBoot {
    mainClass = "com.example.feniksdemo.FeniksDemoApplication"
}

listOf("Migrate", "Info").forEach { operation ->
    tasks.register<JavaExec>("flyway$operation") {
        group = "database"
        description = "Runs Flyway ${operation.lowercase()} with the app stopped."
        classpath = sourceSets.main.get().runtimeClasspath
        mainClass = "com.example.feniksdemo.FlywayCommand"
        javaLauncher = javaToolchains.launcherFor(java.toolchain)
        workingDir = rootDir
        args(operation.lowercase())
    }
}


tasks.register<JavaExec>("demoReset") {
    group = "database"
    description = "Recreates and seeds only the local demo database; stop the app first."
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass = "com.example.feniksdemo.FlywayCommand"
    javaLauncher = javaToolchains.launcherFor(java.toolchain)
    workingDir = rootDir
    args("reset")
}
