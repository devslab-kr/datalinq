/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.ui;

import kr.devslab.datalinq.config.JdbcUrls;
import kr.devslab.datalinq.core.Operation;
import kr.devslab.datalinq.core.OperationType;
import kr.devslab.datalinq.i18n.Messages;
import kr.devslab.datalinq.ui.DataLinqController.Center;
import kr.devslab.datalinq.ui.DataLinqController.Entry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
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

    private static DataLinqController controller(FakeGateway gateway,
                                                 DataLinqController.Runner runner, Operation... ops) {
        DataLinqController c = new DataLinqController(
                MSG, true, 4, () -> List.of(ops), runner, gateway, gateway, true);
        c.init();
        return c;
    }

    /** In-memory gateway for both ports - records save/test calls, no JDBC, no config file. */
    private static final class FakeGateway
            implements DataLinqController.DatasourceGateway, DataLinqController.SettingsGateway {
        final Map<String, Map<String, String>> ds = new LinkedHashMap<>(); // name -> fields
        String defaultSource = "";
        String defaultTarget = "";
        String testResult; // null = success
        int saves;
        int tests;

        // settings state
        String language = "en";
        boolean dryRunDefault = true;
        boolean maskPassword = true;
        int batchSize = 1000;
        int maxParallel = 4;
        String sqlDir = "";
        int settingsSaves;

        FakeGateway with(String name, String url, String user, String pass) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("type", "custom");
            m.put("url", url);
            m.put("username", user);
            m.put("password", pass);
            ds.put(name, m);
            return this;
        }

        private String f(String name, String key) {
            return ds.containsKey(name) ? ds.get(name).getOrDefault(key, "") : "";
        }

        @Override public List<String> names() {
            return new ArrayList<>(ds.keySet());
        }
        @Override public String url(String name) {
            String type = f(name, "type");
            return JdbcUrls.isStructured(type)
                    ? JdbcUrls.build(type, f(name, "host"), f(name, "port"), f(name, "database"))
                    : f(name, "url");
        }
        @Override public String type(String name)     { return f(name, "type"); }
        @Override public String host(String name)     { return f(name, "host"); }
        @Override public String port(String name)     { return f(name, "port"); }
        @Override public String database(String name) { return f(name, "database"); }
        @Override public String username(String name) { return f(name, "username"); }
        @Override public String password(String name) { return f(name, "password"); }
        @Override public String defaultSource() {
            return defaultSource;
        }
        @Override public String defaultTarget() {
            return defaultTarget;
        }
        @Override public void save(String name, String url, String user, String pass,
                                   boolean asDefaultSource, boolean asDefaultTarget) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("type", "custom");
            m.put("url", url);
            m.put("username", user);
            m.put("password", pass);
            ds.put(name, m);
            if (asDefaultSource) {
                defaultSource = name;
            }
            if (asDefaultTarget) {
                defaultTarget = name;
            }
            saves++;
        }
        @Override public void saveStructured(String name, String type, String host, String port,
                                             String database, String user, String pass) {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("type", type);
            m.put("host", host);
            m.put("port", port);
            m.put("database", database);
            m.put("username", user);
            m.put("password", pass);
            ds.put(name, m);
            saves++;
        }
        @Override public void setDefaultSource(String name) {
            defaultSource = name;
        }
        @Override public void setDefaultTarget(String name) {
            defaultTarget = name;
        }
        @Override public void remove(String name) {
            ds.remove(name);
        }
        @Override public String test(String url, String user, String pass) {
            tests++;
            return testResult;
        }

        // ---- SettingsGateway ----
        @Override public String language() {
            return language;
        }
        @Override public boolean dryRunDefault() {
            return dryRunDefault;
        }
        @Override public boolean maskPassword() {
            return maskPassword;
        }
        @Override public int batchSize() {
            return batchSize;
        }
        @Override public int maxParallel() {
            return maxParallel;
        }
        @Override public String sqlDir() {
            return sqlDir;
        }
        @Override public void save(String language, boolean dryRunDefault, boolean maskPassword,
                                   int batchSize, int maxParallel, String sqlDir) {
            this.language = language;
            this.dryRunDefault = dryRunDefault;
            this.maskPassword = maskPassword;
            this.batchSize = batchSize;
            this.maxParallel = maxParallel;
            this.sqlDir = sqlDir;
            settingsSaves++;
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
    void maskPasswordIsTogglableState() {
        DataLinqController c = controller((op, dry, log) -> 0);
        assertTrue(c.maskPassword()); // seeded true by the test helper
        c.setMaskPassword(false);
        assertFalse(c.maskPassword());
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

    @Test
    void savingStructuredDatasourceDerivesTheUrl() {
        FakeGateway gw = new FakeGateway();
        DataLinqController c = controller(gw, (op, dry, log) -> 0);
        c.saveDatasourceStructured("a", "mariadb", "localhost", "3306", "mydb", "root", "pw");
        assertEquals("jdbc:mariadb://localhost:3306/mydb", gw.url("a"));
        assertEquals("mariadb", c.dsType("a"));
        assertEquals("localhost", c.dsHost("a"));
        assertEquals("mydb", c.dsDatabase("a"));
    }

    @Test
    void settingDefaultSourceAndTargetPersistsThroughGateway() {
        FakeGateway gw = new FakeGateway().with("a", "u", "", "").with("b", "u2", "", "");
        DataLinqController c = controller(gw, (op, dry, log) -> 0);
        c.openDbConnection();
        c.setDsIndex(0); // "a"
        c.makeDefaultSource();
        assertEquals("a", gw.defaultSource());
        c.setDsIndex(1); // "b"
        c.makeDefaultTarget();
        assertEquals("b", gw.defaultTarget());
        assertEquals("a", c.defaultSourceName());
        assertEquals("b", c.defaultTargetName());
    }

    // ---- Settings ----

    @Test
    void openingSettingsSeedsFromGatewayAndSwitchesScreen() {
        FakeGateway gw = new FakeGateway();
        gw.language = "ko";
        gw.batchSize = 500;
        gw.sqlDir = "/data/sql";
        DataLinqController c = controller(gw, (op, dry, log) -> 0);
        c.setSelected(0); // Settings is the first base entry
        c.activate();
        assertEquals(DataLinqController.Screen.SETTINGS, c.screen());
        assertEquals("ko", c.setLanguage());
        assertEquals("500", c.setBatchSize());
        assertEquals("/data/sql", c.setSqlDir());
    }

    @Test
    void settingsTogglesLanguageAndEditsTextRows() {
        DataLinqController c = controller((op, dry, log) -> 0);
        c.openSettings();
        c.setSettingsRow(0); // language: en -> ko
        c.settingsToggle();
        assertEquals("ko", c.setLanguage());

        c.setSettingsRow(3); // batch-size: digits only
        c.settingsBackspace(); // clear seeded "1000"
        c.settingsBackspace();
        c.settingsBackspace();
        c.settingsBackspace();
        c.settingsType("x"); // ignored - not a digit
        c.settingsType("2");
        c.settingsType("5");
        assertEquals("25", c.setBatchSize());

        c.setSettingsRow(5); // sql-dir: any char
        c.settingsType("/");
        c.settingsType("a");
        assertEquals("/a", c.setSqlDir());
    }

    @Test
    void savingSettingsPersistsAndAppliesMaskLive() {
        FakeGateway gw = new FakeGateway();
        DataLinqController c = controller(gw, (op, dry, log) -> 0);
        c.openSettings();
        c.setSettingsRow(2); // mask-password
        c.settingsToggle();  // true -> false
        c.saveSettings();
        assertEquals(1, gw.settingsSaves);
        assertFalse(gw.maskPassword);  // persisted
        assertFalse(c.maskPassword()); // applied live
    }

    @Test
    void folderPickerBrowsesIntoASubdirAndSelectsIt(@TempDir Path dir) throws IOException {
        Files.createDirectory(dir.resolve("alpha"));
        Files.createDirectory(dir.resolve("beta"));
        DataLinqController c = controller((op, dry, log) -> 0);
        c.openSettings();
        c.setSettingsRow(5); // sql-dir
        for (char ch : dir.toString().toCharArray()) {
            c.settingsType(String.valueOf(ch)); // seed the picker's start folder
        }
        c.openFolderPicker();
        assertTrue(c.folderPicker());
        assertEquals(".", c.browseEntries().get(0));
        assertEquals("..", c.browseEntries().get(1));
        assertTrue(c.browseEntries().contains("alpha"));

        while (!c.browseEntries().get(c.browseIndex()).equals("alpha")) {
            c.browseDown();
        }
        c.browseActivate(); // descend into alpha
        assertTrue(c.browsePath().endsWith("alpha"));

        while (c.browseIndex() > 0) {
            c.browseUp();
        }
        c.browseActivate(); // "." selects this folder
        assertFalse(c.folderPicker());
        assertTrue(c.setSqlDir().endsWith("alpha"));
    }

    @Test
    void changingSqlDirOnSaveRescans() {
        FakeGateway gw = new FakeGateway();
        AtomicInteger scans = new AtomicInteger();
        DataLinqController c = new DataLinqController(MSG, true, 4,
                () -> { scans.incrementAndGet(); return List.of(); },
                (op, dry, log) -> 0, gw, gw, true);
        c.init(); // first scan
        c.openSettings();
        c.setSettingsRow(5);
        c.settingsType("/");
        c.settingsType("n");
        int before = scans.get();
        c.saveSettings();
        assertTrue(scans.get() > before); // sql-dir changed -> rescan
    }
}
