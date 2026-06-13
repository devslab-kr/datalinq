/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.engine;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for custom migrations: transforms, derived rows, lookups, or one source result
 * set split into several target tables (master/detail and beyond). It is just plain Java -
 * there is no complexity ceiling; the helpers below keep complex handlers readable.
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} (so it stays GraalVM
 * native-image friendly - no string-driven reflection). To add one:
 * <ol>
 *   <li>subclass this, give it a unique {@link #name()} and implement {@link #migrate()};</li>
 *   <li>register it in {@code META-INF/services/kr.devslab.datalinq.engine.MigrationHandler};</li>
 *   <li>reference it from a folder's operation.properties: {@code handler=<name>}.</li>
 * </ol>
 *
 * <p>Reads come from the SOURCE (or any named datasource via {@link #queryFrom}); writes go
 * to the TARGET and honor dry-run. The engine wraps the whole run in one target transaction.
 */
public abstract class MigrationHandler {

    /** Injected by the engine before {@link #migrate()} runs. */
    protected MigrationContext ctx;

    /** Unique key referenced from operation.properties as {@code handler=<name>}. */
    public abstract String name();

    public final void init(MigrationContext context) {
        this.ctx = context;
    }

    /** Implement the migration logic here. */
    public abstract void migrate() throws Exception;

    // ---- reads ----

    /** Runs a SELECT (a .sql file in this folder) against the SOURCE; returns all rows. */
    protected List<Row> query(String sqlFileName) throws Exception {
        return runQuery(ctx.source(), ctx.sql(sqlFileName));
    }

    /** Runs an arbitrary SELECT against a NAMED datasource (for joining a second source). */
    protected List<Row> queryFrom(String datasource, String sql) throws SQLException {
        return runQuery(ctx.connection(datasource), sql);
    }

    /**
     * Loads a two-column {@code SELECT key, value} from a named datasource into a map -
     * handy for code/reference lookups (e.g. natural code -> surrogate id).
     */
    protected Map<String, Object> lookup(String datasource, String sql) throws SQLException {
        Map<String, Object> map = new LinkedHashMap<>();
        try (Statement st = ctx.connection(datasource).createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                map.put(rs.getString(1), rs.getObject(2));
            }
        }
        return map;
    }

    // ---- writes (TARGET, honor dry-run) ----

    /** Inserts one row into a TARGET table; returns the generated key (or -1). */
    protected long insert(String table, Map<String, Object> values) throws SQLException {
        List<String> cols = new ArrayList<>(values.keySet());
        String sql = insertSql(table, cols);
        if (ctx.dryRun()) {
            ctx.log("[dry-run] " + sql + " <- " + values.values());
            return -1L;
        }
        try (PreparedStatement ps = ctx.target().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            bind(ps, cols, values);
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return -1L;
    }

    /** Batched insert of many rows (same columns) into a TARGET table; returns the row count. */
    protected int insertBatch(String table, List<Map<String, Object>> rows) throws SQLException {
        if (rows.isEmpty()) {
            return 0;
        }
        List<String> cols = new ArrayList<>(rows.get(0).keySet());
        String sql = insertSql(table, cols);
        if (ctx.dryRun()) {
            ctx.log("[dry-run] " + sql + "  x" + rows.size());
            return rows.size();
        }
        try (PreparedStatement ps = ctx.target().prepareStatement(sql)) {
            for (Map<String, Object> row : rows) {
                bind(ps, cols, row);
                ps.addBatch();
            }
            return ps.executeBatch().length;
        }
    }

    /**
     * Runs a raw UPDATE/DELETE (etc.) against the TARGET - e.g. a second pass that fills
     * forward-referencing foreign keys. Returns the affected row count.
     */
    protected int execute(String sql, Object... args) throws SQLException {
        if (ctx.dryRun()) {
            ctx.log("[dry-run] " + sql + (args.length > 0 ? " " + Arrays.toString(args) : ""));
            return 0;
        }
        try (PreparedStatement ps = ctx.target().prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                ps.setObject(i + 1, args[i]);
            }
            return ps.executeUpdate();
        }
    }

    /** Ordered, null-tolerant column map. Usage: {@code values("a", 1, "b", null)}. */
    protected static Map<String, Object> values(Object... keyValuePairs) {
        if (keyValuePairs.length % 2 != 0) {
            throw new IllegalArgumentException("values() needs (key, value) pairs");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < keyValuePairs.length; i += 2) {
            m.put((String) keyValuePairs[i], keyValuePairs[i + 1]);
        }
        return m;
    }

    // ---- internals ----

    private static List<Row> runQuery(Connection connection, String sql) throws SQLException {
        List<Row> rows = new ArrayList<>();
        try (Statement st = connection.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            String[] labels = new String[n];
            for (int i = 1; i <= n; i++) {
                labels[i - 1] = md.getColumnLabel(i);
            }
            while (rs.next()) {
                Map<String, Object> m = new LinkedHashMap<>();
                for (int i = 1; i <= n; i++) {
                    m.put(labels[i - 1], rs.getObject(i));
                }
                rows.add(new Row(m));
            }
        }
        return rows;
    }

    private static String insertSql(String table, List<String> cols) {
        return "INSERT INTO " + table + " (" + String.join(", ", cols) + ") VALUES ("
                + String.join(", ", Collections.nCopies(cols.size(), "?")) + ")";
    }

    private static void bind(PreparedStatement ps, List<String> cols, Map<String, Object> row) throws SQLException {
        int i = 1;
        for (String c : cols) {
            ps.setObject(i++, row.get(c));
        }
    }
}
