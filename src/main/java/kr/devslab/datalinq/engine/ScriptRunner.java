/*
 * Copyright 2026 devslab
 * SPDX-License-Identifier: Apache-2.0
 */

package kr.devslab.datalinq.engine;

import kr.devslab.datalinq.core.Operation;

import java.nio.file.Path;
import java.sql.Statement;

/**
 * Runs the folder's {@code .sql} files (name order) against the TARGET database. For
 * resets / deletes / schema steps - anything that isn't a source-to-target migration.
 *
 * <p>Statement splitting is a simple split on {@code ';'} - fine for plain DML/DDL, not
 * for stored-procedure bodies that contain {@code ';'}.
 */
public final class ScriptRunner {

    public int run(MigrationContext ctx) throws Exception {
        Operation op = ctx.operation();
        int affected = 0;
        for (Path file : op.sqlFiles()) {
            String content = ctx.sql(file.getFileName().toString());
            for (String raw : content.split(";")) {
                String stmt = raw.strip();
                if (stmt.isEmpty()) {
                    continue;
                }
                if (ctx.dryRun()) {
                    ctx.log("[dry-run] " + firstLine(stmt));
                    continue;
                }
                try (Statement st = ctx.target().createStatement()) {
                    st.execute(stmt);
                    int u = st.getUpdateCount();
                    if (u > 0) {
                        affected += u;
                    }
                }
            }
        }
        ctx.log((ctx.dryRun() ? "[dry-run] " : "") + "ran " + op.sqlFiles().size()
                + " script file(s) on target; rows affected=" + affected);
        return affected;
    }

    private static String firstLine(String s) {
        int nl = s.indexOf('\n');
        return (nl < 0 ? s : s.substring(0, nl)).strip();
    }
}
