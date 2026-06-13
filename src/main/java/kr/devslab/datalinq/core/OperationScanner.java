/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.core;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Scans an {@code sql/} root and turns each subfolder into an {@link Operation}.
 * <p>
 * Convention: folder {@code NN_Some_Name} -> order {@code NN}, label {@code "Some Name"}.
 * Type is inferred (see {@link #inferType}); an optional {@code operation.properties}
 * file can override description / source / target / table / handler / destructive / confirm.
 */
public final class OperationScanner {

    private static final Pattern PREFIX = Pattern.compile("^(\\d+)[_-](.+)$");

    private final Path root;

    public OperationScanner(Path root) {
        this.root = root;
    }

    public Path root() {
        return root;
    }

    public List<Operation> scan() throws IOException {
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> dirs = Files.list(root)) {
            return dirs.filter(Files::isDirectory)
                    .map(this::toOperation)
                    .sorted(Comparator.comparingInt(Operation::order).thenComparing(Operation::id))
                    .toList();
        }
    }

    private Operation toOperation(Path dir) {
        String folder = dir.getFileName().toString();
        Matcher m = PREFIX.matcher(folder);
        int order = m.matches() ? Integer.parseInt(m.group(1)) : 9999;
        String displayName = (m.matches() ? m.group(2) : folder).replace('_', ' ');

        List<Path> sqlFiles = listSql(dir);
        Properties meta = loadMeta(dir);

        String description = meta.getProperty("description", "").trim();
        String sourceDb = meta.getProperty("source", "").trim();      // datasource name
        String targetDb = meta.getProperty("target", "").trim();      // datasource name
        String targetTable = meta.getProperty("table", "").trim();    // ETL target table
        String handlerName = meta.getProperty("handler", "").trim();
        boolean destructive = Boolean.parseBoolean(meta.getProperty("destructive", "false").trim());
        String confirmText = meta.getProperty("confirm", "").trim();
        String typeHint = meta.getProperty("type", "").trim().toUpperCase(Locale.ROOT);

        OperationType type = inferType(typeHint, handlerName, sqlFiles);

        return new Operation(order, folder, displayName, description, type, dir, sqlFiles,
                sourceDb, targetDb, targetTable, handlerName, destructive, confirmText);
    }

    private static OperationType inferType(String hint, String handlerName, List<Path> sqlFiles) {
        if (!handlerName.isEmpty()) {
            return OperationType.HANDLER;
        }
        switch (hint) {
            case "HANDLER":
                return OperationType.HANDLER;
            case "ETL":
                return OperationType.ETL;
            case "SCRIPT":
                return OperationType.SCRIPT;
            default:
                // no explicit hint: a source.sql means "migrate" (ETL), otherwise run scripts on target
                return hasFile(sqlFiles, "source.sql") ? OperationType.ETL : OperationType.SCRIPT;
        }
    }

    private static boolean hasFile(List<Path> files, String name) {
        return files.stream().anyMatch(p -> p.getFileName().toString().equalsIgnoreCase(name));
    }

    private List<Path> listSql(Path dir) {
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".sql"))
                    .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private Properties loadMeta(Path dir) {
        Properties p = new Properties();
        Path meta = dir.resolve("operation.properties");
        if (Files.exists(meta)) {
            try (Reader in = Files.newBufferedReader(meta, StandardCharsets.UTF_8)) {
                p.load(in);
            } catch (IOException ignored) {
                // treat as no metadata
            }
        }
        return p;
    }
}
