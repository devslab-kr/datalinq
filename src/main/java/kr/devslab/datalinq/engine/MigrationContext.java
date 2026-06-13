/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.engine;

import kr.devslab.datalinq.config.AppConfig;
import kr.devslab.datalinq.core.Operation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Everything a runner/handler needs for one execution: the resolved source + target
 * connections, the operation, the dry-run flag, and a log sink.
 * <p>
 * The TARGET connection is managed by {@link MigrationEngine} (autoCommit off; commit on
 * success, rollback on error or in dry-run). Handlers that need extra datasources (e.g. a
 * join across two sources) can open more via {@link #connection(String)}; those are closed
 * automatically when the operation finishes and are NOT part of the target transaction.
 */
public final class MigrationContext {

    private final Connection source;
    private final Connection target;
    private final Operation operation;
    private final boolean dryRun;
    private final Consumer<String> log;
    private final AppConfig config;
    private final List<Connection> extra = new ArrayList<>();

    public MigrationContext(Connection source, Connection target, Operation operation,
                            boolean dryRun, Consumer<String> log, AppConfig config) {
        this.source = source;
        this.target = target;
        this.operation = operation;
        this.dryRun = dryRun;
        this.log = log;
        this.config = config;
    }

    public Connection source() {
        return source;
    }

    public Connection target() {
        return target;
    }

    public Operation operation() {
        return operation;
    }

    public boolean dryRun() {
        return dryRun;
    }

    public void log(String message) {
        log.accept(message);
    }

    /** Reads a SQL file from this operation's folder. */
    public String sql(String fileName) throws IOException {
        return Files.readString(operation.dir().resolve(fileName), StandardCharsets.UTF_8);
    }

    /**
     * Opens an ADDITIONAL named datasource connection (for handlers that need more than the
     * operation's source/target). Auto-closed when the operation finishes.
     */
    public Connection connection(String datasource) throws SQLException {
        Connection c = config.connection(datasource);
        extra.add(c);
        return c;
    }

    void closeExtras() {
        for (Connection c : extra) {
            try {
                c.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
        extra.clear();
    }
}
