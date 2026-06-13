/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.config;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.DriverPropertyInfo;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Properties;
import java.util.logging.Logger;

/**
 * Wraps a JDBC {@link Driver} that was loaded by an external class loader (a jar from the drivers
 * directory). {@link java.sql.DriverManager} refuses to use a driver whose class is not visible to
 * the caller's class loader, so we register this shim - which IS loaded by the application class
 * loader - and it delegates every call to the real driver.
 */
final class DriverShim implements Driver {

    private final Driver delegate;

    DriverShim(Driver delegate) {
        this.delegate = delegate;
    }

    @Override
    public Connection connect(String url, Properties info) throws SQLException {
        return delegate.connect(url, info);
    }

    @Override
    public boolean acceptsURL(String url) throws SQLException {
        return delegate.acceptsURL(url);
    }

    @Override
    public DriverPropertyInfo[] getPropertyInfo(String url, Properties info) throws SQLException {
        return delegate.getPropertyInfo(url, info);
    }

    @Override
    public int getMajorVersion() {
        return delegate.getMajorVersion();
    }

    @Override
    public int getMinorVersion() {
        return delegate.getMinorVersion();
    }

    @Override
    public boolean jdbcCompliant() {
        return delegate.jdbcCompliant();
    }

    @Override
    public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        return delegate.getParentLogger();
    }
}
