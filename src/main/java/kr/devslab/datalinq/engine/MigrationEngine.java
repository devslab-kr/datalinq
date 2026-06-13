/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.engine;

import kr.devslab.datalinq.config.AppConfig;
import kr.devslab.datalinq.core.Operation;
import kr.devslab.datalinq.core.OperationType;

import java.sql.Connection;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.function.Consumer;

/**
 * Runs one operation end to end: resolves the source/target datasources (by name, falling
 * back to defaults), opens their connections, dispatches to the runner for the operation
 * type, and manages the target transaction - commit on success, rollback on error or in
 * dry-run (so dry-run truly writes nothing).
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
        String targetName = resolve(op.targetDb(), config.defaultTarget());
        if (targetName.isBlank()) {
            throw new IllegalStateException(
                    "no target datasource - set the operation's target= or defaults.target");
        }
        boolean needsSource = op.type() != OperationType.SCRIPT;
        String sourceName = needsSource ? resolve(op.sourceDb(), config.defaultSource()) : "";
        if (needsSource && sourceName.isBlank()) {
            throw new IllegalStateException(
                    "no source datasource - set the operation's source= or defaults.source");
        }

        Connection source = null;
        Connection target = null;
        MigrationContext ctx = null;
        try {
            target = config.connection(targetName);
            target.setAutoCommit(false);
            if (needsSource) {
                source = config.connection(sourceName);
            }
            log.accept("datasource: source=" + (needsSource ? sourceName : "-") + ", target=" + targetName);
            ctx = new MigrationContext(source, target, op, dryRun, log, config);

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
            if (target != null) {
                safeRollback(target, log);
            }
            throw e;
        } finally {
            if (ctx != null) {
                ctx.closeExtras();
            }
            close(source);
            close(target);
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

    private static String resolve(String operationValue, String defaultValue) {
        return (operationValue == null || operationValue.isBlank()) ? defaultValue : operationValue;
    }

    private static void safeRollback(Connection target, Consumer<String> log) {
        try {
            target.rollback();
            log.accept("rolled back");
        } catch (Exception ex) {
            log.accept("rollback failed: " + ex.getMessage());
        }
    }

    private static void close(Connection c) {
        if (c != null) {
            try {
                c.close();
            } catch (Exception ignored) {
                // best effort
            }
        }
    }
}
