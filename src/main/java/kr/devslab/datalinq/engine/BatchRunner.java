/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.engine;

import kr.devslab.datalinq.core.Operation;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.function.BiConsumer;

/**
 * Runs many INDEPENDENT operations concurrently on virtual threads, bounded by a permit
 * count so we never open more target connections than the database can handle. Each
 * operation runs in its own connection + transaction (via {@link MigrationEngine}), so only
 * operations that don't depend on each other should be batched together.
 *
 * <p>Structured without the preview {@code StructuredTaskScope}: the per-task virtual-thread
 * {@link ExecutorService} is used in try-with-resources, so {@link #runAll} returns only
 * after every task has finished. A single failing operation is captured in its
 * {@link Result} - it never aborts the batch.
 */
public final class BatchRunner {

    /** Per-operation outcome. */
    public record Result(Operation operation, boolean ok, int rows, String error) { }

    /** What to do for one operation (injected so the runner can be tested without a database). */
    @FunctionalInterface
    public interface OperationRunner {
        int run(Operation op) throws Exception;
    }

    private final int maxParallel;

    public BatchRunner(int maxParallel) {
        this.maxParallel = Math.max(1, maxParallel);
    }

    /** Convenience {@link OperationRunner} backed by {@link MigrationEngine}. */
    public static OperationRunner using(MigrationEngine engine, boolean dryRun,
                                        BiConsumer<Operation, String> onLog) {
        return op -> engine.run(op, dryRun, line -> onLog.accept(op, line));
    }

    /** Runs all operations concurrently (bounded by maxParallel); results in submission order. */
    public List<Result> runAll(List<Operation> ops, OperationRunner runner) {
        Semaphore permits = new Semaphore(maxParallel);
        List<Future<Result>> futures = new ArrayList<>();
        try (ExecutorService scope = Executors.newVirtualThreadPerTaskExecutor()) {
            for (Operation op : ops) {
                futures.add(scope.submit(() -> {
                    permits.acquire();
                    try {
                        return new Result(op, true, runner.run(op), null);
                    } catch (Exception e) {
                        return new Result(op, false, 0, String.valueOf(e.getMessage()));
                    } finally {
                        permits.release();
                    }
                }));
            }
        } // executor close() blocks until every task completes (structured join)

        List<Result> results = new ArrayList<>(futures.size());
        for (Future<Result> f : futures) {
            try {
                results.add(f.get());
            } catch (Exception e) {
                results.add(new Result(null, false, 0, String.valueOf(e.getMessage())));
            }
        }
        return results;
    }
}
