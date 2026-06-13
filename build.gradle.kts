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

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
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
}

application {
    mainClass = "kr.devslab.datalinq.Main"
}

// The TUI reads from the real terminal; let `gradle run` pass stdin through.
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
