/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.ui;

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
import static dev.tamboui.toolkit.Toolkit.stack;
import static dev.tamboui.toolkit.Toolkit.text;

/**
 * The View + input dispatch (tamboui MVC): {@link #render()} is a pure function of the
 * {@link DataLinqController} state. Keyboard, number shortcuts (1-9), and mouse (wheel
 * scroll + click to select/run) all dispatch to controller commands. About and the
 * destructive confirm render as centred dialog popups overlaid (via {@code stack}).
 */
public final class DataLinqApp extends ToolkitApp {

    private final DataLinqController c;
    private final List<String> logo;
    private final List<String> about;
    private final ListElement<?> menu;

    // DB Connection screen view state: a unified ↑↓ cursor over [datasource rows..., url, user,
    // pass]; the three edit buffers; and the last screen, to reseed buffers on entry.
    private final String[] dbBuf = {"", "", ""};
    private int dbCursor;
    private DataLinqController.Screen lastScreen = DataLinqController.Screen.MAIN;

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
        dbCursor = c.dsIndex();
        reseedDbFields();
    }

    private void reseedDbFields() {
        String name = c.selectedDatasource();
        dbBuf[0] = name == null ? "" : nullToEmpty(c.url(name));
        dbBuf[1] = name == null ? "" : nullToEmpty(c.username(name));
        dbBuf[2] = name == null ? "" : nullToEmpty(c.password(name));
    }

    private Element dbScreen() {
        List<String> names = c.datasourceNames();
        Element center;
        if (names.isEmpty()) {
            center = panel(column(text(c.msg().get("db.noDatasources")).yellow()))
                    .title(c.msg().get("db.title")).rounded().borderColor(Color.CYAN);
        } else {
            boolean cursorOnDs = dbCursor < names.size();
            List<Element> dsLines = new ArrayList<>();
            for (int i = 0; i < names.size(); i++) {
                String n = names.get(i);
                String marks = (c.isDefaultSource(n) ? " [S]" : "") + (c.isDefaultTarget(n) ? " [T]" : "");
                boolean here = (i == c.dsIndex());
                var line = text((here ? "> " : "  ") + n + marks);
                dsLines.add(here ? line.yellow().bold() : line.white());
            }
            Element dsPanel = panel(column(dsLines.toArray(new Element[0])))
                    .title(c.msg().get("menu.dbConnection")).rounded()
                    .borderColor(cursorOnDs ? Color.CYAN : Color.DARK_GRAY);

            int field = cursorOnDs ? -1 : dbCursor - names.size();
            Element form = panel(column(
                    fieldRow(c.msg().get("field.url"), dbBuf[0], field == 0),
                    fieldRow(c.msg().get("field.user"), dbBuf[1], field == 1),
                    fieldRow(c.msg().get("field.password"), dbBuf[2], field == 2),
                    text(""),
                    text(c.dbStatus()).yellow()))
                    .title(c.selectedDatasource()).rounded()
                    .borderColor(cursorOnDs ? Color.DARK_GRAY : Color.CYAN);

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

    private Element fieldRow(String label, String value, boolean active) {
        String s = (active ? "> " : "  ") + padRight(label, 9) + " : " + value + (active ? "█" : "");
        var e = text(s);
        return active ? e.yellow().bold() : e.white();
    }

    private EventResult onDbKey(KeyEvent e) {
        if (e.matches(Actions.CANCEL)) { // Esc
            c.back();
            return EventResult.HANDLED;
        }
        List<String> names = c.datasourceNames();
        if (names.isEmpty()) {
            return EventResult.HANDLED; // nothing to edit; only Esc leaves
        }
        if (e.code() == KeyCode.UP) {
            moveDbCursor(dbCursor - 1, names);
            return EventResult.HANDLED;
        }
        if (e.code() == KeyCode.DOWN) {
            moveDbCursor(dbCursor + 1, names);
            return EventResult.HANDLED;
        }
        if (e.code() == KeyCode.F5) {
            doDbTest();
            return EventResult.HANDLED;
        }
        if (e.matches(Actions.SELECT)) { // Enter
            doDbSave();
            return EventResult.HANDLED;
        }
        if (dbCursor >= names.size()) { // editing a field
            int field = dbCursor - names.size();
            if (e.code() == KeyCode.BACKSPACE) {
                if (!dbBuf[field].isEmpty()) {
                    dbBuf[field] = dbBuf[field].substring(0, dbBuf[field].length() - 1);
                }
                return EventResult.HANDLED;
            }
            if (e.code() == KeyCode.CHAR && e.string() != null) {
                dbBuf[field] += e.string();
                return EventResult.HANDLED;
            }
        }
        return EventResult.UNHANDLED;
    }

    private void moveDbCursor(int target, List<String> names) {
        int max = names.size() + 2; // last field row (password)
        dbCursor = Math.max(0, Math.min(max, target));
        if (dbCursor < names.size() && dbCursor != c.dsIndex()) {
            c.setDsIndex(dbCursor);
            reseedDbFields();
        }
    }

    private void doDbTest() {
        String name = c.selectedDatasource();
        if (name == null) {
            return;
        }
        String url = dbBuf[0];
        String user = dbBuf[1];
        String pass = dbBuf[2];
        c.markTesting(name);
        Thread.ofVirtual().start(() -> c.testConnection(name, url, user, pass));
    }

    private void doDbSave() {
        String name = c.selectedDatasource();
        if (name != null) {
            c.saveDatasource(name, dbBuf[0], dbBuf[1], dbBuf[2], false, false);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String padRight(String s, int n) {
        StringBuilder b = new StringBuilder(s);
        while (b.length() < n) {
            b.append(' ');
        }
        return b.toString();
    }

    private static boolean isChar(KeyEvent e, String s) {
        return e.code() == KeyCode.CHAR && s.equals(e.string());
    }
}
