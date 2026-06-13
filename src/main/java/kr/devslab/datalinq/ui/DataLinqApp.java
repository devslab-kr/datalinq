/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.ui;

import kr.devslab.datalinq.config.JdbcUrls;
import kr.devslab.datalinq.core.Operation;
import kr.devslab.datalinq.ui.DataLinqController.Entry;

import dev.tamboui.layout.Constraint;
import dev.tamboui.layout.Rect;
import dev.tamboui.style.Color;
import dev.tamboui.style.Overflow;
import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.TuiConfig;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;
import dev.tamboui.tui.event.MouseEvent;

import java.util.ArrayList;
import java.util.List;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.dialog;
import static dev.tamboui.toolkit.Toolkit.dock;
import static dev.tamboui.toolkit.Toolkit.list;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.row;
import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;

/**
 * The View + input dispatch (tamboui MVC): {@link #render()} is a pure function of the
 * {@link DataLinqController} state. Keyboard, number shortcuts (1-9), and mouse (wheel
 * scroll + click to select/run) all dispatch to controller commands. About and the
 * destructive confirm render as centred dialog popups overlaid (via {@code stack}).
 */
public final class DataLinqApp extends ToolkitApp {

    /** Minimum underlined width of a DB field value, so empty fields still show an input line. */
    private static final int FIELD_WIDTH = 24;

    private final DataLinqController c;
    private final List<String> logo;
    private final List<String> about;
    private final ListElement<?> menu;

    // DB Connection screen view state: two panes (datasource list / edit fields) switched with
    // Left/Right; Up/Down moves within the active pane. The edit fields are structured - a DB type
    // selector plus host/port/database (or a raw URL for the custom type) and user/password.
    private boolean dbFieldsPane; // false = datasource list active, true = edit fields active
    private int dbField;          // index into the active field set (see dbSlots())
    private String dbType = JdbcUrls.CUSTOM;
    private String dbHost = "";
    private String dbPort = "";
    private String dbDatabase = "";
    private String dbUrl = "";
    private String dbUser = "";
    private String dbPass = "";
    private DataLinqController.Screen lastScreen = DataLinqController.Screen.MAIN;

    /** The editable fields of the DB form, in display order. */
    private enum Slot { TYPE, URL, HOST, PORT, DATABASE, USER, PASSWORD }

    public DataLinqApp(DataLinqController controller, List<String> logo, List<String> about) {
        this.c = controller;
        this.logo = logo;
        this.about = about;
        this.menu = list()
                .highlightSymbol("> ")
                .highlightColor(Color.YELLOW)
                .autoScroll()
                .scrollbar();
    }

    @Override
    protected TuiConfig configure() {
        return TuiConfig.builder().mouseCapture(true).build();
    }

    @Override
    protected void onStart() {
        c.init();
        menu.selected(c.selected());
    }

    @Override
    protected Element render() {
        if (c.screen() != lastScreen) {
            if (c.screen() == DataLinqController.Screen.DB_CONNECTION) {
                enterDbScreen();
            }
            lastScreen = c.screen();
        }
        if (c.screen() == DataLinqController.Screen.DB_CONNECTION) {
            return dbScreen();
        }
        if (c.screen() == DataLinqController.Screen.SETTINGS) {
            return settingsScreen();
        }

        List<Entry> entries = c.entries();
        List<String> labels = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            labels.add(menuLabel(i, entries.get(i)));
        }

        Element base = dock()
                .top(header())
                .left(panel(menu.items(labels))
                        .title(c.msg().get("menu.migrations"))
                        .rounded()
                        .borderColor(Color.CYAN)
                        .id("menu")
                        .focusable()
                        .onKeyEvent(this::onKey)
                        .onMouseEvent(this::onMouse),
                        Constraint.percentage(40))
                .center(panel(outputContent())
                        .title("output")
                        .rounded()
                        .borderColor(Color.DARK_GRAY))
                .bottom(panel(text(" " + c.msg().get("footer.keys", shortcutRange()) + " ").dim())
                        .rounded()
                        .borderColor(Color.DARK_GRAY));

