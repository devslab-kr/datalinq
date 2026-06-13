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

## Quick start

From nothing to your first migration in five steps. Every block is copy-paste.

### 1. Install

```bash
jbang app install datalinq@devslab-kr/datalinq   # creates the `datalinq` command (jbang provisions a JDK too)
```

> No jbang? Download `datalinq.jar` from the [latest release](https://github.com/devslab-kr/datalinq/releases/latest) and use `java -jar datalinq.jar` wherever this guide says `datalinq`. Needs **JDK 21+**.

### 2. Look around — no database required

```bash
datalinq init     # scaffolds application.example.yml, i18n/, branding/, and a sample sql/ folder here
datalinq list     # lists the migrations discovered under sql/
datalinq          # opens the TUI (arrow keys / number keys to move, q or Esc to quit)
```

### 3. Point it at your databases

`init` wrote `application.example.yml`. Copy it and fill in one source and one target:

```bash
cp application.example.yml application.yml
```

```yaml
datasources:
  my-source:
    type: sqlserver          # sqlserver | mariadb | postgresql   (or: type: custom + a raw url:)
    host: localhost
    port: 1433
    database: SourceDb
    username: sa
    password: "secret"
  my-target:
    type: postgresql
    host: localhost
    port: 5432
    database: TargetDb
    username: postgres
    password: "secret"
defaults:
  source: my-source
  target: my-target
```

```bash
datalinq config   # verify it resolved (passwords are masked)
```

> Need a driver that isn't bundled (Oracle, H2, SQLite, ...)? Run `datalinq driver oracle`, then use `type: custom` + a JDBC `url:` for that datasource.

### 4. Write your first migration

A migration is just a folder under `sql/`. The simplest kind — **ETL** — copies rows from a source query into a target table. The SELECT's **column aliases become the target columns**, so you never hand-write an INSERT:

```bash
mkdir -p sql/01_Customers
```

`sql/01_Customers/source.sql`:

```sql
SELECT customer_id AS id,
       full_name   AS name,
       created_at  AS created
FROM   customers
```

`sql/01_Customers/operation.properties`:

```properties
type=etl
table=customers       # the target table to INSERT into
```

### 5. Run it — dry-run first, always

```bash
datalinq run 0             # DRY-RUN: reads the source, writes nothing (the target transaction is rolled back)
datalinq run 0 --execute   # for real: one transaction, commit on success / rollback on any error
```

Or run it from the TUI: launch `datalinq`, pick the migration, press Enter. Drop more `NN_Name` folders under `sql/` and each becomes another menu item.

<details>
<summary><b>👉 Want to watch it actually move data? A complete, copy-paste demo with Docker — no database of your own needed.</b></summary>

Spins up two throwaway PostgreSQL databases, seeds the source, runs the migration, and prints the copied rows. (Verified end-to-end.)

```bash
# 1. two throwaway Postgres databases (source on 5433, target on 5434)
docker run -d --name dl-src -p 5433:5432 -e POSTGRES_PASSWORD=demo postgres:16-alpine
docker run -d --name dl-tgt -p 5434:5432 -e POSTGRES_PASSWORD=demo postgres:16-alpine
sleep 5

# 2. seed the source; create the empty target table
docker exec dl-src psql -U postgres -c "CREATE TABLE customers(customer_id int, full_name text, created_at timestamp); INSERT INTO customers VALUES (1,'Alice',now()),(2,'Bob',now());"
docker exec dl-tgt psql -U postgres -c "CREATE TABLE customers(id int primary key, name text, created timestamp);"

# 3. a working folder with config + one migration
mkdir -p dl-demo/sql/01_Customers && cd dl-demo
cat > application.yml <<'YAML'
datasources:
  my-source:
    type: postgresql
    host: localhost
    port: 5433
    database: postgres
    username: postgres
    password: demo
  my-target:
    type: postgresql
    host: localhost
    port: 5434
    database: postgres
    username: postgres
    password: demo
defaults:
  source: my-source
  target: my-target
YAML
cat > sql/01_Customers/source.sql <<'SQL'
SELECT customer_id AS id, full_name AS name, created_at AS created FROM customers
SQL
printf 'type=etl\ntable=customers\n' > sql/01_Customers/operation.properties

# 4. run for real, then check the target
datalinq run 0 --execute
docker exec dl-tgt psql -U postgres -c "SELECT * FROM customers ORDER BY id;"   # -> Alice, Bob

# 5. clean up
cd .. && docker rm -f dl-src dl-tgt
```

</details>

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

## Install & run

The default command is the **TUI**; every subcommand is also scriptable.

### With jbang (recommended)

[jbang](https://www.jbang.dev/) installs DataLinq as a real `datalinq` command and will even
provision a JDK for you if none is present:

```bash
jbang app install datalinq@devslab-kr/datalinq   # once - creates the `datalinq` command
datalinq                                          # launch the TUI (Migrations / DB Connection / Settings / About)
datalinq init                                     # write editable defaults (i18n/, branding/, example config, sql/)
datalinq list                                     # list discovered operations
datalinq run 0                                    # dry-run operation #0
datalinq run 0 --execute                          # actually write
```

Or run it once without installing: `jbang datalinq@devslab-kr/datalinq`.

### With the jar (no jbang)

Download `datalinq.jar` from the [latest release](https://github.com/devslab-kr/datalinq/releases/latest)
(needs **JDK 21+**) and run it directly - `java -jar datalinq.jar <command>` is equivalent to
`datalinq <command>`:

```bash
java -jar datalinq.jar              # TUI
java -jar datalinq.jar config       # show resolved config (passwords masked)
java -jar datalinq.jar run 0 --execute
```

During development `./gradlew run --args="..."` works too; `./gradlew shadowJar` builds the jar.

## Status

Engine (scanner / ETL / script / handler / transactions / dry-run), `application.yml` config,
bundled + downloadable JDBC drivers, the CLI, and the **TamboUI TUI** (Migrations, DB Connection,
Settings, About - all calling the same `MigrationEngine`) are in place and verified.
