/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq;

import kr.devslab.datalinq.config.AppConfig;
import kr.devslab.datalinq.core.Operation;
import kr.devslab.datalinq.core.OperationScanner;
import kr.devslab.datalinq.engine.MigrationEngine;
import kr.devslab.datalinq.i18n.Messages;
import kr.devslab.datalinq.ui.Logo;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/**
 * CLI entry point. (A TamboUI menu front-end calls the same engine; this CLI keeps the
 * engine + config runnable/testable on their own.)
 *
 * <pre>
 *   (no args) | list            list discovered operations
 *   config                      show resolved application.yml (passwords masked)
 *   i18n [lang]                 inspect translated UI strings
 *   logo                        print the header logo
 *   run &lt;index&gt; [--execute]     run one (default = dry-run; --execute actually writes)
 * </pre>
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        Path projectDir = Paths.get(System.getProperty("user.dir"));
        Path configFile = projectDir.resolve("application.yml");
        AppConfig config = AppConfig.load(configFile);
        Path sqlRoot = resolveSqlRoot(projectDir, config);

        List<Operation> ops = new OperationScanner(sqlRoot).scan();
        String cmd = args.length > 0 ? args[0] : "list";

        switch (cmd) {
            case "list" -> printList(sqlRoot, ops);
            case "config" -> printConfig(config, sqlRoot);
            case "i18n" -> printI18n(projectDir, config, args);
            case "logo" -> Logo.load(projectDir.resolve("branding/logo.txt")).forEach(System.out::println);
            case "run" -> runOne(ops, config, args);
            default -> {
                System.err.println("unknown command: " + cmd);
                System.exit(2);
            }
        }
    }

    private static Path resolveSqlRoot(Path projectDir, AppConfig config) {
        String dir = config.sqlDir();
        return dir.isBlank() ? projectDir.resolve("sql") : Paths.get(dir);
    }

    private static void printList(Path sqlRoot, List<Operation> ops) {
        System.out.println("Operations under " + sqlRoot + ":");
        if (ops.isEmpty()) {
            System.out.println("  (none - create " + sqlRoot + "/NN_Name folders)");
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

    private static void printConfig(AppConfig cfg, Path sqlRoot) {
        System.out.println("config: " + cfg.file());
        System.out.println("  sql-dir: " + sqlRoot);
        System.out.println("  datasources:");
        for (String name : cfg.datasourceNames()) {
            String mark = (name.equals(cfg.defaultSource()) ? " [default source]" : "")
                    + (name.equals(cfg.defaultTarget()) ? " [default target]" : "");
            System.out.println("    - " + name + ": " + cfg.url(name)
                    + "  user=" + cfg.username(name) + "  pass=" + mask(cfg.password(name)) + mark);
        }
        System.out.println("  defaults: source=" + cfg.defaultSource() + ", target=" + cfg.defaultTarget());
        System.out.println("  options: batch-size=" + cfg.batchSize()
                + ", dry-run-default=" + cfg.dryRunDefault() + ", language=" + cfg.language());
    }

    private static void printI18n(Path projectDir, AppConfig config, String[] args) {
        String lang = args.length > 1 ? args[1] : config.language();
        Messages m = Messages.load(projectDir.resolve("i18n"), lang);
        System.out.println("language: " + (lang.isBlank()
                ? "(system: " + Locale.getDefault().getLanguage() + ")" : lang));
        for (String k : new String[]{"menu.settings", "menu.dbConnection", "menu.about",
                "action.test", "confirm.destructiveDefault", "footer.keys"}) {
            System.out.println("  " + k + " = " + m.get(k));
        }
    }

    private static void runOne(List<Operation> ops, AppConfig config, String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("usage: run <index> [--execute]");
            System.exit(2);
            return;
        }
        Operation op = ops.get(Integer.parseInt(args[1]));
        boolean dryRun = !(args.length > 2 && args[2].equals("--execute"));
        System.out.println("> " + op.displayName() + (dryRun ? "  [dry-run]" : "  [EXECUTE]"));
        new MigrationEngine(config).run(op, dryRun, line -> System.out.println("    " + line));
        System.out.println("done.");
    }

    private static String mask(String s) {
        if (s == null || s.isEmpty()) {
            return "(none)";
        }
        return "*".repeat(Math.min(8, s.length()));
    }
}
