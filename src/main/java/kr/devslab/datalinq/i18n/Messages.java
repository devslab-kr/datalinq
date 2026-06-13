/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.i18n;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.Locale;
import java.util.Properties;

/**
 * Tiny i18n: loads {@code messages_<lang>.properties} (UTF-8) from an external {@code i18n/}
 * folder. English is always loaded first as the fallback base; the chosen language overlays
 * it, so missing translations fall back to English (and missing keys fall back to the key
 * itself). Pure file I/O - no {@code ResourceBundle} - so it is drop-in editable and
 * GraalVM native-image safe.
 *
 * <p>Add a language by dropping {@code messages_<lang>.properties} into {@code i18n/}.
 */
public final class Messages {

    private final Properties props;

    private Messages(Properties props) {
        this.props = props;
    }

    /**
     * @param i18nDir folder containing {@code messages_<lang>.properties}
     * @param lang    language code (e.g. {@code "ko"}); null/blank uses the system default
     * @return a Messages backed by English + the chosen language overlay
     */
    public static Messages load(Path i18nDir, String lang) {
        String language = (lang == null || lang.isBlank())
                ? Locale.getDefault().getLanguage() : lang.trim();
        Properties p = new Properties();
        merge(p, i18nDir, "en");                  // base / fallback
        if (!"en".equals(language)) {
            merge(p, i18nDir, language);          // overlay chosen language
        }
        return new Messages(p);
    }

    /** Message for {@code key}, or {@code key} itself if missing. */
    public String get(String key) {
        return props.getProperty(key, key);
    }

    /** Message for {@code key} formatted with {@code args} (see {@link MessageFormat}). */
    public String get(String key, Object... args) {
        String pattern = props.getProperty(key, key);
        return args.length == 0 ? pattern : MessageFormat.format(pattern, args);
    }

    private static void merge(Properties target, Path dir, String lang) {
        Path file = dir.resolve("messages_" + lang + ".properties");
        if (Files.exists(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                target.load(r);
            } catch (IOException ignored) {
                // keep whatever has loaded so far
            }
        }
    }
}
