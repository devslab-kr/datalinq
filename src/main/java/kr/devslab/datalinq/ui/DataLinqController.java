/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.ui;

import kr.devslab.datalinq.core.Operation;
import kr.devslab.datalinq.engine.BatchRunner;
import kr.devslab.datalinq.i18n.Messages;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Controller in tamboui's recommended MVC: it owns ALL application state and exposes
 * commands (mutate) and queries (read). It has no TamboUI dependency, so its behaviour -
 * navigation, the confirm flow, running, error handling, parallel run - is unit-testable
 * without a terminal or a database (the operation source and per-op runner are injected).
 */
public final class DataLinqController {

    /** What the centre panel shows. */
    public enum Center { OUTPUT, ABOUT, CONFIRM }

    /** A menu entry: a fixed base action, or a discovered migration. */
    public record Entry(Kind kind, String label, Operation operation) {
        public enum Kind { SETTINGS, DB_CONNECTION, ABOUT, MIGRATION }
    }

    /** Supplies the migration operations (prod: scan the sql folder; test: a fixed list). */
    @FunctionalInterface
    public interface OperationProvider {
        List<Operation> scan() throws Exception;
    }

    /** Runs one operation (prod: MigrationEngine; test: a fake) - injected for testability. */
    @FunctionalInterface
    public interface Runner {
        int run(Operation op, boolean dryRun, Consumer<String> log) throws Exception;
    }

    private final Messages msg;
    private final int maxParallel;
    private final OperationProvider provider;
    private final Runner runner;

    private final List<Entry> entries = new ArrayList<>();
    private final List<String> output = Collections.synchronizedList(new ArrayList<>());
    private int selected;
    private boolean dryRun;
    private Center center = Center.OUTPUT;
    private Entry pendingConfirm;
    private volatile boolean running;

    public DataLinqController(Messages msg, boolean dryRunDefault, int maxParallel,
                              OperationProvider provider, Runner runner) {
        this.msg = msg;
        this.dryRun = dryRunDefault;
        this.maxParallel = Math.max(1, maxParallel);
        this.provider = provider;
        this.runner = runner;
    }

    public void init() {
        rescan();
    }

    // ---- queries ----

    public Messages msg() {
        return msg;
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public int selected() {
        return selected;
    }

    public Entry selectedEntry() {
        return (selected >= 0 && selected < entries.size()) ? entries.get(selected) : null;
    }

    public boolean dryRun() {
        return dryRun;
    }

    public Center center() {
        return center;
    }

    public Entry pendingConfirm() {
        return pendingConfirm;
    }

    public boolean running() {
        return running;
    }

    public List<String> output() {
        synchronized (output) {
            return List.copyOf(output);
        }
    }

    public String modeLabel() {
        return dryRun ? msg.get("mode.dryRun") : msg.get("mode.execute");
    }

    // ---- commands ----

    public void setSelected(int index) {
        if (index < 0) {
            selected = 0;
        } else if (index >= entries.size()) {
            selected = Math.max(0, entries.size() - 1);
        } else {
            selected = index;
        }
    }

    public void moveUp() {
        setSelected(selected - 1);
    }

    public void moveDown() {
        setSelected(selected + 1);
    }

    public void rescan() {
        entries.clear();
        entries.add(new Entry(Entry.Kind.SETTINGS, msg.get("menu.settings"), null));
        entries.add(new Entry(Entry.Kind.DB_CONNECTION, msg.get("menu.dbConnection"), null));
        entries.add(new Entry(Entry.Kind.ABOUT, msg.get("menu.about"), null));
        try {
            for (Operation op : provider.scan()) {
                entries.add(new Entry(Entry.Kind.MIGRATION, op.displayName(), op));
            }
        } catch (Exception e) {
            log("scan failed: " + e.getMessage());
        }
        setSelected(selected);
    }

    public void toggleDryRun() {
        dryRun = !dryRun;
        log(modeLabel());
    }

    /** Enter on the selected entry. */
    public void activate() {
        Entry e = selectedEntry();
        if (e == null) {
            return;
        }
        switch (e.kind()) {
            case ABOUT -> center = Center.ABOUT;
            case SETTINGS, DB_CONNECTION -> {
                center = Center.OUTPUT;
                log("(" + e.label() + " - coming soon)");
            }
            case MIGRATION -> {
                Operation op = e.operation();
                if (op.destructive() && !dryRun) {
                    pendingConfirm = e;
                    center = Center.CONFIRM;
                } else {
                    runMigration(e);
                }
            }
        }
    }

    public void confirmYes() {
        Entry e = pendingConfirm;
        pendingConfirm = null;
        center = Center.OUTPUT;
        if (e != null) {
            runMigration(e);
        }
    }

    public void confirmNo() {
        pendingConfirm = null;
        center = Center.OUTPUT;
        log(msg.get("status.cancelled"));
    }

    public void back() {
        center = Center.OUTPUT;
        pendingConfirm = null;
    }

    /** Runs one migration. Synchronous; the View may call it on a virtual thread. */
    public void runMigration(Entry e) {
        if (e == null || e.operation() == null) {
            return;
        }
        Operation op = e.operation();
        running = true;
        center = Center.OUTPUT;
        log(msg.get("status.running", op.displayName()) + (dryRun ? "  [dry-run]" : ""));
        try {
            runner.run(op, dryRun, this::log);
            log(msg.get("status.done", op.displayName()));
        } catch (Exception ex) {
            log(msg.get("status.failed", op.displayName()) + " - " + ex.getMessage());
        } finally {
            running = false;
        }
    }

    /** Runs all discovered migrations concurrently (bounded by maxParallel virtual threads). */
    public void runAllMigrations() {
        List<Operation> ops = entries.stream()
                .filter(e -> e.kind() == Entry.Kind.MIGRATION)
                .map(Entry::operation)
                .toList();
        if (ops.isEmpty()) {
            log(msg.get("status.failed", "no migrations"));
            return;
        }
        running = true;
        center = Center.OUTPUT;
        boolean dry = dryRun;
        log(msg.get("status.running", "ALL (" + ops.size() + ", parallel " + maxParallel + ")"));
        List<BatchRunner.Result> results = new BatchRunner(maxParallel)
                .runAll(ops, op -> runner.run(op, dry, line -> log(op.displayName() + ": " + line)));
        long ok = results.stream().filter(BatchRunner.Result::ok).count();
        for (BatchRunner.Result r : results) {
            if (!r.ok()) {
                log(msg.get("status.failed", r.operation().displayName()) + " - " + r.error());
            }
        }
        log(msg.get("status.done", "ALL (" + ok + "/" + results.size() + ")"));
        running = false;
    }

    private void log(String line) {
        output.add(line);
    }
}
