/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.config;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.sql.Driver;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Stream;

/**
 * Makes extra JDBC drivers available beyond the bundled ones (MS SQL Server / MariaDB /
 * PostgreSQL). Drivers placed in {@code ~/.datalinq/drivers/} - dropped there by hand or fetched
 * with {@link #download} - are loaded at startup and registered through a {@link DriverShim} so
 * {@link DriverManager} will use them (it ignores drivers from foreign class loaders otherwise).
 * Use such a driver from the DB Connection screen's Custom type with a hand-written URL.
 */
public final class Drivers {

    private static final String MAVEN_CENTRAL = "https://repo1.maven.org/maven2/";

    /** A Maven Central coordinate for a downloadable driver. */
    public record Coord(String group, String artifact, String version) {
        String jarName() {
            return artifact + "-" + version + ".jar";
        }

        String path() {
            return group.replace('.', '/') + "/" + artifact + "/" + version + "/" + jarName();
        }
    }

    /** Known downloadable drivers (the bundled ones are not here - they always work). */
    public static final Map<String, Coord> CATALOG = catalog();

    private Drivers() {
    }

    private static Map<String, Coord> catalog() {
        Map<String, Coord> m = new LinkedHashMap<>();
        m.put("mysql", new Coord("com.mysql", "mysql-connector-j", "9.1.0"));
        m.put("postgresql", new Coord("org.postgresql", "postgresql", "42.7.4"));
        m.put("h2", new Coord("com.h2database", "h2", "2.3.232"));
        m.put("sqlite", new Coord("org.xerial", "sqlite-jdbc", "3.47.1.0"));
        m.put("oracle", new Coord("com.oracle.database.jdbc", "ojdbc11", "23.6.0.24.10"));
        return m;
    }

    /** The directory holding user-provided / downloaded driver jars. */
    public static Path driversDir() {
        return Paths.get(System.getProperty("user.home"), ".datalinq", "drivers");
    }

    /**
     * Loads every jar in {@link #driversDir()} and registers its JDBC driver(s) via a shim.
     * Safe to call when the directory is absent (returns empty). Returns the registered driver
     * class names.
     */
    public static List<String> loadExternal() {
        List<String> loaded = new ArrayList<>();
        Path dir = driversDir();
        if (!Files.isDirectory(dir)) {
            return loaded;
        }
        List<URL> urls = new ArrayList<>();
        try (Stream<Path> jars = Files.list(dir)) {
            jars.filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar"))
                    .forEach(p -> {
                        try {
                            urls.add(p.toUri().toURL());
                        } catch (Exception ignored) {
                            // skip an unreadable entry
                        }
                    });
        } catch (IOException e) {
            return loaded;
        }
        if (urls.isEmpty()) {
            return loaded;
        }
        URLClassLoader cl = new URLClassLoader(urls.toArray(new URL[0]), Drivers.class.getClassLoader());
        for (Driver driver : ServiceLoader.load(Driver.class, cl)) {
            // Only the externally-loaded drivers need a shim; skip the bundled ones the parent sees.
            if (driver.getClass().getClassLoader() == cl) {
                try {
                    DriverManager.registerDriver(new DriverShim(driver));
                    loaded.add(driver.getClass().getName());
                } catch (Exception ignored) {
                    // a driver that won't register is simply unavailable
                }
            }
        }
        return loaded;
    }

    /**
     * Downloads a catalog driver from Maven Central into {@link #driversDir()} and returns the
     * saved jar path. It takes effect on the next launch (drivers are registered at startup).
     */
    public static Path download(String name) throws IOException {
        Coord coord = CATALOG.get(name);
        if (coord == null) {
            throw new IOException("unknown driver '" + name + "' - known: " + CATALOG.keySet());
        }
        String spec = MAVEN_CENTRAL + coord.path();
        URL url = URI.create(spec).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setConnectTimeout(15_000);
        conn.setReadTimeout(30_000);
        conn.setInstanceFollowRedirects(true);
        conn.setRequestProperty("User-Agent", "datalinq");
        int code = conn.getResponseCode();
        if (code != HttpURLConnection.HTTP_OK) {
            conn.disconnect();
            throw new IOException("could not download '" + name + "' (HTTP " + code + "): " + spec
                    + (code == HttpURLConnection.HTTP_NOT_FOUND
                            ? " - this version is not on Maven Central; the catalog coordinate may be wrong"
                            : ""));
        }
        Files.createDirectories(driversDir());
        Path target = driversDir().resolve(coord.jarName());
        Path part = driversDir().resolve(coord.jarName() + ".part");
        try (InputStream in = conn.getInputStream()) {
            Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            conn.disconnect();
        }
        // Move into place only after a complete download, so a failure never leaves a truncated jar.
        Files.move(part, target, StandardCopyOption.REPLACE_EXISTING);
        return target;
    }
}
