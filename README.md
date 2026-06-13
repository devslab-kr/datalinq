# DataLinq

A TUI-driven data migration tool (built with [TamboUI](https://github.com/tamboui/tamboui)).
The **base menus are fixed in code**; the **migration menus are discovered by scanning the
`sql/` folder** - drop a folder, get a menu.

Source = **MS SQL Server**, Target = **MariaDB** (cross-vendor, two connections).

> A devslab asset. Licensed under Apache-2.0.

## Add a migration = drop a folder

```
sql/
├── 01_Approval_Lines/       # ETL: source.sql (aliased SELECT) -> target table
│   ├── source.sql
│   └── operation.properties     ->  type=etl, target=approval_lines
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
| **etl** | `source.sql` + `target=<table>` | read source, auto-INSERT into target. The SELECT's **column aliases = target columns**, so no INSERT is written. | none |
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

```yaml
datasource:
  source: { url: jdbc:sqlserver://..., username: sa,   password: "" }
  target: { url: jdbc:mariadb://...,   username: root, password: "" }
options:
  batch-size: 1000
  dry-run-default: true
```

Copy `application.example.yml` -> `application.yml` (gitignored). It can also be edited from
inside the app (DB Connection screen) and saved.

## Safety

- **dry-run by default** - the target transaction is rolled back, nothing is written.
- `destructive=true` operations require an explicit confirmation before running.
- Each operation runs in **one target transaction**: commit on success, rollback on any error.

## Run (CLI)

```bash
cp application.example.yml application.yml      # fill in MSSQL + MariaDB
./gradlew run --args="list"                     # list operations
./gradlew run --args="config"                   # show resolved config (passwords masked)
./gradlew run --args="run 0"                    # dry-run operation #0
./gradlew run --args="run 0 --execute"          # actually write
# ./gradlew run                                 # TUI menu (wired next)
```

## Status

Engine (scanner / ETL / script / handler / transactions / dry-run) + `application.yml` config
+ CLI are in place and verified. The TamboUI menu front-end (Settings / DB Connection / About +
migrations) is the next layer - it calls the same `MigrationEngine`.
