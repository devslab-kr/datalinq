/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.ui;

import kr.devslab.datalinq.core.Operation;
import kr.devslab.datalinq.ui.DataLinqController.Entry;

import dev.tamboui.layout.Constraint;
import dev.tamboui.style.Color;
import dev.tamboui.style.Overflow;
import dev.tamboui.toolkit.app.ToolkitApp;
import dev.tamboui.toolkit.element.Element;
import dev.tamboui.toolkit.elements.ListElement;
import dev.tamboui.toolkit.event.EventResult;
import dev.tamboui.tui.bindings.Actions;
import dev.tamboui.tui.event.KeyCode;
import dev.tamboui.tui.event.KeyEvent;

import java.util.ArrayList;
import java.util.List;

import static dev.tamboui.toolkit.Toolkit.column;
import static dev.tamboui.toolkit.Toolkit.dock;
import static dev.tamboui.toolkit.Toolkit.list;
import static dev.tamboui.toolkit.Toolkit.panel;
import static dev.tamboui.toolkit.Toolkit.text;

/**
 * The View + key dispatch (tamboui MVC): {@link #render()} is a pure function of the
 * {@link DataLinqController} state, and key events are dispatched to controller commands.
 * All behaviour lives in the controller (which is unit-tested); this class is just wiring.
 */
public final class DataLinqApp extends ToolkitApp {

    private final DataLinqController c;
    private final List<String> logo;
    private final List<String> about;
    private final ListElement<?> menu;

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
    protected void onStart() {
        c.init();
        menu.selected(c.selected());
    }

    @Override
    protected Element render() {
        List<String> labels = new ArrayList<>();
        for (Entry e : c.entries()) {
            labels.add(menuLabel(e));
        }

        return dock()
                .top(header())
                .left(panel(menu.items(labels))
                        .title(c.msg().get("menu.migrations"))
                        .rounded()
                        .borderColor(Color.CYAN)
                        .id("menu")
                        .focusable()
                        .onKeyEvent(this::onKey),
                        Constraint.percentage(40))
                .center(centerPanel())
                .bottom(panel(text(" " + c.msg().get("footer.keys") + " ").dim())
                        .rounded()
                        .borderColor(Color.DARK_GRAY));
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

    private Element centerPanel() {
        return switch (c.center()) {
            case ABOUT -> panel(aboutContent())
                    .title(c.msg().get("menu.about")).rounded().borderColor(Color.DARK_GRAY);
            case CONFIRM -> panel(confirmContent())
                    .title(c.msg().get("confirm.title")).rounded().borderColor(Color.RED);
            case OUTPUT -> panel(outputContent())
                    .title("output").rounded().borderColor(Color.DARK_GRAY);
        };
    }

    private Element aboutContent() {
        List<Element> lines = new ArrayList<>();
        for (String l : about) {
            lines.add(text(l).overflow(Overflow.WRAP_WORD));
        }
        return column(lines.toArray(new Element[0]));
    }

    private Element confirmContent() {
        Entry e = c.pendingConfirm();
        String label = e != null ? e.label() : "";
        String confirm = (e != null && !e.operation().confirmText().isEmpty())
                ? e.operation().confirmText()
                : c.msg().get("confirm.destructiveDefault");
        return column(
                text("!  " + label).red().bold(),
                text(""),
                text(confirm).overflow(Overflow.WRAP_WORD),
                text(""),
                text(c.msg().get("confirm.prompt")).yellow());
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

    private String menuLabel(Entry e) {
        if (e.kind() == Entry.Kind.MIGRATION) {
            Operation op = e.operation();
            return op.displayName() + "  [" + op.type() + "]" + (op.destructive() ? "  (!)" : "");
        }
        return "* " + e.label();
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
                    c.activate();
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

    private static boolean isChar(KeyEvent e, String s) {
        return e.code() == KeyCode.CHAR && s.equals(e.string());
    }
}
