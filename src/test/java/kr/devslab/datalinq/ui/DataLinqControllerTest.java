/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.ui;

import kr.devslab.datalinq.core.Operation;
import kr.devslab.datalinq.core.OperationType;
import kr.devslab.datalinq.i18n.Messages;
import kr.devslab.datalinq.ui.DataLinqController.Center;
import kr.devslab.datalinq.ui.DataLinqController.Entry;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the MVC controller - no terminal, no database. */
class DataLinqControllerTest {

    private static final Messages MSG = Messages.load(Path.of("no-such-dir"), "en"); // keys returned as-is

    private static Operation migration(String name, boolean destructive) {
        return new Operation(0, name, name, "", OperationType.SCRIPT, Path.of("."),
                List.of(), "", "", "", "", destructive, "");
    }

    private static DataLinqController controller(DataLinqController.Runner runner, Operation... ops) {
        DataLinqController c = new DataLinqController(MSG, true, 4, () -> List.of(ops), runner);
        c.init();
        return c;
    }

    @Test
    void rescanBuildsBaseMenuPlusMigrations() {
        DataLinqController c = controller((op, dry, log) -> 0, migration("M1", false), migration("M2", false));
        List<Entry> e = c.entries();
        assertEquals(5, e.size()); // 3 base + 2 migrations
        assertEquals(Entry.Kind.SETTINGS, e.get(0).kind());
        assertEquals(Entry.Kind.DB_CONNECTION, e.get(1).kind());
        assertEquals(Entry.Kind.ABOUT, e.get(2).kind());
        assertEquals(Entry.Kind.MIGRATION, e.get(3).kind());
    }

    @Test
    void activateAboutShowsAboutThenBack() {
        DataLinqController c = controller((op, dry, log) -> 0);
        c.setSelected(2); // About
        c.activate();
        assertEquals(Center.ABOUT, c.center());
        c.back();
        assertEquals(Center.OUTPUT, c.center());
    }

    @Test
    void destructiveInExecuteModeAsksConfirmThenRuns() {
        AtomicInteger runs = new AtomicInteger();
        DataLinqController c = controller((op, dry, log) -> { runs.incrementAndGet(); return 3; },
                migration("Reset", true));
        c.toggleDryRun(); // true -> false (execute)
        assertFalse(c.dryRun());
        c.setSelected(3); // the destructive migration
        c.activate();
        assertEquals(Center.CONFIRM, c.center());
        assertNotNull(c.pendingConfirm());
        assertEquals(0, runs.get()); // not run until confirmed
        c.confirmYes();
        assertEquals(1, runs.get());
        assertNull(c.pendingConfirm());
        assertEquals(Center.OUTPUT, c.center());
    }

    @Test
    void confirmNoCancelsWithoutRunning() {
        AtomicInteger runs = new AtomicInteger();
        DataLinqController c = controller((op, dry, log) -> { runs.incrementAndGet(); return 0; },
                migration("Reset", true));
        c.toggleDryRun();
        c.setSelected(3);
        c.activate();
        c.confirmNo();
        assertEquals(0, runs.get());
        assertNull(c.pendingConfirm());
    }

    @Test
    void nonDestructiveRunsImmediately() {
        AtomicInteger runs = new AtomicInteger();
        DataLinqController c = controller((op, dry, log) -> { runs.incrementAndGet(); return 0; },
                migration("M1", false));
        c.setSelected(3);
        c.activate();
        assertEquals(1, runs.get());
        assertNotEquals(Center.CONFIRM, c.center());
    }

    @Test
    void runFailureIsLoggedAndDoesNotThrow() {
        DataLinqController c = controller((op, dry, log) -> { throw new RuntimeException("db down"); },
                migration("M1", false));
        c.setSelected(3);
        c.activate();
        assertTrue(c.output().stream().anyMatch(l -> l.contains("db down")));
        assertFalse(c.running());
    }

    @Test
    void runAllRunsEveryMigration() {
        AtomicInteger runs = new AtomicInteger();
        DataLinqController c = controller((op, dry, log) -> { runs.incrementAndGet(); return 1; },
                migration("M1", false), migration("M2", false), migration("M3", false));
        c.runAllMigrations();
        assertEquals(3, runs.get());
        assertFalse(c.running());
    }
}
