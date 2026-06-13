/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq;

import kr.devslab.datalinq.config.AppConfig;
import kr.devslab.datalinq.config.AppConfigDatasourceGateway;
import kr.devslab.datalinq.config.Drivers;
import kr.devslab.datalinq.core.Operation;
import kr.devslab.datalinq.core.OperationScanner;
import kr.devslab.datalinq.engine.MigrationEngine;
import kr.devslab.datalinq.i18n.Messages;
import kr.devslab.datalinq.ui.DataLinqApp;
import kr.devslab.datalinq.ui.DataLinqController;
import kr.devslab.datalinq.ui.Logo;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Locale;

/**
 * CLI entry point. (A TamboUI menu front-end calls the same engine; this CLI keeps the
 * engine + config runnable/testable on their own.)
 *
 * <pre>
 *   (no args) | tui             launch the TUI
 *   init                        write the bundled defaults (i18n/, branding/, example
 *                               config, sql/ skeleton) into the current folder for editing
 *   list                        list discovered operations
 *   config                      show resolved application.yml (passwords masked)
 *   i18n [lang]                 inspect translated UI strings
 *   logo                        print the header logo
 *   driver [name]               list / download extra JDBC drivers (into ~/.datalinq/drivers)
 *   run &lt;index&gt; [--execute]     run one (default = dry-run; --execute actually writes)
 * </pre>
 */
public final class Main {

    public static void main(String[] args) throws Exception {
        Drivers.loadExternal(); // register any user-provided / downloaded JDBC drivers
        Home home = Home.detect();
        AppConfig config = loadConfig(home);
        Path sqlRoot = resolveSqlRoot(home, config);

        List<Operation> ops = new OperationScanner(sqlRoot).scan();
        String cmd = args.length > 0 ? args[0] : "tui";

        switch (cmd) {
            case "tui" -> launchTui(home, config);
            case "init" -> initScaffold();
            case "list" -> printList(sqlRoot, ops);
            case "config" -> printConfig(config, sqlRoot);
            case "i18n" -> printI18n(home, config, args);
            case "logo" -> Logo.load(home.resolve("branding/logo.txt")).forEach(System.out::println);
            case "driver" -> manageDriver(args);
            case "run" -> runOne(ops, config, args);
            default -> {
                System.err.println("unknown command: " + cmd);
                System.exit(2);
            }
        }
    }

    /**
     * Writes the defaults baked into the jar out to the current folder so they can be edited:
     * {@code i18n/}, {@code branding/logo.txt}, {@code application.example.yml}, and an empty
     * {@code sql/} with a README describing the folder convention. Existing files are kept, so it
     * is safe to re-run. (You do not need this just to run - the jar already carries the defaults;
     * {@code init} only materialises them when you want to customise.)
     */
    private static void initScaffold() throws IOException {
        Path cwd = Paths.get(System.getProperty("user.dir"));
        System.out.println("Scaffolding DataLinq defaults into " + cwd);
        int written = 0;
        written += extract("/i18n/messages_en.properties", cwd, "i18n/messages_en.properties");
        written += extract("/i18n/messages_ko.properties", cwd, "i18n/messages_ko.properties");
        written += extract("/branding/logo.txt", cwd, "branding/logo.txt");
        written += extract("/application.example.yml", cwd, "application.example.yml");
        Path sqlDir = cwd.resolve("sql");
        if (Files.exists(sqlDir.resolve("README.txt"))) {
            System.out.println("  skip     sql/README.txt (exists)");
        } else {
            Files.createDirectories(sqlDir);
            Files.writeString(sqlDir.resolve("README.txt"), SQL_README, StandardCharsets.UTF_8);
            System.out.println("  created  sql/README.txt");
            written++;
        }
        System.out.println();
        if (written == 0) {
            System.out.println("Everything already exists - nothing changed.");
        } else {
            System.out.println("Done (" + written + " file(s)). Copy application.example.yml to "
                    + "application.yml and fill in your DBs, drop migration folders under sql/, then: datalinq");
        }
    }

    private static int extract(String resource, Path baseDir, String relative) throws IOException {
        Path target = baseDir.resolve(relative);
        if (Files.exists(target)) {
            System.out.println("  skip     " + relative + " (exists)");
            return 0;
        }
        try (InputStream in = Main.class.getResourceAsStream(resource)) {
            if (in == null) {
                System.out.println("  missing  " + relative + " (no bundled default)");
                return 0;
            }
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            Files.copy(in, target);
        }
        System.out.println("  created  " + relative);
        return 1;
    }

