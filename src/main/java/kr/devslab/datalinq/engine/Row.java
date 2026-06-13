/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */

package kr.devslab.datalinq.engine;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * One row from a source query: column label (alias) -> value, in select order.
 * Column names are matched exactly as written in the SELECT (so handler code uses
 * the same alias names that appear in {@code source.sql}).
 */
public final class Row {

    private final Map<String, Object> values;

    public Row(Map<String, Object> values) {
        this.values = values;
    }

    public Object get(String column) {
        return values.get(column);
    }

    public String str(String column) {
        Object v = values.get(column);
        return v == null ? null : v.toString();
    }

    public Set<String> columns() {
        return values.keySet();
    }

    public Map<String, Object> asMap() {
        return new LinkedHashMap<>(values);
    }
}
