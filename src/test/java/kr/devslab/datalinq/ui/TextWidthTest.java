/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for terminal column-width measurement and padding. */
class TextWidthTest {

    @Test
    void asciiCountsOneCellPerChar() {
        assertEquals(3, TextWidth.of("URL"));
        assertEquals(8, TextWidth.of("Password"));
    }

    @Test
    void hangulCountsTwoCellsPerGlyph() {
        assertEquals(6, TextWidth.of("사용자"));   // 3 glyphs * 2 cells
        assertEquals(8, TextWidth.of("비밀번호")); // 4 glyphs * 2 cells
    }

    @Test
    void padsToEqualColumnWidthAcrossScripts() {
        // The bug this fixes: char-count padding drifts; column-width padding lines them up.
        String a = TextWidth.pad("URL", 8);
        String b = TextWidth.pad("사용자", 8);
        String c = TextWidth.pad("비밀번호", 8);
        assertEquals(8, TextWidth.of(a));
        assertEquals(8, TextWidth.of(b));
        assertEquals(8, TextWidth.of(c));
    }

    @Test
    void padIsNoOpWhenAlreadyAtOrOverWidth() {
        assertEquals("Password", TextWidth.pad("Password", 8));
        assertEquals("Password", TextWidth.pad("Password", 4));
    }

    @Test
    void mixedAsciiAndHangul() {
        assertTrue(TextWidth.of("DB 연결") == 2 + 1 + 4); // "DB" + space + 연결(2 glyphs)
    }
}
