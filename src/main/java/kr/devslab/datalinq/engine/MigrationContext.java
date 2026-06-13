/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */

package kr.devslab.datalinq.engine;

import kr.devslab.datalinq.core.Operation;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.util.function.Consumer;

/**
 * Everything a runner/handler needs for one execution: the two live connections,
 * the operation being run, the dry-run flag, and a log sink.
 * <p>
 * The TARGET connection is managed by {@link MigrationEngine} (autoCommit off; it
 * commits on success, rolls back on error or in dry-run).
 */
public final class MigrationContext {

    private final Connection source;
    private final Connection target;
    private final Operation operation;
    private final boolean dryRun;
    private final Consumer<String> log;

    public MigrationContext(Connection source, Connection target, Operation operation,
                            boolean dryRun, Consumer<String> log) {
        this.source = source;
        this.target = target;
        this.operation = operation;
        this.dryRun = dryRun;
        this.log = log;
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
}
