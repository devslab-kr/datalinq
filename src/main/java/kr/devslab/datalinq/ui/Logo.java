/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads the header logo (ASCII art). An external file ({@code branding/logo.txt}) wins so
 * branding can be swapped without rebuilding; otherwise the bundled classpath default
 * ({@code /branding/logo.txt}, baked into the jar) is used; finally a plain wordmark.
 */
public final class Logo {

    private static final List<String> FALLBACK = List.of("DataLinq");

    private Logo() {
    }

    public static List<String> load(Path file) {
        if (Files.isRegularFile(file)) {
            try {
                List<String> lines = trimTrailingBlanks(Files.readAllLines(file, StandardCharsets.UTF_8));
                if (!lines.isEmpty()) {
                    return lines;
                }
            } catch (IOException ignored) {
                // fall through to the bundled default
            }
        }
        try (InputStream in = Logo.class.getResourceAsStream("/branding/logo.txt")) {
            if (in != null) {
                List<String> lines = trimTrailingBlanks(readLines(in));
                if (!lines.isEmpty()) {
                    return lines;
                }
            }
        } catch (IOException ignored) {
            // fall through to the wordmark
        }
        return FALLBACK;
    }

    private static List<String> readLines(InputStream in) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            for (String line = r.readLine(); line != null; line = r.readLine()) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static List<String> trimTrailingBlanks(List<String> source) {
        List<String> lines = new ArrayList<>(source);
        while (!lines.isEmpty() && lines.get(lines.size() - 1).isBlank()) {
            lines.remove(lines.size() - 1);
        }
        return lines;
    }
}
