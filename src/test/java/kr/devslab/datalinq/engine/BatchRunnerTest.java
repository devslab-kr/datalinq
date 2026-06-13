/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.engine;

import kr.devslab.datalinq.core.Operation;
import kr.devslab.datalinq.core.OperationType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Verifies the virtual-thread batch runner mechanics without touching a database. */
class BatchRunnerTest {

    private static Operation op(String id) {
        return new Operation(0, id, id, "", OperationType.HANDLER, Path.of("."),
                List.of(), "", "", "", "h", false, "");
    }

    private static List<Operation> ops(int n) {
        List<Operation> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(op("op" + i));
        }
        return list;
    }

    @Test
    void runsAllAndCollectsResults() {
        List<BatchRunner.Result> results = new BatchRunner(4).runAll(ops(20), o -> 7);
        assertEquals(20, results.size());
        assertTrue(results.stream().allMatch(BatchRunner.Result::ok));
        assertTrue(results.stream().allMatch(r -> r.rows() == 7));
    }

    @Test
    void neverExceedsMaxParallel() {
        int max = 3;
        AtomicInteger current = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();

        new BatchRunner(max).runAll(ops(30), o -> {
            int now = current.incrementAndGet();
            peak.accumulateAndGet(now, Math::max);
            try {
                Thread.sleep(20);
            } finally {
                current.decrementAndGet();
            }
            return 1;
        });

        assertTrue(peak.get() <= max, "peak concurrency " + peak.get() + " exceeded " + max);
        assertTrue(peak.get() >= 2, "expected real concurrency, peak was " + peak.get());
    }

    @Test
    void capturesPerOperationFailureWithoutAbortingBatch() {
        List<BatchRunner.Result> results = new BatchRunner(2).runAll(
                List.of(op("ok"), op("boom")),
                o -> {
                    if (o.id().equals("boom")) {
                        throw new RuntimeException("kaboom");
                    }
                    return 1;
                });
        assertTrue(results.stream().anyMatch(r -> r.ok() && r.rows() == 1));
        BatchRunner.Result failed = results.stream().filter(r -> !r.ok()).findFirst().orElseThrow();
        assertEquals("kaboom", failed.error());
    }
}
