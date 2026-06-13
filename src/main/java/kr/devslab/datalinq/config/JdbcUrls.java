/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.config;

import java.util.List;

/**
 * Builds JDBC URLs from a database type + host / port / database, so the DB Connection screen can
 * offer structured fields instead of a raw URL. Only the bundled drivers are offered:
 * {@code sqlserver} and {@code mariadb} (the MariaDB driver also connects to MySQL servers).
 * {@code custom} keeps a hand-written URL verbatim.
 */
public final class JdbcUrls {

    public static final String SQLSERVER = "sqlserver";
    public static final String MARIADB = "mariadb";
    public static final String POSTGRESQL = "postgresql";
    public static final String CUSTOM = "custom";

    /** Selectable types in display order (custom last). */
    public static final List<String> TYPES = List.of(SQLSERVER, MARIADB, POSTGRESQL, CUSTOM);

    private JdbcUrls() {
    }

    /** The conventional default port for a type ("" for custom). */
    public static String defaultPort(String type) {
        return switch (type == null ? "" : type) {
            case SQLSERVER -> "1433";
            case MARIADB -> "3306";
            case POSTGRESQL -> "5432";
            default -> "";
        };
    }

    /** True if the type uses structured host/port/database fields (false for custom). */
    public static boolean isStructured(String type) {
        return SQLSERVER.equals(type) || MARIADB.equals(type) || POSTGRESQL.equals(type);
    }

    /**
     * Builds a JDBC URL for a structured type. Returns "" for an unknown / custom type (the caller
     * uses the hand-written URL there). A blank port falls back to {@link #defaultPort}.
     */
    public static String build(String type, String host, String port, String database) {
        String h = host == null ? "" : host.trim();
        String db = database == null ? "" : database.trim();
        String p = (port == null || port.isBlank()) ? defaultPort(type) : port.trim();
        return switch (type == null ? "" : type) {
            case SQLSERVER -> "jdbc:sqlserver://" + h + ":" + p
                    + ";databaseName=" + db + ";encrypt=false;trustServerCertificate=true";
            case MARIADB -> "jdbc:mariadb://" + h + ":" + p + "/" + db;
            case POSTGRESQL -> "jdbc:postgresql://" + h + ":" + p + "/" + db;
            default -> "";
        };
    }
}
