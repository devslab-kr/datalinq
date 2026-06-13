# Changelog

All notable changes to DataLinq are documented here.
The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project aims to adhere to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

First functional cut: a cross-vendor JDBC data-migration tool with a TamboUI front-end.
Nothing is released to a registry yet; the version is `0.1.0-SNAPSHOT`.

### Added

- **Migration engine** - folder-scanning operation discovery (`sql/NN_Name/`), three
  operation types (ETL row copy, SCRIPT on the target, custom HANDLER), each run in a single
  target transaction (commit on success, rollback on error), **dry-run by default**.
- **Handlers** via `MigrationHandler` + ServiceLoader (no string reflection, GraalVM-friendly),
  with helpers for master/detail splits and generated-key FKs, plus coercing typed `Row`
  accessors (`getLong/getInt/getBigDecimal/getBool`).
- **Parallel batch runner** on virtual threads, bounded by `max-parallel`.
- **`application.yml` config** - named multi-datasource pool (any datasource can be a source or
  target), `defaults.source/target`, per-operation `source=`/`target=` overrides, and options
  (`batch-size`, `dry-run-default`, `language`, `max-parallel`, `mask-password`, `sql-dir`).
- **Structured connections** - datasources configured by `type` + `host`/`port`/`database`
  (sqlserver / mariadb / postgresql), with a `custom` type that keeps a hand-written URL.
- **JDBC drivers** - SQL Server, MariaDB/MySQL and PostgreSQL bundled; others downloadable from
  Maven Central via `datalinq driver <name>` (oracle / mysql / h2 / sqlite) into
  `~/.datalinq/drivers/`. Externally-loaded drivers are registered through a `DriverShim` so the
  JDK `DriverManager` will use them.
- **TamboUI TUI** (MVC; the controller is TUI/DB-free and unit-tested) with a Migrations menu
  (number shortcuts, mouse, run/dry-run/run-all), a **DB Connection** screen (two-pane datasource
  list / structured edit fields, test + save, default source/target, derived-URL preview), a
  **Settings** screen (options + a folder picker for the sql dir, with live menu refresh), and
  About / destructive-confirm dialogs. CJK-aware column alignment throughout.
- **CLI** - `tui` (default), `init`, `list`, `config`, `run <index> [--execute]`, `driver`,
  `i18n`, `logo`.
- **Distribution** - single droppable Shadow fat-jar (`datalinq.jar`) with default resources
  baked in, a jbang alias, and `datalinq init` to materialise editable defaults.
- **i18n** (English / Korean) from external `messages_<lang>.properties` overlaying the bundled
  defaults, and an external brand logo.

### Fixed

- Driver re-download failed on Windows with a cryptic file-lock error: external drivers are now
  loaded only for the commands that open connections (`tui`/`run`), so `driver <name>` can refresh
  an already-downloaded jar. Downloads also report HTTP status clearly and stage to a `.part` file
  before an atomic move (no truncated jars).
- Crash (`NoClassDefFoundError`) when pressing Esc on the main screen.
- DB Connection screen: column misalignment with CJK labels, and editable fields that could not be
  reached / edited (now a two-pane Left/Right navigation model).

### Notes

- `application.yml` holds credentials and is gitignored; copy `application.example.yml`.
- Targets Java 21 bytecode (Gradle toolchain), runs on any JDK 21+.
