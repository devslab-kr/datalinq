/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */

package kr.devslab.datalinq.engine;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * One row from a source query: column label (alias) -> value, in select order.
 * Column names are matched exactly as written in the SELECT (so handler code uses
 * the same alias names that appear in {@code source.sql}).
 *
 * <p>{@link #get(String)} returns the raw JDBC value ({@code Object}) and is all a
 * pass-through migration needs - the value flows straight back into an insert without a
 * cast. The typed accessors below exist only for handlers that <em>compute</em> with a
 * value (arithmetic, comparisons); they <b>coerce</b> rather than cast, because the same
 * column can arrive as {@code Integer}, {@code Long}, or {@code BigDecimal} depending on
 * the driver - a strict cast (or a {@code <T> get(col, Class<T>)} generic) would break on
 * that variance. All return boxed types so a SQL NULL stays {@code null}.
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

    /** Value as a {@code Long} (coerces any numeric or numeric string); null stays null. */
    public Long getLong(String column) {
        Object v = values.get(column);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.longValue();
        }
        try {
            return Long.parseLong(v.toString().trim());
        } catch (NumberFormatException e) {
            throw notNumeric(column, v, "Long");
        }
    }

    /** Value as an {@code Integer} (coerces any numeric or numeric string); null stays null. */
    public Integer getInt(String column) {
        Object v = values.get(column);
        if (v == null) {
            return null;
        }
        if (v instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(v.toString().trim());
        } catch (NumberFormatException e) {
            throw notNumeric(column, v, "Integer");
        }
    }

    /** Value as a {@code BigDecimal} (exact - via the string form, never a double); null stays null. */
    public BigDecimal getBigDecimal(String column) {
        Object v = values.get(column);
        if (v == null) {
            return null;
        }
        if (v instanceof BigDecimal b) {
            return b;
        }
        try {
            return new BigDecimal(v.toString().trim());
        } catch (NumberFormatException e) {
            throw notNumeric(column, v, "BigDecimal");
        }
    }

    /** Value as a {@code Boolean} (true / non-zero / "true"/"t"/"y"/"1"); null stays null. */
    public Boolean getBool(String column) {
        Object v = values.get(column);
        if (v == null) {
            return null;
        }
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.longValue() != 0;
        }
        String s = v.toString().trim().toLowerCase();
        return s.equals("true") || s.equals("t") || s.equals("y") || s.equals("1");
    }

    private static IllegalStateException notNumeric(String column, Object value, String target) {
        return new IllegalStateException("column '" + column + "' = " + value + " ("
                + value.getClass().getSimpleName() + ") is not convertible to " + target);
    }

    public Set<String> columns() {
        return values.keySet();
    }

    public Map<String, Object> asMap() {
        return new LinkedHashMap<>(values);
    }
}
