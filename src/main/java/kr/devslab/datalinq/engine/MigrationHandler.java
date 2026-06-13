/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */

package kr.devslab.datalinq.engine;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for custom migrations: transforms, derived rows, or one source result set
 * split into several target tables (e.g. master/detail).
 *
 * <p>Implementations are discovered via {@link java.util.ServiceLoader} (so it stays
 * GraalVM native-image friendly - no string-driven reflection). To add one:
 * <ol>
 *   <li>subclass this, give it a unique {@link #name()} and implement {@link #migrate()};</li>
 *   <li>register it in {@code META-INF/services/kr.devslab.datalinq.engine.MigrationHandler};</li>
 *   <li>reference it from a folder's operation.properties: {@code handler=<name>}.</li>
 * </ol>
 *
 * <p>The helpers query the SOURCE and insert into the TARGET of the active context;
 * inserts honor dry-run. The engine wraps the whole run in a target transaction.
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

    /** Runs a SELECT (a .sql file in this folder) against the SOURCE; returns all rows. */
    protected List<Row> query(String sqlFileName) throws Exception {
        String sql = ctx.sql(sqlFileName);
        List<Row> rows = new ArrayList<>();
        try (Statement st = ctx.source().createStatement();
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

    /**
     * Inserts one row into a TARGET table; returns the generated key (or -1).
     * In dry-run mode nothing is written - the statement is logged instead.
     */
    protected long insert(String table, Map<String, Object> values) throws SQLException {
        List<String> cols = new ArrayList<>(values.keySet());
        String sql = "INSERT INTO " + table + " (" + String.join(", ", cols) + ") VALUES ("
                + String.join(", ", Collections.nCopies(cols.size(), "?")) + ")";
        if (ctx.dryRun()) {
            ctx.log("[dry-run] " + sql + " <- " + values.values());
            return -1L;
        }
        try (PreparedStatement ps = ctx.target().prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            int i = 1;
            for (String c : cols) {
                ps.setObject(i++, values.get(c));
            }
            ps.executeUpdate();
            try (ResultSet keys = ps.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getLong(1);
                }
            }
        }
        return -1L;
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
}
