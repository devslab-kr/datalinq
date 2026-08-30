/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogoTest {

    @Test
    void bundledNoColorLogoUsesStableAsciiUtf8Output(@TempDir Path tempDir) throws Exception {
        List<String> lines = Logo.load(tempDir.resolve("missing-logo.txt"));

        assertEquals(List.of(
                "DataLinq",
                "========",
                "[ source ] ===> [ target ]",
                "Open source by DevsLab"), lines);
        assertEquals(List.of(8, 8, 26, 22), lines.stream().map(String::length).toList());
        assertEquals(26, lines.stream().mapToInt(String::length).max().orElseThrow());
        assertTrue(lines.stream().flatMapToInt(String::chars).allMatch(codePoint -> codePoint <= 0x7f));
        assertFalse(lines.stream().anyMatch(line -> line.indexOf('\u001b') >= 0));

        String bundled = new String(Logo.class.getResourceAsStream("/branding/logo.txt").readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(String.join("\n", lines) + "\n", bundled);
    }
}
