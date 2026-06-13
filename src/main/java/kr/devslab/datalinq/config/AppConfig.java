/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */

package kr.devslab.datalinq.config;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads / edits / saves {@code application.yml}: datasource (source = MS SQL, target = MariaDB)
 * plus options. Backed by a plain nested {@code Map} (no POJO binding) so it stays GraalVM
 * native-image friendly. The DB Connection screen mutates fields and calls {@link #save()}.
 *
 * <pre>
 * datasource:
 *   source: { url, username, password }
 *   target: { url, username, password }
 * options:
 *   batch-size: 1000
 *   dry-run-default: true
 * </pre>
 */
public final class AppConfig {

    private final Path file;
    private final Map<String, Object> root;

    private AppConfig(Path file, Map<String, Object> root) {
        this.file = file;
        this.root = root;
    }

    @SuppressWarnings("unchecked")
    public static AppConfig load(Path file) throws IOException {
        Map<String, Object> root = new LinkedHashMap<>();
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Object loaded = new Yaml().load(r);
                if (loaded instanceof Map<?, ?> m) {
                    root = (Map<String, Object>) m;
                }
            }
        }
        return new AppConfig(file, root);
    }

    // ---- datasource accessors ----

    public String sourceUrl()      { return str("datasource", "source", "url"); }
    public String sourceUsername() { return str("datasource", "source", "username"); }
    public String sourcePassword() { return str("datasource", "source", "password"); }
    public String targetUrl()      { return str("datasource", "target", "url"); }
    public String targetUsername() { return str("datasource", "target", "username"); }
    public String targetPassword() { return str("datasource", "target", "password"); }

    public void setSource(String url, String username, String password) {
        setDatasource("source", url, username, password);
    }

    public void setTarget(String url, String username, String password) {
        setDatasource("target", url, username, password);
    }

    // ---- options ----

    public int batchSize() {
        Object v = path("options", "batch-size");
        return v instanceof Number n ? n.intValue() : 1000;
    }

    public boolean dryRunDefault() {
        Object v = path("options", "dry-run-default");
        return !(v instanceof Boolean b) || b; // default true
    }

    /** UI language code (e.g. "ko"); blank = system default. */
    public String language() {
        Object v = path("options", "language");
        return v == null ? "" : v.toString().trim();
    }

    // ---- connections ----

    public Connection openSource() throws SQLException {
        return DriverManager.getConnection(require(sourceUrl(), "datasource.source.url"),
                sourceUsername(), sourcePassword());
    }

    public Connection openTarget() throws SQLException {
        return DriverManager.getConnection(require(targetUrl(), "datasource.target.url"),
                targetUsername(), targetPassword());
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

    private void setDatasource(String role, String url, String username, String password) {
        Map<String, Object> ds = child(root, "datasource");
        Map<String, Object> r = child(ds, role);
        r.put("url", url);
        r.put("username", username);
        r.put("password", password);
    }

    private static String require(String value, String key) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("missing config: " + key + " (set it in application.yml)");
        }
        return value;
    }
}
