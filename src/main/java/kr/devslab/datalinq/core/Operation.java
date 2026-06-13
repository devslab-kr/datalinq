/*
 * Copyright 2026 devslab
 * SPDX-License-Identifier: Apache-2.0
 */

package kr.devslab.datalinq.core;

import java.nio.file.Path;
import java.util.List;

/**
 * One menu item discovered from an {@code sql/} subfolder.
 *
 * @param order        sort order parsed from the numeric folder prefix ({@code 01_...})
 * @param id           the raw folder name (stable identifier)
 * @param displayName  human label (prefix stripped, underscores -> spaces)
 * @param description  optional description from operation.properties
 * @param type         how to run it
 * @param dir          the folder path
 * @param sqlFiles     {@code .sql} files in the folder, sorted by name
 * @param targetTable  target table for ETL (from {@code target=} in operation.properties)
 * @param handlerName  ServiceLoader key for HANDLER type (from {@code handler=})
 * @param destructive  whether this needs an extra confirmation (from {@code destructive=true})
 * @param confirmText  message shown on the confirmation prompt
 */
public record Operation(
        int order,
        String id,
        String displayName,
        String description,
        OperationType type,
        Path dir,
        List<Path> sqlFiles,
        String targetTable,
        String handlerName,
        boolean destructive,
        String confirmText) {
}
