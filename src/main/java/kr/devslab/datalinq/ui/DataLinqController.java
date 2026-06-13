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

    /** The active full screen. MAIN is the menu + output; others are dedicated editors. */
    public enum Screen { MAIN, DB_CONNECTION, SETTINGS }

    /** The number of editable rows on the Settings screen. */
    public static final int SETTINGS_ROWS = 6;

    /** What the centre panel shows on the MAIN screen. */
    public enum Center { OUTPUT, ABOUT, CONFIRM }

    /**
     * Port for reading / testing / persisting datasources, kept TUI- and DB-free so the
     * controller stays unit-testable (prod: backed by AppConfig + DriverManager; test: a fake).
     */
    public interface DatasourceGateway {
        List<String> names();

        String url(String name);

        String username(String name);

        String password(String name);

        String defaultSource();

        String defaultTarget();

        void save(String name, String url, String username, String password,
                  boolean asDefaultSource, boolean asDefaultTarget) throws Exception;

        void remove(String name) throws Exception;

        /** Attempts a connection; returns {@code null} on success, otherwise an error message. */
        String test(String url, String username, String password);
    }

    /**
     * Port for reading / persisting the {@code options.*} settings, kept TUI- and config-free so
     * the controller stays unit-testable (prod: backed by AppConfig; test: a fake).
     */
    public interface SettingsGateway {
        String language();

        boolean dryRunDefault();

        boolean maskPassword();

        int batchSize();

        int maxParallel();

        String sqlDir();

        void save(String language, boolean dryRunDefault, boolean maskPassword,
                  int batchSize, int maxParallel, String sqlDir) throws Exception;
    }

    /** A menu entry: a fixed base action, or a discovered migration. */
    public record Entry(Kind kind, String label, Operation operation) {
        public enum Kind { SETTINGS, DB_CONNECTION, ABOUT, QUIT, MIGRATION }
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
    private final DatasourceGateway datasources;
    private final SettingsGateway settings;

    private final List<Entry> entries = new ArrayList<>();
    private final List<String> output = Collections.synchronizedList(new ArrayList<>());
    private int selected;
    private boolean dryRun;
    private Screen screen = Screen.MAIN;
    private Center center = Center.OUTPUT;
    private Entry pendingConfirm;
    private volatile boolean running;
    private boolean quitRequested;

    // DB Connection screen state
    private int dsIndex;
    private volatile String dbStatus = "";
    private boolean maskPassword;

    // Settings screen state (edit buffers seeded from the gateway on entry)
    private int settingsRow;
    private String setLanguage = "";
    private boolean setDryRun;
    private boolean setMask;
    private String setBatchSize = "";
    private String setMaxParallel = "";
    private String setSqlDir = "";
    private String settingsStatus = "";

    public DataLinqController(Messages msg, boolean dryRunDefault, int maxParallel,
                              OperationProvider provider, Runner runner, DatasourceGateway datasources,
                              SettingsGateway settings, boolean maskPassword) {
        this.msg = msg;
        this.dryRun = dryRunDefault;
        this.maxParallel = Math.max(1, maxParallel);
        this.provider = provider;
        this.runner = runner;
        this.datasources = datasources;
        this.settings = settings;
        this.maskPassword = maskPassword;
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

    public Screen screen() {
        return screen;
    }

    public Center center() {
        return center;
    }

    // ---- DB Connection queries ----

    public List<String> datasourceNames() {
        return datasources.names();
    }

    public int dsIndex() {
        return dsIndex;
    }

    /** The currently selected datasource name, or null if there are none. */
    public String selectedDatasource() {
        List<String> names = datasources.names();
        return (dsIndex >= 0 && dsIndex < names.size()) ? names.get(dsIndex) : null;
    }

    public String url(String name)      { return datasources.url(name); }
    public String username(String name) { return datasources.username(name); }
    public String password(String name) { return datasources.password(name); }
    public boolean isDefaultSource(String name) { return name != null && name.equals(datasources.defaultSource()); }
    public boolean isDefaultTarget(String name) { return name != null && name.equals(datasources.defaultTarget()); }

    /** Status line for the DB Connection screen (test / save result); volatile - set off-thread. */
    public String dbStatus() {
        return dbStatus;
    }

    /** Whether the DB Connection screen masks password values (a setting; editable in Settings). */
    public boolean maskPassword() {
        return maskPassword;
    }

    public void setMaskPassword(boolean mask) {
        this.maskPassword = mask;
    }

    // ---- Settings queries ----

    public int settingsRow()       { return settingsRow; }
    public String setLanguage()    { return setLanguage; }
    public boolean setDryRun()     { return setDryRun; }
    public boolean setMask()       { return setMask; }
    public String setBatchSize()   { return setBatchSize; }
    public String setMaxParallel() { return setMaxParallel; }
    public String setSqlDir()      { return setSqlDir; }
    public String settingsStatus() { return settingsStatus; }

    public Entry pendingConfirm() {
        return pendingConfirm;
    }

    public boolean running() {
        return running;
    }

    /** Set when the user activates the Quit entry; the View calls quit() and clears the UI. */
    public boolean quitRequested() {
        return quitRequested;
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
        entries.add(new Entry(Entry.Kind.QUIT, msg.get("menu.quit"), null));
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
            case QUIT -> quitRequested = true;
            case DB_CONNECTION -> openDbConnection();
            case SETTINGS -> openSettings();
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

    /** Select an entry by index and activate it (number shortcut / mouse click). */
    public void activateIndex(int index) {
        setSelected(index);
        activate();
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
        if (screen == Screen.DB_CONNECTION || screen == Screen.SETTINGS) {
            screen = Screen.MAIN;
            center = Center.OUTPUT;
            return;
        }
        center = Center.OUTPUT;
        pendingConfirm = null;
    }

    // ---- DB Connection commands ----

    public void openDbConnection() {
        screen = Screen.DB_CONNECTION;
        dsIndex = 0;
        dbStatus = "";
    }

    public void closeDbConnection() {
        screen = Screen.MAIN;
        center = Center.OUTPUT;
    }

    public void setDsIndex(int index) {
        int count = datasources.names().size();
        if (count == 0) {
            dsIndex = 0;
        } else if (index < 0) {
            dsIndex = 0;
        } else if (index >= count) {
            dsIndex = count - 1;
        } else {
            dsIndex = index;
        }
    }

    public void moveDsUp() {
        setDsIndex(dsIndex - 1);
    }

    public void moveDsDown() {
        setDsIndex(dsIndex + 1);
    }

    /** Sets a transient "testing ..." status before an async {@link #testConnection}. */
    public void markTesting(String name) {
        dbStatus = msg.get("status.testing", name);
    }

    /** Tests a connection with the given (possibly edited) values; updates {@link #dbStatus()}. */
    public void testConnection(String name, String url, String username, String password) {
        String error = datasources.test(url, username, password);
        dbStatus = (error == null)
                ? msg.get("status.connectionOk", name)
                : msg.get("status.connectionFail", name, error);
        log(dbStatus);
    }

    /** Persists a datasource (optionally as default source/target); updates {@link #dbStatus()}. */
    public void saveDatasource(String name, String url, String username, String password,
                               boolean asDefaultSource, boolean asDefaultTarget) {
        try {
            datasources.save(name, url, username, password, asDefaultSource, asDefaultTarget);
            dbStatus = msg.get("status.saved", name);
        } catch (Exception e) {
            dbStatus = msg.get("status.saveFailed", name, e.getMessage());
        }
        log(dbStatus);
    }

    // ---- Settings commands ----

    public void openSettings() {
        screen = Screen.SETTINGS;
        settingsRow = 0;
        settingsStatus = "";
        setLanguage = settings.language();
        setDryRun = settings.dryRunDefault();
        setMask = settings.maskPassword();
        setBatchSize = Integer.toString(settings.batchSize());
        setMaxParallel = Integer.toString(settings.maxParallel());
        setSqlDir = settings.sqlDir();
    }

    public void setSettingsRow(int row) {
        settingsRow = Math.max(0, Math.min(SETTINGS_ROWS - 1, row));
    }

    public void settingsUp() {
        setSettingsRow(settingsRow - 1);
    }

    public void settingsDown() {
        setSettingsRow(settingsRow + 1);
    }

    /** Toggles the active boolean/choice row (language cycle, dry-run, mask); no-op on text rows. */
    public void settingsToggle() {
        switch (settingsRow) {
            case 0 -> setLanguage = cycleLanguage(setLanguage);
            case 1 -> setDryRun = !setDryRun;
            case 2 -> setMask = !setMask;
            default -> { /* text rows are edited by typing */ }
        }
    }

    /** Appends a typed character to the active text row (digits only for the numeric rows). */
    public void settingsType(String ch) {
        if (ch == null || ch.isEmpty()) {
            return;
        }
        switch (settingsRow) {
            case 3 -> { if (isDigit(ch)) setBatchSize += ch; }
            case 4 -> { if (isDigit(ch)) setMaxParallel += ch; }
            case 5 -> setSqlDir += ch;
            default -> { /* boolean/choice rows ignore typing */ }
        }
    }

    public void settingsBackspace() {
        switch (settingsRow) {
            case 3 -> setBatchSize = chop(setBatchSize);
            case 4 -> setMaxParallel = chop(setMaxParallel);
            case 5 -> setSqlDir = chop(setSqlDir);
            default -> { /* nothing to delete on boolean/choice rows */ }
        }
    }

    /**
     * Persists the edited options. mask-password applies live; a changed sql-dir triggers a
     * rescan so the menu reflects the new folder immediately. language/batch-size/max-parallel
     * take effect on the next launch.
     */
    public void saveSettings() {
        int batch = parseIntOr(setBatchSize, settings.batchSize());
        int parallel = Math.max(1, parseIntOr(setMaxParallel, settings.maxParallel()));
        String previousSqlDir = settings.sqlDir();
        try {
            settings.save(setLanguage, setDryRun, setMask, batch, parallel, setSqlDir);
            setBatchSize = Integer.toString(batch);
            setMaxParallel = Integer.toString(parallel);
            maskPassword = setMask; // live
            settingsStatus = msg.get("status.saved", "settings");
            if (!setSqlDir.equals(previousSqlDir)) {
                rescan(); // the menu reflects the new sql folder right away
            }
        } catch (Exception e) {
            settingsStatus = msg.get("status.saveFailed", "settings", e.getMessage());
        }
    }

    private static String cycleLanguage(String current) {
        // en -> ko -> (blank = system) -> en
        if ("en".equals(current)) {
            return "ko";
        }
        if ("ko".equals(current)) {
            return "";
        }
        return "en";
    }

    private static boolean isDigit(String ch) {
        return ch.length() == 1 && Character.isDigit(ch.charAt(0));
    }

    private static String chop(String s) {
        return s.isEmpty() ? s : s.substring(0, s.length() - 1);
    }

    private static int parseIntOr(String s, int fallback) {
        try {
            return Integer.parseInt(s.trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
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
