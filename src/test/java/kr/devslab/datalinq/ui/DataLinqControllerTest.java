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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the MVC controller - no terminal, no database. */
class DataLinqControllerTest {

    private static final Messages MSG = Messages.load(Path.of("no-such-dir"), "en"); // bundled en defaults

    private static Operation migration(String name, boolean destructive) {
        return new Operation(0, name, name, "", OperationType.SCRIPT, Path.of("."),
                List.of(), "", "", "", "", destructive, "");
    }

    private static DataLinqController controller(DataLinqController.Runner runner, Operation... ops) {
        return controller(new FakeGateway(), runner, ops);
    }

    private static DataLinqController controller(DataLinqController.DatasourceGateway gateway,
                                                 DataLinqController.Runner runner, Operation... ops) {
        DataLinqController c = new DataLinqController(MSG, true, 4, () -> List.of(ops), runner, gateway);
        c.init();
        return c;
    }

    /** In-memory datasource gateway - records save/test calls, no JDBC. */
    private static final class FakeGateway implements DataLinqController.DatasourceGateway {
        final Map<String, String[]> ds = new LinkedHashMap<>(); // name -> [url, user, pass]
        String defaultSource = "";
        String defaultTarget = "";
        String testResult; // null = success
        int saves;
        int tests;

        FakeGateway with(String name, String url, String user, String pass) {
            ds.put(name, new String[]{url, user, pass});
            return this;
        }

        @Override public List<String> names() {
            return new ArrayList<>(ds.keySet());
        }
        @Override public String url(String name) {
            return ds.containsKey(name) ? ds.get(name)[0] : "";
        }
        @Override public String username(String name) {
            return ds.containsKey(name) ? ds.get(name)[1] : "";
        }
        @Override public String password(String name) {
            return ds.containsKey(name) ? ds.get(name)[2] : "";
        }
        @Override public String defaultSource() {
            return defaultSource;
        }
        @Override public String defaultTarget() {
            return defaultTarget;
        }
        @Override public void save(String name, String url, String user, String pass,
                                   boolean asDefaultSource, boolean asDefaultTarget) {
            ds.put(name, new String[]{url, user, pass});
            if (asDefaultSource) {
                defaultSource = name;
            }
            if (asDefaultTarget) {
                defaultTarget = name;
            }
            saves++;
        }
        @Override public void remove(String name) {
            ds.remove(name);
        }
        @Override public String test(String url, String user, String pass) {
            tests++;
            return testResult;
        }
    }

    @Test
    void rescanBuildsBaseMenuPlusMigrations() {
        DataLinqController c = controller((op, dry, log) -> 0, migration("M1", false), migration("M2", false));
        List<Entry> e = c.entries();
        assertEquals(6, e.size()); // 4 base + 2 migrations
        assertEquals(Entry.Kind.SETTINGS, e.get(0).kind());
        assertEquals(Entry.Kind.DB_CONNECTION, e.get(1).kind());
        assertEquals(Entry.Kind.ABOUT, e.get(2).kind());
        assertEquals(Entry.Kind.QUIT, e.get(3).kind());
        assertEquals(Entry.Kind.MIGRATION, e.get(4).kind());
    }

    @Test
    void quitEntryRequestsQuit() {
        DataLinqController c = controller((op, dry, log) -> 0);
        c.setSelected(3); // Quit
        assertFalse(c.quitRequested());
        c.activate();
        assertTrue(c.quitRequested());
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
        c.setSelected(4); // the destructive migration
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
        c.setSelected(4);
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
        c.setSelected(4);
        c.activate();
        assertEquals(1, runs.get());
        assertNotEquals(Center.CONFIRM, c.center());
    }

    @Test
    void runFailureIsLoggedAndDoesNotThrow() {
        DataLinqController c = controller((op, dry, log) -> { throw new RuntimeException("db down"); },
                migration("M1", false));
        c.setSelected(4);
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

    // ---- DB Connection ----

    @Test
    void activatingDbConnectionEntryOpensThatScreen() {
        DataLinqController c = controller((op, dry, log) -> 0);
        assertEquals(DataLinqController.Screen.MAIN, c.screen());
        c.setSelected(1); // DB Connection
        c.activate();
        assertEquals(DataLinqController.Screen.DB_CONNECTION, c.screen());
        c.back();
        assertEquals(DataLinqController.Screen.MAIN, c.screen());
    }

    @Test
    void datasourceSelectionClampsToBounds() {
        FakeGateway gw = new FakeGateway().with("a", "u1", "", "").with("b", "u2", "", "");
        DataLinqController c = controller(gw, (op, dry, log) -> 0);
        c.openDbConnection();
        assertEquals(0, c.dsIndex());
        c.moveDsUp();
        assertEquals(0, c.dsIndex()); // clamped at 0
        c.moveDsDown();
        c.moveDsDown(); // only 2 datasources
        assertEquals(1, c.dsIndex()); // clamped at last
        assertEquals("b", c.selectedDatasource());
    }

    @Test
    void testConnectionSetsOkOrFailStatus() {
        FakeGateway gw = new FakeGateway().with("a", "jdbc:x", "u", "p");
        DataLinqController c = controller(gw, (op, dry, log) -> 0);

        gw.testResult = null; // success
        c.testConnection("a", "jdbc:x", "u", "p");
        assertEquals(1, gw.tests);
        assertTrue(c.dbStatus().contains("a"));

        gw.testResult = "connection refused";
        c.testConnection("a", "jdbc:x", "u", "p");
        assertEquals(2, gw.tests);
        assertTrue(c.dbStatus().contains("connection refused"));
    }

    @Test
    void saveDatasourcePersistsThroughGateway() {
        FakeGateway gw = new FakeGateway().with("a", "old", "u", "p");
        DataLinqController c = controller(gw, (op, dry, log) -> 0);
        c.saveDatasource("a", "jdbc:new", "root", "secret", true, false);
        assertEquals(1, gw.saves);
        assertEquals("jdbc:new", gw.url("a"));
        assertEquals("a", gw.defaultSource());
        assertTrue(c.dbStatus().contains("a"));
    }
}
