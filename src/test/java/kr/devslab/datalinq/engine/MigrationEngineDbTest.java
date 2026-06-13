/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.engine;

import kr.devslab.datalinq.config.AppConfig;
import kr.devslab.datalinq.core.Operation;
import kr.devslab.datalinq.core.OperationScanner;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.MariaDBContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end engine tests against real databases: PostgreSQL as the SOURCE and MariaDB as the
 * TARGET, proving the cross-vendor row copy, the dry-run "writes nothing" guarantee, the
 * rollback-on-error guarantee, and SCRIPT execution on the target.
 *
 * <p>Needs a reachable Docker daemon; {@code disabledWithoutDocker} skips (does not fail) the
 * class when none is available, so the local build stays green on machines without Docker while
 * CI still runs it.
 */
@Testcontainers(disabledWithoutDocker = true)
class MigrationEngineDbTest {

    @Container
    static final PostgreSQLContainer<?> SOURCE = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static final MariaDBContainer<?> TARGET = new MariaDBContainer<>("mariadb:11.4");

    @TempDir
    Path sqlRoot;

    private AppConfig config;
    private final List<String> logLines = new ArrayList<>();
    private final Consumer<String> log = logLines::add;

    @BeforeEach
    void wireConfig() throws Exception {
        // A custom-type datasource keeps the container's JDBC URL verbatim; connection() uses it.
        config = AppConfig.load(Files.createTempFile("datalinq-it", ".yml"));
        config.setDatasource("src", SOURCE.getJdbcUrl(), SOURCE.getUsername(), SOURCE.getPassword());
        config.setDatasource("tgt", TARGET.getJdbcUrl(), TARGET.getUsername(), TARGET.getPassword());
        config.setDefaultSource("src");
        config.setDefaultTarget("tgt");
        logLines.clear();
    }

    @Test
    void etlCopiesRowsCrossVendor() throws Exception {
        exec(SOURCE,
                "DROP TABLE IF EXISTS customers",
                "CREATE TABLE customers (id INT, name VARCHAR(100))",
                "INSERT INTO customers VALUES (1, 'Alice'), (2, 'Bob'), (3, 'Carol')");
        exec(TARGET,
                "DROP TABLE IF EXISTS customers",
                "CREATE TABLE customers (id INT PRIMARY KEY, name VARCHAR(100))");

        Operation op = etl("01_Customers", "SELECT id AS id, name AS name FROM customers", "customers");
        int copied = new MigrationEngine(config).run(op, false, log);

        assertEquals(3, copied, "engine should report 3 rows copied");
        assertEquals(3, count(TARGET, "customers"), "target should hold the 3 copied rows");
        assertEquals("Bob", scalar(TARGET, "SELECT name FROM customers WHERE id = 2"));
    }

    @Test
    void dryRunWritesNothingToTarget() throws Exception {
        exec(SOURCE,
                "DROP TABLE IF EXISTS customers",
                "CREATE TABLE customers (id INT, name VARCHAR(100))",
                "INSERT INTO customers VALUES (1, 'Alice'), (2, 'Bob')");
        exec(TARGET,
                "DROP TABLE IF EXISTS customers",
                "CREATE TABLE customers (id INT PRIMARY KEY, name VARCHAR(100))");

        Operation op = etl("01_Customers", "SELECT id AS id, name AS name FROM customers", "customers");
        int counted = new MigrationEngine(config).run(op, true, log);

        assertEquals(2, counted, "dry-run still counts the source rows it would copy");
        assertEquals(0, count(TARGET, "customers"), "dry-run must roll back - target stays empty");
    }

    @Test
    void errorRollsBackTheWholeTargetTransaction() throws Exception {
        // Two rows with the same id violate the target's PRIMARY KEY on the second insert.
        exec(SOURCE,
                "DROP TABLE IF EXISTS customers",
                "CREATE TABLE customers (id INT, name VARCHAR(100))",
                "INSERT INTO customers VALUES (1, 'Alice'), (1, 'Dupe')");
        exec(TARGET,
                "DROP TABLE IF EXISTS customers",
                "CREATE TABLE customers (id INT PRIMARY KEY, name VARCHAR(100))");

        Operation op = etl("01_Customers", "SELECT id AS id, name AS name FROM customers", "customers");
        assertThrows(Exception.class, () -> new MigrationEngine(config).run(op, false, log));

        assertEquals(0, count(TARGET, "customers"), "a failed run must leave the target unchanged");
    }

    @Test
    void scriptRunsAgainstTheTarget() throws Exception {
        exec(TARGET,
                "DROP TABLE IF EXISTS base_data",
                "CREATE TABLE base_data (id INT PRIMARY KEY)",
                "INSERT INTO base_data VALUES (1), (2), (3)");

        Operation op = script("03_Reset", "01_reset.sql", "DELETE FROM base_data WHERE id > 1;");
        new MigrationEngine(config).run(op, false, log);

        assertEquals(1, count(TARGET, "base_data"), "script DELETE should run on the target");
    }

    @Test
    void scriptDryRunLeavesTheTargetUntouched() throws Exception {
        exec(TARGET,
                "DROP TABLE IF EXISTS base_data",
                "CREATE TABLE base_data (id INT PRIMARY KEY)",
                "INSERT INTO base_data VALUES (1), (2), (3)");

        Operation op = script("03_Reset", "01_reset.sql", "DELETE FROM base_data;");
        new MigrationEngine(config).run(op, true, log);

        assertEquals(3, count(TARGET, "base_data"), "dry-run must not execute the script");
    }

    // ---- helpers ----

    private Operation etl(String folder, String sourceSql, String table) throws Exception {
        Path dir = Files.createDirectories(sqlRoot.resolve(folder));
        Files.writeString(dir.resolve("source.sql"), sourceSql);
        Files.writeString(dir.resolve("operation.properties"), "type=etl\ntable=" + table + "\n");
        return scanOne();
    }

    private Operation script(String folder, String fileName, String sql) throws Exception {
        Path dir = Files.createDirectories(sqlRoot.resolve(folder));
        Files.writeString(dir.resolve(fileName), sql);
        Files.writeString(dir.resolve("operation.properties"), "type=script\n");
        return scanOne();
    }

    private Operation scanOne() throws Exception {
        List<Operation> ops = new OperationScanner(sqlRoot).scan();
        assertEquals(1, ops.size(), "test set up exactly one operation folder");
        return ops.get(0);
    }

    private static void exec(JdbcDatabaseContainer<?> c, String... statements) throws Exception {
        try (Connection conn = DriverManager.getConnection(c.getJdbcUrl(), c.getUsername(), c.getPassword());
             Statement st = conn.createStatement()) {
            for (String s : statements) {
                st.execute(s);
            }
        }
    }

    private static long count(JdbcDatabaseContainer<?> c, String table) throws Exception {
        Object v = scalar(c, "SELECT COUNT(*) FROM " + table);
        return ((Number) v).longValue();
    }

    private static Object scalar(JdbcDatabaseContainer<?> c, String sql) throws Exception {
        try (Connection conn = DriverManager.getConnection(c.getJdbcUrl(), c.getUsername(), c.getPassword());
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getObject(1) : null;
        }
    }
}
