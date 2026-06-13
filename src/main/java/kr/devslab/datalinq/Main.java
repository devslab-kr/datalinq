/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */

package kr.devslab.datalinq;

import kr.devslab.datalinq.config.AppConfig;
import kr.devslab.datalinq.i18n.Messages;
import kr.devslab.datalinq.ui.Logo;
import kr.devslab.datalinq.core.Operation;
import kr.devslab.datalinq.core.OperationScanner;
import kr.devslab.datalinq.engine.MigrationEngine;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * CLI entry point. (A TamboUI menu front-end calls the same engine; this CLI keeps the
 * engine + config runnable/testable on their own.)
 *
 * <pre>
 *   (no args) | list            list discovered operations
 *   config                      show the resolved application.yml (passwords masked)
 *   run &lt;index&gt; [--execute]     run one (default = dry-run; --execute actually writes)
 * </pre>
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        Path projectDir = Paths.get(System.getProperty("user.dir"));
        Path sqlRoot = projectDir.resolve("sql");
        Path configFile = projectDir.resolve("application.yml");

        List<Operation> ops = new OperationScanner(sqlRoot).scan();
        String cmd = args.length > 0 ? args[0] : "list";

        switch (cmd) {
            case "list" -> printList(sqlRoot, ops);
            case "config" -> printConfig(configFile);
            case "i18n" -> printI18n(projectDir, configFile, args);
            case "logo" -> Logo.load(projectDir.resolve("branding/logo.txt")).forEach(System.out::println);
            case "run" -> runOne(ops, configFile, args);
            default -> {
                System.err.println("unknown command: " + cmd);
                System.exit(2);
            }
        }
    }

    private static void printList(Path sqlRoot, List<Operation> ops) {
        System.out.println("Operations under " + sqlRoot + ":");
        if (ops.isEmpty()) {
            System.out.println("  (none - create sql/NN_Name folders)");
        }
        for (int i = 0; i < ops.size(); i++) {
            Operation o = ops.get(i);
            System.out.printf("  [%d] %-28s type=%-7s %s%n",
                    i, o.displayName(), o.type(), o.destructive() ? "(!)" : "");
            if (o.destructive() && !o.confirmText().isEmpty()) {
                System.out.println("        confirm: " + o.confirmText());
            }
        }
        System.out.println();
        System.out.println("run:  ... run <index> [--execute]   (default = dry-run)");
    }

    private static void printConfig(Path configFile) throws Exception {
        AppConfig cfg = AppConfig.load(configFile);
        System.out.println("config: " + configFile);
        System.out.println("  source: " + cfg.sourceUrl()
                + "  user=" + cfg.sourceUsername() + "  pass=" + mask(cfg.sourcePassword()));
        System.out.println("  target: " + cfg.targetUrl()
                + "  user=" + cfg.targetUsername() + "  pass=" + mask(cfg.targetPassword()));
        System.out.println("  options: batch-size=" + cfg.batchSize()
                + "  dry-run-default=" + cfg.dryRunDefault());
    }

    private static void printI18n(Path projectDir, Path configFile, String[] args) throws Exception {
        String lang = args.length > 1 ? args[1] : AppConfig.load(configFile).language();
        Messages m = Messages.load(projectDir.resolve("i18n"), lang);
        System.out.println("language: " + (lang.isBlank()
                ? "(system: " + java.util.Locale.getDefault().getLanguage() + ")" : lang));
        for (String k : new String[]{"menu.settings", "menu.dbConnection", "menu.about",
                "action.test", "confirm.destructiveDefault", "footer.keys"}) {
            System.out.println("  " + k + " = " + m.get(k));
        }
    }

    private static void runOne(List<Operation> ops, Path configFile, String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: run <index> [--execute]");
            System.exit(2);
            return;
        }
        Operation op = ops.get(Integer.parseInt(args[1]));
        boolean dryRun = !(args.length > 2 && args[2].equals("--execute"));
        AppConfig cfg = AppConfig.load(configFile);
        System.out.println("> " + op.displayName() + (dryRun ? "  [dry-run]" : "  [EXECUTE]"));
        new MigrationEngine(cfg).run(op, dryRun, line -> System.out.println("    " + line));
        System.out.println("done.");
    }

    private static String mask(String s) {
        if (s == null || s.isEmpty()) {
            return "(none)";
        }
        return "*".repeat(Math.min(8, s.length()));
    }
}
