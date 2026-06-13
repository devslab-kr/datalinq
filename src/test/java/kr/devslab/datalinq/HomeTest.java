/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Unit tests for the base-directory preference in {@link Home}. */
class HomeTest {

    @Test
    void prefersTheBaseThatActuallyHasTheResource(@TempDir Path install, @TempDir Path cwd) throws IOException {
        // i18n exists only under the (lower-priority) working dir -> that base wins for i18n.
        Files.createDirectory(cwd.resolve("i18n"));
        Home home = Home.of(List.of(install, cwd));
        assertEquals(cwd.resolve("i18n"), home.resolve("i18n"));
    }

    @Test
    void prefersTheHigherPriorityBaseWhenBothHaveTheResource(@TempDir Path install, @TempDir Path cwd)
            throws IOException {
        Files.createDirectory(install.resolve("i18n"));
        Files.createDirectory(cwd.resolve("i18n"));
        Home home = Home.of(List.of(install, cwd));
        assertEquals(install.resolve("i18n"), home.resolve("i18n"));
    }

    @Test
    void fallsBackToThePrimaryBaseWhenNoBaseHasTheResource(@TempDir Path install, @TempDir Path cwd) {
        Home home = Home.of(List.of(install, cwd));
        // Nothing exists yet (e.g. application.yml to be created by save()): the primary base.
        assertEquals(install.resolve("application.yml"), home.resolve("application.yml"));
    }

    @Test
    void resolvesNestedPaths(@TempDir Path base) throws IOException {
        Files.createDirectory(base.resolve("branding"));
        Files.writeString(base.resolve("branding").resolve("logo.txt"), "X");
        Home home = Home.of(List.of(base));
        assertEquals(base.resolve("branding/logo.txt"), home.resolve("branding/logo.txt"));
    }
}
