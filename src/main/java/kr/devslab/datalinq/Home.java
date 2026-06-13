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
 *   <li>the directory holding the running jar - a dropped fat jar keeps {@code sql/} and any
 *       overrides right next to {@code datalinq.jar};</li>
 *   <li>the parent of {@code lib/} - an {@code installDist} image bundles the defaults there; and</li>
 *   <li>the current working directory - so dev runs (IDE / {@code gradle}) and files placed in
 *       whatever folder the app is launched from still take effect.</li>
 * </ol>
 * Running from exploded classes (no jar) uses only the working directory. The jar also carries
 * the defaults on its classpath, so a bare jar with no external files still renders correctly.
 */
public final class Home {

    private final List<Path> bases;

    private Home(List<Path> bases) {
        this.bases = bases;
    }

    public static Home detect() {
        List<Path> bases = new ArrayList<>(jarBases());
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

    /** The primary base: the jar's directory when packaged, else the working directory. */
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

    /**
     * Resource bases derived from the running jar's location:
     * <ul>
     *   <li>the jar's own directory - a dropped fat jar keeps {@code sql/} and overrides beside it;</li>
     *   <li>the parent of {@code lib/} - an {@code installDist} image bundles defaults there.</li>
     * </ul>
     * Empty when running from exploded classes (IDE / {@code gradle}); the working dir then applies.
     */
    private static List<Path> jarBases() {
        List<Path> out = new ArrayList<>();
        try {
            CodeSource cs = Home.class.getProtectionDomain().getCodeSource();
            if (cs != null && cs.getLocation() != null) {
                Path location = Paths.get(cs.getLocation().toURI());
                if (Files.isRegularFile(location)) { // a jar (fat jar, or <install>/lib/datalinq.jar)
                    Path dir = location.getParent();
                    if (dir != null) {
                        out.add(dir);
                        if ("lib".equals(String.valueOf(dir.getFileName()))) {
                            Path install = dir.getParent();
                            if (install != null) {
                                out.add(install);
                            }
                        }
                    }
                }
            }
        } catch (Exception ignored) {
            // running from exploded classes or an unreadable location -> rely on the working dir
        }
        return out;
    }
}
