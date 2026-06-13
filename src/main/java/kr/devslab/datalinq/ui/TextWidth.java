/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.ui;

/**
 * Terminal column-width helpers. A monospace terminal renders East Asian "wide" glyphs (Hangul,
 * CJK) in two cells, so aligning columns by {@link String#length()} drifts once the text mixes
 * scripts. {@link #of(String)} approximates the rendered width and {@link #pad(String, int)} pads
 * to a target column width - used to line up the DB-form labels regardless of locale.
 */
public final class TextWidth {

    private TextWidth() {
    }

    /** Approximate terminal column width - wide (East Asian) glyphs count as two cells. */
    public static int of(String s) {
        int width = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            width += isWide(cp) ? 2 : 1;
            i += Character.charCount(cp);
        }
        return width;
    }

    /** Pads {@code s} with trailing spaces until its column width reaches {@code targetWidth}. */
    public static String pad(String s, int targetWidth) {
        StringBuilder b = new StringBuilder(s);
        for (int w = of(s); w < targetWidth; w++) {
            b.append(' ');
        }
        return b.toString();
    }

    private static boolean isWide(int cp) {
        return (cp >= 0x1100 && cp <= 0x115F)      // Hangul Jamo
                || (cp >= 0x2E80 && cp <= 0xA4CF)  // CJK radicals .. Yi
                || (cp >= 0xAC00 && cp <= 0xD7A3)  // Hangul syllables
                || (cp >= 0xF900 && cp <= 0xFAFF)  // CJK compatibility ideographs
                || (cp >= 0xFE30 && cp <= 0xFE4F)  // CJK compatibility forms
                || (cp >= 0xFF00 && cp <= 0xFF60)  // fullwidth forms
                || (cp >= 0xFFE0 && cp <= 0xFFE6)
                || (cp >= 0x20000 && cp <= 0x3FFFD); // CJK extension planes
    }
}
