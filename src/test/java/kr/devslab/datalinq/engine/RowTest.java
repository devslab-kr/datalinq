/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.engine;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for {@link Row}'s coercing typed accessors. */
class RowTest {

    private static Row row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return new Row(m);
    }

    @Test
    void getLongCoercesAcrossNumericTypes() {
        // the same logical value as the driver might hand it back in different types
        assertEquals(5L, row("id", 5).getLong("id"));            // Integer
        assertEquals(5L, row("id", 5L).getLong("id"));           // Long
        assertEquals(5L, row("id", new BigDecimal("5")).getLong("id")); // BigDecimal
        assertEquals(5L, row("id", "5").getLong("id"));          // numeric String
    }

    @Test
    void nullStaysNull() {
        assertNull(row("id", null).getLong("id"));
        assertNull(row("id", null).getInt("id"));
        assertNull(row("id", null).getBigDecimal("id"));
        assertNull(row("id", null).getBool("id"));
        assertNull(row("name", null).str("name"));
    }

    @Test
    void getBigDecimalIsExactViaString() {
        assertEquals(new BigDecimal("19.99"), row("price", "19.99").getBigDecimal("price"));
        assertEquals(new BigDecimal("19.99"), row("price", new BigDecimal("19.99")).getBigDecimal("price"));
    }

    @Test
    void getBoolAcceptsCommonForms() {
        assertTrue(row("ok", true).getBool("ok"));
        assertTrue(row("ok", 1).getBool("ok"));
        assertTrue(row("ok", "Y").getBool("ok"));
        assertTrue(row("ok", "true").getBool("ok"));
        assertFalse(row("ok", 0).getBool("ok"));
        assertFalse(row("ok", "n").getBool("ok"));
    }

    @Test
    void nonNumericValueThrowsWithColumnContext() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> row("qty", "abc").getLong("qty"));
        assertTrue(ex.getMessage().contains("qty"));
    }
}
