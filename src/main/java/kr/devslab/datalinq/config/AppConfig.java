/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads / edits / saves {@code application.yml}. Supports MULTIPLE named datasources - any of
 * which can act as a source or a target. An operation picks its source/target datasource by
 * name, falling back to {@code defaults.source} / {@code defaults.target}.
 *
 * <pre>
 * datasources:
 *   legacy-erp: { url: jdbc:sqlserver://..., username: sa,   password: "" }
 *   new-core:   { url: jdbc:mariadb://...,   username: root, password: "" }
 * defaults:
 *   source: legacy-erp
 *   target: new-core
 * options:
 *   batch-size: 1000
 *   dry-run-default: true
 *   language: en
 * </pre>
 *
 * Backed by a plain nested {@code Map} (no POJO binding) so it stays GraalVM native-image
 * friendly. The DB Connection screen mutates datasources and calls {@link #save()}.
 */
public final class AppConfig {

    private final Path file;
    private final Map<String, Object> root;

    private AppConfig(Path file, Map<String, Object> root) {
        this.file = file;
        this.root = root;
    }

    public static AppConfig load(Path file) throws IOException {
        return load(file, file);
    }

    /**
     * Reads config from {@code readFrom} but remembers {@code saveTo} as the {@link #save()}
     * target. On first run the packaged {@code application.example.yml} can seed the values
     * while edits are written to the user's own {@code application.yml}.
     */
    @SuppressWarnings("unchecked")
    public static AppConfig load(Path readFrom, Path saveTo) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        if (Files.exists(readFrom)) {
            try (Reader r = Files.newBufferedReader(readFrom, StandardCharsets.UTF_8)) {
                Object loaded = new Yaml().load(r);
                if (loaded instanceof Map<?, ?> m) {
                    root = (Map<String, Object>) m;
                }
            }
        }
        return new AppConfig(saveTo, root);
    }

    /**
     * Seeds from a bundled classpath resource (e.g. {@code /application.example.yml} baked into
     * the jar) when no external config exists yet, while {@code saveTo} remains the
     * {@link #save()} target so the first edit writes the user's own {@code application.yml}.
     */
    @SuppressWarnings("unchecked")
    public static AppConfig loadResource(String resource, Path saveTo) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        try (InputStream in = AppConfig.class.getResourceAsStream(resource)) {
            if (in != null) {
                Object loaded = new Yaml().load(new InputStreamReader(in, StandardCharsets.UTF_8));
                if (loaded instanceof Map<?, ?> m) {
                    root = (Map<String, Object>) m;
                }
            }
        }
        return new AppConfig(saveTo, root);
    }

    // ---- datasources ----

    public List<String> datasourceNames() {
        Object v = root.get("datasources");
        List<String> names = new ArrayList<>();
        if (v instanceof Map<?, ?> m) {
            for (Object k : m.keySet()) {
                names.add(k.toString());
            }
        }
        return names;
    }

    public String type(String datasource)     { return field(datasource, "type"); }
    public String host(String datasource)     { return field(datasource, "host"); }
    public String port(String datasource)     { return field(datasource, "port"); }
    public String database(String datasource) { return field(datasource, "database"); }
    public String username(String datasource) { return field(datasource, "username"); }
    public String password(String datasource) { return field(datasource, "password"); }

    /**
     * The effective JDBC URL: built from {@code type/host/port/database} for a structured type,
     * otherwise the stored {@code url} (a {@code custom} type, or a legacy datasource with no type).
     */
    public String url(String datasource) {
        String type = field(datasource, "type");
        if (JdbcUrls.isStructured(type)) {
            return JdbcUrls.build(type, host(datasource), port(datasource), database(datasource));
        }
        return field(datasource, "url");
    }

    /** Stores a custom datasource (hand-written URL). */
    public void setDatasource(String name, String url, String username, String password) {
        Map<String, Object> ds = child(child(root, "datasources"), name);
        ds.put("type", JdbcUrls.CUSTOM);
        ds.put("url", url);
        ds.put("username", username);
        ds.put("password", password);
        ds.remove("host");
        ds.remove("port");
        ds.remove("database");
    }

    /** Stores a structured datasource (type + host/port/database); the URL is derived. */
    public void setDatasourceStructured(String name, String type, String host, String port,
                                        String database, String username, String password) {
        Map<String, Object> ds = child(child(root, "datasources"), name);
        ds.put("type", type);
        ds.put("host", host);
        ds.put("port", port);
        ds.put("database", database);
        ds.put("username", username);
        ds.put("password", password);
        ds.remove("url");
    }

    @SuppressWarnings("unchecked")
    public void removeDatasource(String name) {
        Object v = root.get("datasources");
        if (v instanceof Map<?, ?> m) {
            ((Map<String, Object>) m).remove(name);
        }
    }

    // ---- defaults ----

    public String defaultSource() { return str("defaults", "source"); }
    public String defaultTarget() { return str("defaults", "target"); }

    public void setDefaultSource(String name) { child(root, "defaults").put("source", name); }
    public void setDefaultTarget(String name) { child(root, "defaults").put("target", name); }

    // ---- options ----

    public int batchSize() {
        Object v = path("options", "batch-size");
        return v instanceof Number n ? n.intValue() : 1000;
    }

    public boolean dryRunDefault() {
        Object v = path("options", "dry-run-default");
        return !(v instanceof Boolean b) || b; // default true
    }

    /** Whether the DB Connection screen masks password values ({@code options.mask-password}). */
    public boolean maskPassword() {
        Object v = path("options", "mask-password");
        return !(v instanceof Boolean b) || b; // default true
    }

    public String language() {
        Object v = path("options", "language");
        return v == null ? "" : v.toString().trim();
    }

    /** Directory holding migration folders ({@code options.sql-dir}); blank -> "./sql". */
    public String sqlDir() {
        Object v = path("options", "sql-dir");
        return v == null ? "" : v.toString().trim();
    }

    public void setSqlDir(String dir) {
        child(root, "options").put("sql-dir", dir);
    }

    /** Sets an arbitrary {@code options.<key>} value (used by the Settings screen). */
    public void setOption(String key, Object value) {
        child(root, "options").put(key, value);
    }

    /** Max operations run concurrently ({@code options.max-parallel}); bounds DB connections. */
    public int maxParallel() {
        Object v = path("options", "max-parallel");
        return v instanceof Number n ? n.intValue() : 4;
    }

    // ---- connections ----

    /** Opens a connection to the named datasource. */
    public Connection connection(String datasource) throws SQLException {
        String url = url(datasource);
        if (url == null || url.isBlank()) {
            throw new IllegalStateException("datasource '" + datasource
                    + "' is not defined (or has no url) in application.yml");
        }
        return DriverManager.getConnection(url, username(datasource), password(datasource));
    }

    // ---- persistence ----

    public Path file() {
        return file;
    }

    public void save() throws IOException {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setPrettyFlow(true);
        if (file.getParent() != null) {
            Files.createDirectories(file.getParent());
        }
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            new Yaml(opts).dump(root, w);
        }
    }

    // ---- helpers ----

    @SuppressWarnings("unchecked")
    private String field(String datasource, String key) {
        Object all = root.get("datasources");
        if (all instanceof Map<?, ?> m) {
            Object ds = ((Map<String, Object>) m).get(datasource);
            if (ds instanceof Map<?, ?> dm) {
                Object v = ((Map<String, Object>) dm).get(key);
                return v == null ? "" : v.toString();
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Object path(String... keys) {
        Map<String, Object> m = root;
        for (int i = 0; i < keys.length - 1; i++) {
            Object v = m.get(keys[i]);
            if (!(v instanceof Map)) {
                return null;
            }
            m = (Map<String, Object>) v;
        }
        return m.get(keys[keys.length - 1]);
    }

    private String str(String... keys) {
        Object v = path(keys);
        return v == null ? "" : v.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> child(Map<String, Object> m, String key) {
        Object v = m.get(key);
        if (v instanceof Map) {
            return (Map<String, Object>) v;
        }
        Map<String, Object> created = new LinkedHashMap<>();
        m.put(key, created);
        return created;
    }
}
