plugins {
    application
}

group = "kr.devslab"
version = "0.1.0-SNAPSHOT"

repositories {
    mavenCentral()
    // TamboUI is currently snapshot-only
    maven {
        url = uri("https://central.sonatype.com/repository/maven-snapshots/")
        mavenContent { snapshotsOnly() }
    }
}

dependencies {
    // TUI front-end (engine itself uses none of this)
    implementation("dev.tamboui:tamboui-toolkit:0.4.0-SNAPSHOT")
    runtimeOnly("dev.tamboui:tamboui-jline3-backend:0.4.0-SNAPSHOT")

    // application.yml config (loaded as a Map -> native-image friendly)
    implementation("org.yaml:snakeyaml:2.3")

    // JDBC drivers: source = MS SQL Server, target = MariaDB
    implementation("com.microsoft.sqlserver:mssql-jdbc:12.8.1.jre11")
    implementation("org.mariadb.jdbc:mariadb-java-client:3.5.2")

    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

// Java 25 (best virtual-thread + JDBC behaviour; no synchronized pinning).
// A toolchain so `./gradlew run` works no matter which JDK launches Gradle - Gradle
// locates a JDK 25 (auto-detected, e.g. from ~/.jdks) for compiling and running.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "kr.devslab.datalinq.Main"
    // Korean content + Windows console: force UTF-8 stdout/stderr
    applicationDefaultJvmArgs = listOf("-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8")
}

// The TUI reads from the real terminal; let `gradle run` pass stdin through.
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
