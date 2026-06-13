plugins {
    application
    id("com.gradleup.shadow") version "9.4.2"
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

// Compile to Java 21 bytecode (virtual threads are stable since 21) so the app runs on any
// JDK 21+ without juggling JAVA_HOME. Running it on JDK 24+/25 additionally avoids
// virtual-thread pinning in JDBC drivers (JEP 491) - recommended for best throughput, but
// not required.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

// The default i18n/ and branding/ resources live in src/main/resources and are bundled into the
// jar automatically. application.example.yml stays at the project root as a user-facing config
// template (.env.example style), so copy that one into the jar for the first-run seed + init.
// External files (next to the jar, or in the CWD) still override these - see Home / the loaders.
tasks.processResources {
    from("application.example.yml")
}

tasks.test {
    useJUnitPlatform()
}

application {
    mainClass = "kr.devslab.datalinq.Main"
    // Korean content + Windows console: force UTF-8 stdout/stderr.
    // --enable-native-access: JLine uses native terminal access (silences the Java 25
    // restricted-method warning and stays working when it becomes enforced).
    applicationDefaultJvmArgs = listOf(
        "-Dstdout.encoding=UTF-8",
        "-Dstderr.encoding=UTF-8",
        "--enable-native-access=ALL-UNNAMED",
    )
}

// The TUI reads from the real terminal; let `gradle run` / `gradle runShadow` pass stdin through.
tasks.named<JavaExec>("run") {
    standardInput = System.`in`
}
tasks.named<JavaExec>("runShadow") {
    standardInput = System.`in`
}

// The droppable deliverable: a single self-contained jar. mergeServiceFiles() is essential -
// the JDBC drivers (java.sql.Driver) and our MigrationHandler register via ServiceLoader, and a
// naive fat jar would keep only one service file. Stripping signature files avoids the signed-jar
// SecurityException when driver jars are merged.
tasks.shadowJar {
    archiveBaseName = "datalinq"
    archiveClassifier = ""
    archiveVersion = ""
    // duplicatesStrategy takes precedence over transformers: the default would drop the 2nd
    // META-INF/services/java.sql.Driver (MariaDB's) before mergeServiceFiles() sees it, leaving
    // only the MSSQL driver registered. INCLUDE lets the transformer merge both.
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
}

// Bundle the editable default resources into the distribution image (alongside bin/ and lib/)
// so the launcher resolves them via Home (the install dir) and works when run from anywhere.
// application.yml is intentionally NOT bundled - it is gitignored and holds credentials; the
// app seeds from application.example.yml on first run and saves edits to application.yml.
distributions {
    named("main") {
        contents {
            from("i18n") { into("i18n") }
            from("branding") { into("branding") }
            from("sql") { into("sql") }
            from("application.example.yml")
        }
    }
}