    private static final String SQL_README = """
            DataLinq migration folders
            ==========================
            Each subfolder here becomes a menu item. Name them NN_Title (the number sets the
            run order, the rest becomes the label): e.g. 01_Customers, 02_Orders.

            sql/NN_Title/
              *.sql                  the statements, run in filename order
              operation.properties   optional metadata (UTF-8); all keys optional:

                description=...       shown in the UI
                source=legacy-erp     source datasource name   (default: defaults.source)
                target=new-core       target datasource name   (default: defaults.target)
                table=customers       target table for simple row-by-row ETL inserts
                handler=...           a MigrationHandler name() for complex transforms
                type=SCRIPT|ETL|HANDLER   usually inferred; set to be explicit
                destructive=true      ask for confirmation before running in execute mode
                confirm=This wipes X. custom confirmation text

            Type is inferred when not set: a handler= means HANDLER; otherwise a source.sql file
            means ETL (migrate rows); otherwise SCRIPT (run the .sql files against the target).
            Datasource names refer to entries in application.yml.
            """;

    /**
     * Config precedence: an existing external {@code application.yml} (edits saved back to it),
     * else an external {@code application.example.yml}, else the bundled example baked into the
     * jar. In every case {@code save()} targets the external {@code application.yml}.
     */
    private static AppConfig loadConfig(Home home) throws IOException {
        Path configFile = home.resolve("application.yml");
        if (Files.exists(configFile)) {
            return AppConfig.load(configFile);
        }
        Path example = home.resolve("application.example.yml");
        if (Files.exists(example)) {
            return AppConfig.load(example, configFile);
        }
        return AppConfig.loadResource("/application.example.yml", configFile);
    }

    private static Path resolveSqlRoot(Home home, AppConfig config) {
        String dir = config.sqlDir();
        return dir.isBlank() ? home.resolve("sql") : Paths.get(dir);
    }

    /**
     * {@code driver} lists the downloadable JDBC drivers; {@code driver <name>} fetches one from
     * Maven Central into {@code ~/.datalinq/drivers/} (loaded on the next launch).
     */
    private static void manageDriver(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("drivers dir: " + Drivers.driversDir());
            System.out.println("downloadable: " + Drivers.CATALOG.keySet());
            System.out.println("usage: driver <name>   (also: drop any driver .jar in the dir)");
            return;
        }
        System.out.println("downloading " + args[1] + " ...");
        Path jar = Drivers.download(args[1]);
        System.out.println("saved:  " + jar);
        System.out.println("(takes effect on the next launch; use it with the Custom type + a JDBC URL)");
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

    private static void printI18n(Home home, AppConfig config, String[] args) {
        String lang = args.length > 1 ? args[1] : config.language();
        Messages m = Messages.load(home.resolve("i18n"), lang);
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

    private static void launchTui(Home home, AppConfig config) throws Exception {
        Messages msg = Messages.load(home.resolve("i18n"), config.language());
        List<String> logo = Logo.load(home.resolve("branding/logo.txt"));
        MigrationEngine engine = new MigrationEngine(config);
        AppConfigDatasourceGateway gateway = new AppConfigDatasourceGateway(config);
        DataLinqController controller = new DataLinqController(
                msg, config.dryRunDefault(), config.maxParallel(),
                // resolve sql-dir on every scan, so changing it in Settings rescans the new folder
                () -> new OperationScanner(resolveSqlRoot(home, config)).scan(),
                engine::run,
                gateway,      // DatasourceGateway
                gateway,      // SettingsGateway
                config.maskPassword());
        new DataLinqApp(controller, logo, aboutLines(msg)).run();
    }

    private static List<String> aboutLines(Messages msg) {
        return List.of(
                "DataLinq  v0.1.0",
                msg.get("app.subtitle"),
                "",
                "Built with TamboUI",
                "https://github.com/tamboui/tamboui",
                "",
                "© 2026 DevsLab Co., Ltd. · 주식회사 데브스랩",
                "https://devslab.kr · support@devslab.kr",
                "Apache-2.0");
    }

    private static String mask(String s) {
        if (s == null || s.isEmpty()) {
            return "(none)";
        }
        return "*".repeat(Math.min(8, s.length()));
    }
}
