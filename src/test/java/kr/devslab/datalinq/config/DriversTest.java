/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Unit tests for the external JDBC driver manager (no network here - download is integration). */
class DriversTest {

    @Test
    void catalogListsCommonDownloadableDrivers() {
        assertTrue(Drivers.CATALOG.containsKey("postgresql"));
        assertTrue(Drivers.CATALOG.containsKey("mysql"));
        assertTrue(Drivers.CATALOG.containsKey("oracle"));
    }

    @Test
    void driversDirIsUnderTheUserHome() {
        assertTrue(Drivers.driversDir().toString().contains(".datalinq"));
    }

    @Test
    void downloadingAnUnknownDriverFailsWithItsName() {
        IOException ex = assertThrows(IOException.class, () -> Drivers.download("definitely-not-a-driver"));
        assertTrue(ex.getMessage().contains("definitely-not-a-driver"));
    }

    @Test
    void loadExternalNeverThrows() {
        assertNotNull(Drivers.loadExternal()); // absent dir -> empty list, no exception
    }
}
