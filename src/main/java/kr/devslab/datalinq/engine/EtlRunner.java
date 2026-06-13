/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */

package kr.devslab.datalinq.engine;

import kr.devslab.datalinq.core.Operation;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collections;

/**
 * ETL: read {@code source.sql} from the SOURCE db and stream the rows into the configured
 * target table. The INSERT is generated directly from the result set's column labels -
 * which, by convention, are aliased in the SELECT to match the target column names - so
 * no target INSERT statement needs to be written.
 */
public final class EtlRunner {

    private static final int BATCH_SIZE = 1000;

    public int run(MigrationContext ctx) throws Exception {
        Operation op = ctx.operation();
        if (op.targetTable().isBlank()) {
            throw new IllegalStateException("ETL operation '" + op.id()
                    + "' needs 'target=<table>' in operation.properties");
        }
        String sourceSql = ctx.sql(sourceFileName(op));

        try (Statement st = ctx.source().createStatement();
             ResultSet rs = st.executeQuery(sourceSql)) {

            ResultSetMetaData md = rs.getMetaData();
            int n = md.getColumnCount();
            String[] cols = new String[n];
            for (int i = 1; i <= n; i++) {
                cols[i - 1] = md.getColumnLabel(i);
            }
            String insert = "INSERT INTO " + op.targetTable() + " (" + String.join(", ", cols)
                    + ") VALUES (" + String.join(", ", Collections.nCopies(n, "?")) + ")";
            ctx.log("map " + Arrays.toString(cols) + " -> " + op.targetTable());

            if (ctx.dryRun()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                }
                ctx.log("[dry-run] " + insert);
                ctx.log("[dry-run] would insert " + count + " rows");
                return count;
            }

            int count = 0;
            int pending = 0;
            try (PreparedStatement ps = ctx.target().prepareStatement(insert)) {
                while (rs.next()) {
                    for (int i = 1; i <= n; i++) {
                        ps.setObject(i, rs.getObject(i));
                    }
                    ps.addBatch();
                    count++;
                    if (++pending >= BATCH_SIZE) {
                        ps.executeBatch();
                        pending = 0;
                    }
                }
                if (pending > 0) {
                    ps.executeBatch();
                }
            }
            ctx.log("inserted " + count + " rows");
            return count;
        }
    }

    private static String sourceFileName(Operation op) {
        return op.sqlFiles().stream()
                .map(p -> p.getFileName().toString())
                .filter(name -> name.equalsIgnoreCase("source.sql"))
                .findFirst()
                .orElseGet(() -> {
                    if (op.sqlFiles().size() == 1) {
                        return op.sqlFiles().get(0).getFileName().toString();
                    }
                    throw new IllegalStateException("ETL operation '" + op.id()
                            + "' needs a source.sql (or exactly one .sql file)");
                });
    }
}
