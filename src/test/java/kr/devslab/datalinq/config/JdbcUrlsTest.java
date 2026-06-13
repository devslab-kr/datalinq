/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for JDBC URL building from structured fields. */
class JdbcUrlsTest {

    @Test
    void buildsSqlServerWithDefaultPort() {
        assertEquals("jdbc:sqlserver://h:1433;databaseName=db;encrypt=false;trustServerCertificate=true",
                JdbcUrls.build("sqlserver", "h", "", "db"));
    }

    @Test
    void buildsMariadbWithExplicitPort() {
        assertEquals("jdbc:mariadb://h:3307/db", JdbcUrls.build("mariadb", "h", "3307", "db"));
    }

    @Test
    void customAndUnknownTypesBuildNothing() {
        assertEquals("", JdbcUrls.build("custom", "h", "1", "db"));
        assertEquals("", JdbcUrls.build("postgresql", "h", "1", "db"));
    }

    @Test
    void isStructuredOnlyForBundledTypes() {
        assertTrue(JdbcUrls.isStructured("sqlserver"));
        assertTrue(JdbcUrls.isStructured("mariadb"));
        assertFalse(JdbcUrls.isStructured("custom"));
        assertFalse(JdbcUrls.isStructured(""));
    }
}
