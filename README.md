# DataLinq

**English** | [한국어](README.ko.md)

A TUI-driven data migration tool (built with [TamboUI](https://github.com/tamboui/tamboui)).
The **base menus are fixed in code**; the **migration menus are discovered by scanning the
`sql/` folder** - drop a folder, get a menu.

Moves rows **between any two JDBC databases** (cross-vendor, source + target). SQL Server,
MariaDB/MySQL and PostgreSQL drivers are **bundled**; others (Oracle, H2, SQLite, ...) are a
one-line `driver` download away. Connections are configured by **DB type + host/port/database**
(or a raw URL), in `application.yml` or live from the DB Connection screen.

> By **DevsLab Co., Ltd.** (주식회사 데브스랩) · https://devslab.kr · Apache-2.0

## Add a migration = drop a folder

```
sql/
├── 01_Approval_Lines/       # ETL: source.sql (aliased SELECT) -> target table
│   ├── source.sql
│   └── operation.properties     ->  type=etl, table=approval_lines
├── 03_Reset_Base_Data/      # SCRIPT: run .sql on the TARGET (reset/delete)
│   ├── 01_reset.sql
│   └── operation.properties     ->  type=script, destructive=true
└── 05_Orders_With_Items/    # HANDLER: custom code (master/detail split)
    ├── source.sql
    └── operation.properties     ->  handler=orders
```

- Folder `NN_Some_Name` -> order `NN`, menu label `Some Name`.
- `operation.properties` is optional; a `source.sql` folder defaults to ETL, otherwise SCRIPT.

## Three operation types

| type | folder shape | what it does | code? |
|------|--------------|--------------|-------|
| **etl** | `source.sql` + `table=<table>` | read source, auto-INSERT into target. The SELECT's **column aliases = target columns**, so no INSERT is written. | none |
| **script** | one or more `.sql` | run them against the **target** (resets, deletes). | none |
| **handler** | `source.sql` + `handler=<name>` | a `MigrationHandler` (transforms / master-detail / generated-key FKs), discovered by ServiceLoader. | ~20 lines |

### Handler (when one result set becomes several tables)

```java
public class OrdersMigration extends MigrationHandler {
    public String name() { return "orders"; }     // referenced as handler=orders
    public void migrate() throws Exception {
        var rows = query("source.sql");            // flat result set
        // group by order_no -> insert master (orders), get generated id -> insert details
        long orderId = insert("orders", values("order_no", ..., "customer_id", ...));
        insert("order_items", values("order_id", orderId, "product_id", ..., "qty", ...));
    }
}
```

Handlers are resolved by **ServiceLoader** (registered in
`META-INF/services/kr.devslab.datalinq.engine.MigrationHandler`), not string reflection -
so a GraalVM native image stays possible.

## Configuration (`application.yml`)

Multiple **named datasources** - any can be a source or a target. Operations pick by name,
falling back to `defaults`:

```yaml
datasources:
  # Structured types build the JDBC URL from host/port/database:
  legacy-erp:                      # source
    type: sqlserver                # sqlserver | mariadb | postgresql
    host: SRC_HOST
    port: 1433
    database: SRC_DB
    username: sa
    password: ""
  new-core:                        # target (mariadb also connects to MySQL servers)
    type: mariadb
    host: TGT_HOST
    port: 3306
    database: TGT_DB
    username: root
    password: ""
  # custom type keeps a hand-written URL verbatim (use this for downloaded drivers):
  # warehouse:
  #   type: custom
  #   url: jdbc:oracle:thin:@HOST:1521:ORCL
  #   username: app
  #   password: ""
defaults:
  source: legacy-erp               # used when an operation does not set source=
  target: new-core                 # used when an operation does not set target=
options:
  batch-size: 1000
  dry-run-default: true
  language: en                     # en | ko  (blank = system locale)
  max-parallel: 4                  # max operations run concurrently (bounds DB connections)
  mask-password: true              # DB Connection screen: mask passwords (false = show plain)
  # sql-dir: /path/to/sql          # external migration folder (blank = ./sql)
```

An operation can override per run with `source=<name>` / `target=<name>` in its
operation.properties. Copy `application.example.yml` -> `application.yml` (gitignored); it can
also be edited from inside the app (DB Connection screen) and saved.

## Database drivers

- **Bundled** (always work): SQL Server, MariaDB / MySQL, PostgreSQL.
- **Downloadable** into `~/.datalinq/drivers/` from Maven Central, loaded on the next launch:

  ```bash
  datalinq driver               # list what is downloadable
  datalinq driver oracle        # fetch one (oracle | mysql | h2 | sqlite | postgresql)
  ```

  You can also just drop any JDBC driver `.jar` into that folder by hand. Externally-loaded
  drivers are registered through a `DriverShim` (the JDK's `DriverManager` ignores drivers from
  a foreign class loader otherwise). Use them with the **custom** type + a hand-written URL.

## Safety

- **dry-run by default** - the target transaction is rolled back, nothing is written.
- `destructive=true` operations require an explicit confirmation before running.
- Each operation runs in **one target transaction**: commit on success, rollback on any error.

## Run

The default command is the **TUI**; everything is also scriptable from the CLI.

```bash
./gradlew shadowJar                             # build the self-contained jar
java -jar build/libs/datalinq.jar               # TUI menu (Migrations / DB Connection / Settings / About)

# or via the CLI:
java -jar build/libs/datalinq.jar init          # write editable defaults (i18n/, branding/, example config, sql/)
java -jar build/libs/datalinq.jar list          # list discovered operations
java -jar build/libs/datalinq.jar config        # show resolved config (passwords masked)
java -jar build/libs/datalinq.jar run 0         # dry-run operation #0
java -jar build/libs/datalinq.jar run 0 --execute   # actually write
```

The jar is a single droppable artifact (Shadow fat-jar, with a [jbang](https://www.jbang.dev/)
alias in `jbang-catalog.json`). During development `./gradlew run --args="..."` works too.

## Status

Engine (scanner / ETL / script / handler / transactions / dry-run), `application.yml` config,
bundled + downloadable JDBC drivers, the CLI, and the **TamboUI TUI** (Migrations, DB Connection,
Settings, About - all calling the same `MigrationEngine`) are in place and verified.
