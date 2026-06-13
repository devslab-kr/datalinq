/*
 * Copyright 2026 devslab
 * SPDX-License-Identifier: Apache-2.0
 */

package kr.devslab.datalinq.engine;

import kr.devslab.datalinq.config.AppConfig;
import kr.devslab.datalinq.core.Operation;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Consumer;

/**
 * Runs one operation end to end: opens source + target connections, dispatches to the
 * runner for the operation type, and manages the target transaction - commit on success,
 * rollback on error or in dry-run (so dry-run truly writes nothing).
 *
 * <p>HANDLER operations are resolved through {@link ServiceLoader} by their {@code name()}
 * key (no string-driven reflection), keeping the door open for a GraalVM native image.
 */
public final class MigrationEngine {

    private static Map<String, MigrationHandler> handlerCache;

    private final AppConfig config;

    public MigrationEngine(AppConfig config) {
        this.config = config;
    }

    public int run(Operation op, boolean dryRun, Consumer<String> log) throws Exception {
        try (Connection source = config.openSource();
             Connection target = config.openTarget()) {
            target.setAutoCommit(false);
            MigrationContext ctx = new MigrationContext(source, target, op, dryRun, log);
            try {
                int count = switch (op.type()) {
                    case ETL -> new EtlRunner().run(ctx);
                    case SCRIPT -> new ScriptRunner().run(ctx);
                    case HANDLER -> runHandler(ctx);
                };
                if (dryRun) {
                    target.rollback();
                    log.accept("(dry-run: target rolled back, nothing written)");
                } else {
                    target.commit();
                }
                return count;
            } catch (Exception e) {
                safeRollback(target, log);
                throw e;
            }
        }
    }

    private int runHandler(MigrationContext ctx) throws Exception {
        String name = ctx.operation().handlerName();
        MigrationHandler handler = handlers().get(name);
        if (handler == null) {
            throw new IllegalStateException("no handler named '" + name
                    + "' (available: " + handlers().keySet() + ")");
        }
        handler.init(ctx);
        handler.migrate();
        return -1;
    }

    /** Handlers discovered once via ServiceLoader - GraalVM registers these at build time. */
    private static synchronized Map<String, MigrationHandler> handlers() {
        if (handlerCache == null) {
            Map<String, MigrationHandler> map = new HashMap<>();
            for (MigrationHandler h : ServiceLoader.load(MigrationHandler.class)) {
                map.put(h.name(), h);
            }
            handlerCache = map;
        }
        return handlerCache;
    }

    private static void safeRollback(Connection target, Consumer<String> log) {
        try {
            target.rollback();
            log.accept("rolled back");
        } catch (Exception ex) {
            log.accept("rollback failed: " + ex.getMessage());
        }
    }
}
