/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the header logo (ASCII art) from an external file ({@code branding/logo.txt}), so
 * branding can be swapped without rebuilding. Falls back to a plain wordmark if the file is
 * missing or empty.
 */
public final class Logo {

    private static final List<String> FALLBACK = List.of("DataLinq");

    private Logo() {
    }

    public static List<String> load(Path file) {
        if (Files.isRegularFile(file)) {
            try {
                List<String> lines = new ArrayList<>(Files.readAllLines(file, StandardCharsets.UTF_8));
                while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
                    lines.remove(lines.size() - 1);
                }
                if (!lines.isEmpty()) {
                    return lines;
                }
            } catch (IOException ignored) {
                // fall through to the fallback wordmark
            }
        }
        return FALLBACK;
    }
}
