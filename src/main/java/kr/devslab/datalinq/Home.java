/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.ArrayList;
import java.util.List;

/**
 * Resolves the app's external resource files ({@code i18n/}, {@code branding/}, {@code sql/},
 * {@code application.yml}) WITHOUT depending on the current working directory - so the launcher
 * script works when run from anywhere, not just the project root.
 *
 * <p>It searches an ordered list of base directories and, for each requested resource, returns
 * the first base that actually contains it (falling back to the primary base otherwise):
 * <ol>
 *   <li>the install directory - the parent of {@code lib/} that holds the running jar, i.e.
 *       {@code build/install/datalinq/} where the distribution bundles default resources; and</li>
 *   <li>the current working directory - so development runs (IDE / {@code gradle run}) and any
 *       user-edited files placed next to the launcher still take effect.</li>
 * </ol>
 * When running from exploded classes (no jar) only the working directory is used.
 */
public final class Home {

    private final List<Path> bases;

    private Home(List<Path> bases) {
        this.bases = bases;
    }

    public static Home detect() {
        List<Path> bases = new ArrayList<>();
        Path install = installDir();
        if (install != null) {
            bases.add(install);
        }
        bases.add(Paths.get(System.getProperty("user.dir")));
        return new Home(bases);
    }

    /** Test seam: build a Home over explicit base directories (highest priority first). */
    static Home of(List<Path> bases) {
        if (bases.isEmpty()) {
            throw new IllegalArgumentException("at least one base directory is required");
        }
        return new Home(List.copyOf(bases));
    }

    /** The primary base: the install directory when packaged, else the working directory. */
    public Path base() {
        return bases.get(0);
    }

    /**
     * Resolve a resource path (e.g. {@code "i18n"}, {@code "branding/logo.txt"}), preferring
     * whichever base actually contains it; if none do, returns the primary base's candidate so
     * loaders can apply their own missing-file fallback (or {@code save()} can create it there).
     */
    public Path resolve(String name) {
        Path primary = null;
        for (Path b : bases) {
            Path p = b.resolve(name);
            if (primary == null) {
                primary = p;
            }
            if (Files.exists(p)) {
                return p;
            }
        }
        return primary;
    }

    private static Path installDir() {
        try {
            CodeSource cs = Home.class.getProtectionDomain().getCodeSource();
            if (cs == null || cs.getLocation() == null) {
                return null;
            }
            Path location = Paths.get(cs.getLocation().toURI());
            if (Files.isRegularFile(location)) { // <install>/lib/datalinq.jar
                Path lib = location.getParent();
                return lib == null ? null : lib.getParent(); // <install>
            }
            return null; // exploded classes dir (IDE / gradle) -> fall back to the working dir
        } catch (Exception e) {
            return null;
        }
    }
}
