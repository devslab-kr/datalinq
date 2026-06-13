/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.config;

import kr.devslab.datalinq.ui.DataLinqController.DatasourceGateway;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

/**
 * Production {@link DatasourceGateway} backed by {@link AppConfig} (read + persist) and the
 * JDBC {@link DriverManager} (connection test). Kept out of the controller so the controller
 * stays free of config and JDBC and remains unit-testable with a fake gateway.
 */
public final class AppConfigDatasourceGateway implements DatasourceGateway {

    private static final int TEST_TIMEOUT_SECONDS = 5;

    private final AppConfig config;

    public AppConfigDatasourceGateway(AppConfig config) {
        this.config = config;
    }

    @Override
    public List<String> names() {
        return config.datasourceNames();
    }

    @Override
    public String url(String name) {
        return config.url(name);
    }

    @Override
    public String username(String name) {
        return config.username(name);
    }

    @Override
    public String password(String name) {
        return config.password(name);
    }

    @Override
    public String defaultSource() {
        return config.defaultSource();
    }

    @Override
    public String defaultTarget() {
        return config.defaultTarget();
    }

    @Override
    public void save(String name, String url, String username, String password,
                     boolean asDefaultSource, boolean asDefaultTarget) throws Exception {
        config.setDatasource(name, url, username, password);
        if (asDefaultSource) {
            config.setDefaultSource(name);
        }
        if (asDefaultTarget) {
            config.setDefaultTarget(name);
        }
        config.save();
    }

    @Override
    public void remove(String name) throws Exception {
        config.removeDatasource(name);
        config.save();
    }

    @Override
    public String test(String url, String username, String password) {
        if (url == null || url.isBlank()) {
            return "url is empty";
        }
        int previous = DriverManager.getLoginTimeout();
        DriverManager.setLoginTimeout(TEST_TIMEOUT_SECONDS);
        try (Connection ignored = DriverManager.getConnection(url, username, password)) {
            return null;
        } catch (Exception e) {
            String message = e.getMessage();
            return message == null ? e.getClass().getSimpleName() : message;
        } finally {
            DriverManager.setLoginTimeout(previous);
        }
    }
}
