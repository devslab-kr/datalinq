/*
 * Copyright 2026 devslab
 * SPDX-License-Identifier: Apache-2.0
 */

package kr.devslab.datalinq.core;

/**
 * How an operation folder is executed.
 *
 * <ul>
 *   <li>{@code ETL}     - read {@code source.sql} from the SOURCE db, auto-insert into a target table
 *                         (column labels/aliases of the SELECT must match the target columns).</li>
 *   <li>{@code SCRIPT}  - run the folder's {@code .sql} files against the TARGET db
 *                         (resets, deletes, schema tweaks).</li>
 *   <li>{@code HANDLER} - a {@link kr.devslab.datalinq.engine.MigrationHandler} class for custom logic
 *                         (e.g. one source result set -> master + detail tables).</li>
 * </ul>
 */
public enum OperationType {
    ETL,
    SCRIPT,
    HANDLER
}