        return switch (c.center()) {
            case ABOUT -> stack(base, aboutDialog());
            case CONFIRM -> stack(base, confirmDialog());
            case OUTPUT -> base;
        };
    }

    private Element header() {
        List<Element> lines = new ArrayList<>();
        for (String l : logo) {
            lines.add(text(l).cyan());
        }
        lines.add(text(""));
        lines.add(text("  " + c.msg().get("app.subtitle") + "    [ " + c.modeLabel() + " ]").bold());
        return panel(column(lines.toArray(new Element[0])))
                .rounded()
                .borderColor(Color.CYAN);
    }

    private Element aboutDialog() {
        return dialog(c.msg().get("menu.about"), aboutContent())
                .rounded()
                .borderColor(Color.CYAN)
                .width(64);
    }

    private Element aboutContent() {
        List<Element> lines = new ArrayList<>();
        for (String l : about) {
            lines.add(text(l).overflow(Overflow.WRAP_WORD));
        }
        return column(lines.toArray(new Element[0]));
    }

    private Element confirmDialog() {
        Entry e = c.pendingConfirm();
        String label = e != null ? e.label() : "";
        String confirm = (e != null && !e.operation().confirmText().isEmpty())
                ? e.operation().confirmText()
                : c.msg().get("confirm.destructiveDefault");
        return dialog(c.msg().get("confirm.title"),
                text("!  " + label).red().bold(),
                text(""),
                text(confirm).overflow(Overflow.WRAP_WORD),
                text(""),
                text(c.msg().get("confirm.prompt")).yellow())
                .rounded()
                .borderColor(Color.RED)
                .width(56);
    }

    private Element outputContent() {
        List<String> out = c.output();
        List<Element> lines = new ArrayList<>();
        int from = Math.max(0, out.size() - 500);
        for (int i = from; i < out.size(); i++) {
            lines.add(text(out.get(i)).overflow(Overflow.WRAP_WORD));
        }
        if (lines.isEmpty()) {
            lines.add(text(c.msg().get("app.subtitle")).dim());
        }
        return column(lines.toArray(new Element[0]));
    }

    /** The digit-shortcut range actually shown, e.g. "1-8" (or "1") - bounded by 9 key bindings. */
    private String shortcutRange() {
        int n = Math.min(9, c.entries().size());
        return n <= 1 ? "1" : "1-" + n;
    }

    private String menuLabel(int index, Entry e) {
        String num = index < 9 ? (index + 1) + "  " : "   ";
        if (e.kind() == Entry.Kind.MIGRATION) {
            Operation op = e.operation();
            return num + op.displayName() + "  [" + op.type() + "]" + (op.destructive() ? "  (!)" : "");
        }
        return num + e.label();
    }

    private EventResult onKey(KeyEvent e) {
        switch (c.center()) {
            case CONFIRM:
                if (isChar(e, "y") || isChar(e, "Y")) {
                    c.confirmYes();
                } else if (isChar(e, "n") || isChar(e, "N") || e.matches(Actions.CANCEL)) {
                    c.confirmNo();
                }
                return EventResult.HANDLED;
            case ABOUT:
                if (e.matches(Actions.SELECT) || e.matches(Actions.CANCEL)) {
                    c.back();
                }
                return EventResult.HANDLED;
            case OUTPUT:
            default:
                if (e.matches(Actions.CANCEL)) { // Esc on the main screen quits cleanly
                    quit();                       // (consume it so the router does not clear focus
                    return EventResult.HANDLED;   //  and trigger a render error on the redraw)
                }
                if (e.matches(Actions.SELECT)) {
                    c.setSelected(menu.selected());
                    activateAndMaybeQuit(menu.selected());
                    return EventResult.HANDLED;
                }
                for (int n = 1; n <= 9; n++) {
                    if (isChar(e, Integer.toString(n))) {
                        if (n - 1 < c.entries().size()) {
                            menu.selected(n - 1);
                            activateAndMaybeQuit(n - 1);
                        }
                        return EventResult.HANDLED;
                    }
                }
                if (isChar(e, "q") || isChar(e, "Q")) {
                    quit();
                    return EventResult.HANDLED;
                }
                if (isChar(e, "d")) {
                    c.toggleDryRun();
                    return EventResult.HANDLED;
                }
                if (isChar(e, "r")) {
                    c.rescan();
                    return EventResult.HANDLED;
                }
                if (isChar(e, "a")) {
                    c.runAllMigrations();
                    return EventResult.HANDLED;
                }
                return EventResult.UNHANDLED; // let the list handle up/down
        }
    }

    private EventResult onMouse(MouseEvent e) {
        if (c.center() == DataLinqController.Center.OUTPUT && e.isPress() && e.isLeftButton()) {
            Rect area = menu.renderedArea();
            if (area != null && area.contains(e.x(), e.y())) {
                int index = e.y() - area.top();
                if (index >= 0 && index < c.entries().size()) {
                    menu.selected(index);
                    activateAndMaybeQuit(index);
                    return EventResult.HANDLED;
                }
            }
        }
        return EventResult.UNHANDLED; // wheel scroll handled by the list
    }

    private void activateAndMaybeQuit(int index) {
        c.activateIndex(index);
        if (c.quitRequested()) {
            quit();
        }
    }

    // ---- DB Connection screen ----

    private void enterDbScreen() {
        dbFieldsPane = false;
        dbField = 0;
        reseedDbFields();
    }

    private void reseedDbFields() {
        String name = c.selectedDatasource();
        if (name == null) {
            dbType = JdbcUrls.CUSTOM;
            dbHost = dbPort = dbDatabase = dbUrl = dbUser = dbPass = "";
            return;
        }
        String type = c.dsType(name);
        dbType = (type == null || type.isEmpty()) ? JdbcUrls.CUSTOM : type;
        dbHost = nullToEmpty(c.dsHost(name));
        dbPort = nullToEmpty(c.dsPort(name));
        dbDatabase = nullToEmpty(c.dsDatabase(name));
        dbUrl = nullToEmpty(c.url(name)); // custom: the raw URL; structured: the derived one (not edited)
        dbUser = nullToEmpty(c.username(name));
        dbPass = nullToEmpty(c.password(name));
        clampDbField();
    }

    /** The visible field slots for the current type (custom shows a raw URL, else host/port/db). */
    private List<Slot> dbSlots() {
        List<Slot> slots = new ArrayList<>();
        slots.add(Slot.TYPE);
        if (JdbcUrls.CUSTOM.equals(dbType)) {
            slots.add(Slot.URL);
        } else {
            slots.add(Slot.HOST);
            slots.add(Slot.PORT);
            slots.add(Slot.DATABASE);
        }
        slots.add(Slot.USER);
        slots.add(Slot.PASSWORD);
        return slots;
    }

    private void clampDbField() {
        int n = dbSlots().size();
        if (dbField >= n) {
            dbField = n - 1;
        }
        if (dbField < 0) {
            dbField = 0;
        }
    }

    private Element dbScreen() {
        List<String> names = c.datasourceNames();
        Element center;
        if (names.isEmpty()) {
            center = panel(column(text(c.msg().get("db.noDatasources")).yellow()))
                    .title(c.msg().get("db.title")).rounded().borderColor(Color.CYAN);
        } else {
            List<Element> dsLines = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                boolean here = (i == c.dsIndex());
                var line = text((here ? "> " : "  ") + names.get(i));
                dsLines.add(here ? line.yellow().bold() : line.white());
            }
            Element nameList = column(dsLines.toArray(new Element[0])).spacing(1);
            Element defaults = column(
                    text(c.msg().get("db.defaultSource") + ": " + orDash(c.defaultSourceName())).cyan(),
                    text(c.msg().get("db.defaultTarget") + ": " + orDash(c.defaultTargetName())).cyan());
            Element dsPanel = panel(column(nameList, text(""), defaults))
                    .title(c.msg().get("menu.dbConnection")).rounded()
                    .borderColor(dbFieldsPane ? Color.DARK_GRAY : Color.CYAN);

            List<Slot> slots = dbSlots();
            int labelWidth = 0;
            for (Slot s : slots) {
                labelWidth = Math.max(labelWidth, TextWidth.of(labelOf(s)));
            }
            List<Element> formRows = new ArrayList<>();
            for (int i = 0; i < slots.size(); i++) {
                formRows.add(slotRow(slots.get(i), labelWidth, dbFieldsPane && dbField == i));
            }
            if (JdbcUrls.isStructured(dbType)) {
                formRows.add(text("URL: " + JdbcUrls.build(dbType, dbHost, dbPort, dbDatabase)).dim());
            }
            formRows.add(text(c.dbStatus()).yellow());
            Element form = panel(column(formRows.toArray(new Element[0])).spacing(1))
                    .title(c.selectedDatasource()).rounded()
                    .borderColor(dbFieldsPane ? Color.CYAN : Color.DARK_GRAY);

            center = dock().left(dsPanel, Constraint.percentage(35)).center(form);
        }

        return dock()
                .top(header())
                .center(center)
                .bottom(panel(text(" " + c.msg().get("db.keys") + " ").dim())
                        .rounded()
                        .borderColor(Color.DARK_GRAY))
                .id("db")
                .focusable()
                .onKeyEvent(this::onDbKey);
    }

    private Element fieldRow(String label, int labelWidth, String value, boolean active, boolean mask) {
        var labelEl = text((active ? "> " : "  ") + TextWidth.pad(label, labelWidth) + " : ");
        String shown = mask ? "•".repeat(value.length()) : value;
        String region = shown + (active ? "█" : "");
        // Pad the underlined span so even an empty value shows an input line.
        while (TextWidth.of(region) < FIELD_WIDTH) {
            region += " ";
        }
        var valueEl = text(region).underlined();
        return active
                ? row(labelEl.yellow().bold(), valueEl.yellow())
                : row(labelEl.white(), valueEl.white());
    }

    private EventResult onDbKey(KeyEvent e) {
        if (e.matches(Actions.CANCEL)) { // Esc -> leave the screen
            c.back();
            return EventResult.HANDLED;
        }
        if (c.datasourceNames().isEmpty()) {
            return EventResult.HANDLED; // nothing to edit; only Esc leaves
        }
        if (e.code() == KeyCode.F5) {
            doDbTest();
            return EventResult.HANDLED;
        }
        if (e.code() == KeyCode.ENTER) { // Enter -> save (not Actions.SELECT, which also binds Space)
            doDbSave();
            return EventResult.HANDLED;
        }
        return dbFieldsPane ? onFieldsPaneKey(e) : onDatasourcePaneKey(e);
    }

    private EventResult onDatasourcePaneKey(KeyEvent e) {
        if (e.code() == KeyCode.UP) {
            c.moveDsUp();
            reseedDbFields();
            return EventResult.HANDLED;
        }
        if (e.code() == KeyCode.DOWN) {
            c.moveDsDown();
            reseedDbFields();
            return EventResult.HANDLED;
        }
        if (e.code() == KeyCode.RIGHT) { // cross into the edit fields
            dbFieldsPane = true;
            dbField = 0;
            return EventResult.HANDLED;
        }
        if (isChar(e, "s") || isChar(e, "S")) { // set selected datasource as the default source
            c.makeDefaultSource();
            return EventResult.HANDLED;
        }
        if (isChar(e, "t") || isChar(e, "T")) { // set selected datasource as the default target
            c.makeDefaultTarget();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private EventResult onFieldsPaneKey(KeyEvent e) {
        if (e.code() == KeyCode.LEFT) { // back to the datasource list
            dbFieldsPane = false;
            return EventResult.HANDLED;
        }
        List<Slot> slots = dbSlots();
        if (e.code() == KeyCode.UP) {
            dbField = Math.max(0, dbField - 1);
            return EventResult.HANDLED;
        }
        if (e.code() == KeyCode.DOWN) {
            dbField = Math.min(slots.size() - 1, dbField + 1);
            return EventResult.HANDLED;
        }
        Slot active = slots.get(Math.min(dbField, slots.size() - 1));
        if (active == Slot.TYPE) {
            if (isSpace(e)) { // cycle the DB type (changes which fields are shown)
                cycleType();
                return EventResult.HANDLED;
            }
            return EventResult.UNHANDLED;
        }
        if (e.code() == KeyCode.BACKSPACE) {
            editSlot(active, "", true);
            return EventResult.HANDLED;
        }
        if (e.code() == KeyCode.CHAR && e.string() != null) {
            editSlot(active, e.string(), false);
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private void cycleType() {
        int i = JdbcUrls.TYPES.indexOf(dbType);
        dbType = JdbcUrls.TYPES.get((i + 1) % JdbcUrls.TYPES.size());
        clampDbField();
    }

    private void editSlot(Slot s, String append, boolean backspace) {
        switch (s) {
            case URL -> dbUrl = edit(dbUrl, append, backspace);
            case HOST -> dbHost = edit(dbHost, append, backspace);
            case PORT -> dbPort = edit(dbPort, append, backspace);
            case DATABASE -> dbDatabase = edit(dbDatabase, append, backspace);
            case USER -> dbUser = edit(dbUser, append, backspace);
            case PASSWORD -> dbPass = edit(dbPass, append, backspace);
            case TYPE -> { /* selector, not text-editable */ }
        }
    }

    private static String edit(String current, String append, boolean backspace) {
        if (backspace) {
            return current.isEmpty() ? current : current.substring(0, current.length() - 1);
        }
        return current + append;
    }

    private String labelOf(Slot s) {
        return switch (s) {
            case TYPE -> c.msg().get("field.type");
            case URL -> c.msg().get("field.url");
            case HOST -> c.msg().get("field.host");
            case PORT -> c.msg().get("field.port");
            case DATABASE -> c.msg().get("field.database");
            case USER -> c.msg().get("field.user");
            case PASSWORD -> c.msg().get("field.password");
        };
    }

    private static String typeLabel(String type) {
        return switch (type) {
            case JdbcUrls.SQLSERVER -> "MS SQL Server";
            case JdbcUrls.MARIADB -> "MariaDB / MySQL";
            default -> "Custom (URL)";
        };
    }

    private String slotValue(Slot s) {
        return switch (s) {
            case TYPE -> dbType;
            case URL -> dbUrl;
            case HOST -> dbHost;
            case PORT -> dbPort;
            case DATABASE -> dbDatabase;
            case USER -> dbUser;
            case PASSWORD -> dbPass;
        };
    }

    private Element slotRow(Slot s, int labelWidth, boolean active) {
        if (s == Slot.TYPE) {
            var labelEl = text((active ? "> " : "  ") + TextWidth.pad(labelOf(s), labelWidth) + " : ");
            var valueEl = text("◀ " + typeLabel(dbType) + " ▶"); // ◀ type ▶
            return active ? row(labelEl.yellow().bold(), valueEl.yellow())
                    : row(labelEl.white(), valueEl.white());
        }
        boolean mask = (s == Slot.PASSWORD) && c.maskPassword();
        return fieldRow(labelOf(s), labelWidth, slotValue(s), active, mask);
    }

    private void doDbTest() {
        String name = c.selectedDatasource();
        if (name == null) {
            return;
        }
        String url = JdbcUrls.CUSTOM.equals(dbType)
                ? dbUrl : JdbcUrls.build(dbType, dbHost, dbPort, dbDatabase);
        String user = dbUser;
        String pass = dbPass;
        c.markTesting(name);
        Thread.ofVirtual().start(() -> c.testConnection(name, url, user, pass));
    }

    private void doDbSave() {
        String name = c.selectedDatasource();
        if (name == null) {
            return;
        }
        if (JdbcUrls.CUSTOM.equals(dbType)) {
            c.saveDatasource(name, dbUrl, dbUser, dbPass, false, false);
        } else {
            c.saveDatasourceStructured(name, dbType, dbHost, dbPort, dbDatabase, dbUser, dbPass);
        }
    }

    // ---- Settings screen ----

    private Element settingsScreen() {
        Element center = c.folderPicker() ? folderPickerPanel() : settingsForm();
        String keys = c.folderPicker() ? c.msg().get("picker.keys") : c.msg().get("settings.keys");
        return dock()
                .top(header())
                .center(center)
                .bottom(panel(text(" " + keys + " ").dim())
                        .rounded()
                        .borderColor(Color.DARK_GRAY))
                .id("settings")
                .focusable()
                .onKeyEvent(this::onSettingsKey);
    }

    private Element settingsForm() {
        int active = c.settingsRow();
        String[] labels = {
                c.msg().get("field.language"),
                c.msg().get("field.dryRun"),
                c.msg().get("field.maskPassword"),
                c.msg().get("field.batchSize"),
                c.msg().get("field.maxParallel"),
                c.msg().get("field.sqlDir"),
        };
        int w = 0;
        for (String l : labels) {
            w = Math.max(w, TextWidth.of(l));
        }
        String sqlDir = c.setSqlDir().isEmpty() ? c.msg().get("field.sqlDirDefault") : c.setSqlDir();
        // One blank line between the toggle group and the value group - lighter than a gap on
        // every row, but enough to keep the rows from looking cramped.
        return panel(column(
                settingRow(labels[0], w, languageLabel(c.setLanguage()), false, active == 0),
                settingRow(labels[1], w, boolLabel(c.setDryRun()), false, active == 1),
                settingRow(labels[2], w, boolLabel(c.setMask()), false, active == 2),
                text(""),
                settingRow(labels[3], w, c.setBatchSize(), true, active == 3),
                settingRow(labels[4], w, c.setMaxParallel(), true, active == 4),
                settingRow(labels[5], w, sqlDir, false, active == 5),
                text(""),
                text(c.msg().get("settings.restartNote")).dim(),
                text(c.settingsStatus()).yellow()))
                .title(c.msg().get("settings.title"))
                .rounded()
                .borderColor(Color.CYAN);
    }

    private Element folderPickerPanel() {
        List<String> entries = c.browseEntries();
        int idx = c.browseIndex();
        List<Element> lines = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            String e = entries.get(i);
            String label = ".".equals(e) ? c.msg().get("picker.selectThis")
                    : "..".equals(e) ? ".." : e + "/";
            var line = text((i == idx ? "> " : "  ") + label);
            lines.add(i == idx ? line.yellow().bold() : line.white());
        }
        return panel(column(lines.toArray(new Element[0])))
                .title(c.browsePath())
                .rounded()
                .borderColor(Color.CYAN);
    }

    private Element settingRow(String label, int labelWidth, String value, boolean underline, boolean active) {
        var labelEl = text((active ? "> " : "  ") + TextWidth.pad(label, labelWidth) + " : ");
        String shown = value;
        if (underline) {
            shown = value + (active ? "█" : "");
            while (TextWidth.of(shown) < FIELD_WIDTH) {
                shown += " ";
            }
        }
        var valueEl = underline ? text(shown).underlined() : text(shown);
        return active
                ? row(labelEl.yellow().bold(), valueEl.yellow())
                : row(labelEl.white(), valueEl.white());
    }

    private EventResult onSettingsKey(KeyEvent e) {
        if (c.folderPicker()) {
            return onPickerKey(e);
        }
        if (e.matches(Actions.CANCEL)) { // Esc -> leave
            c.back();
            return EventResult.HANDLED;
        }
        if (e.code() == KeyCode.UP) {
            c.settingsUp();
            return EventResult.HANDLED;
        }
        if (e.code() == KeyCode.DOWN) {
            c.settingsDown();
            return EventResult.HANDLED;
        }
        if (e.code() == KeyCode.ENTER) { // save (Enter only - Actions.SELECT also binds Space)
            c.saveSettings();
            return EventResult.HANDLED;
        }
        int row = c.settingsRow();
        if (row <= 2) { // language / dry-run / mask -> toggle with Space or Left/Right
            if (e.code() == KeyCode.LEFT || e.code() == KeyCode.RIGHT || isSpace(e)) {
                c.settingsToggle();
                return EventResult.HANDLED;
            }
            return EventResult.UNHANDLED;
        }
        if (row == 5 && isSpace(e)) { // sql-dir: Space opens the folder picker
            c.openFolderPicker();
            return EventResult.HANDLED;
        }
        // text rows: batch-size / max-parallel (digits) / sql-dir (manual entry)
        if (e.code() == KeyCode.BACKSPACE) {
            c.settingsBackspace();
            return EventResult.HANDLED;
        }
        if (e.code() == KeyCode.CHAR && e.string() != null) {
            c.settingsType(e.string());
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private EventResult onPickerKey(KeyEvent e) {
        if (e.matches(Actions.CANCEL)) {
            c.closeFolderPicker();
            return EventResult.HANDLED;
        }
        if (e.code() == KeyCode.UP) {
            c.browseUp();
            return EventResult.HANDLED;
        }
        if (e.code() == KeyCode.DOWN) {
            c.browseDown();
            return EventResult.HANDLED;
        }
        if (e.code() == KeyCode.ENTER) {
            c.browseActivate();
            return EventResult.HANDLED;
        }
        return EventResult.UNHANDLED;
    }

    private static boolean isSpace(KeyEvent e) {
        return e.code() == KeyCode.CHAR && " ".equals(e.string());
    }

    private static String boolLabel(boolean on) {
        return on ? "[x]" : "[ ]";
    }

    private String languageLabel(String lang) {
        return (lang == null || lang.isEmpty()) ? "(system)" : lang;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String orDash(String s) {
        return (s == null || s.isEmpty()) ? "-" : s;
    }

    private static boolean isChar(KeyEvent e, String s) {
        return e.code() == KeyCode.CHAR && s.equals(e.string());
    }
}
