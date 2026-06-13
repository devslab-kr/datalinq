/*
 * Copyright 2026 DevsLab Co., Ltd.
 * SPDX-License-Identifier: Apache-2.0
 */
package kr.devslab.datalinq.handler;

import kr.devslab.datalinq.engine.MigrationHandler;
import kr.devslab.datalinq.engine.Row;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A "more complex than master/detail" example: one flat source result set becomes
 * <b>customers</b> (deduplicated) + <b>orders</b> (master, with a resolved customer FK) +
 * <b>order_items</b> (detail) - three target tables, a lookup map, and FK resolution, all in
 * one target transaction.
 *
 * <p>{@code source.sql} is expected to return one row per order line:
 * {@code cust_code, cust_name, order_no, product, qty}.
 */
public class OrderImportHandler extends MigrationHandler {

    @Override
    public String name() {
        return "order-import";
    }

    @Override
    public void migrate() throws Exception {
        List<Row> rows = query("source.sql");

        // 1) dedup customers; remember natural code -> generated surrogate id
        Map<String, Long> customerId = new LinkedHashMap<>();
        for (Row r : rows) {
            String code = r.str("cust_code");
            if (!customerId.containsKey(code)) {
                long id = insert("customers", values("code", code, "name", r.get("cust_name")));
                customerId.put(code, id);
            }
        }

        // 2) group by order; insert order (master, FK resolved) then its items (detail)
        Map<String, List<Row>> byOrder = new LinkedHashMap<>();
        for (Row r : rows) {
            byOrder.computeIfAbsent(r.str("order_no"), k -> new ArrayList<>()).add(r);
        }

        int orders = 0;
        int items = 0;
        for (Map.Entry<String, List<Row>> e : byOrder.entrySet()) {
            Row head = e.getValue().get(0);
            long orderId = insert("orders", values(
                    "order_no", head.get("order_no"),
                    "customer_id", customerId.get(head.str("cust_code"))));
            orders++;
            for (Row line : e.getValue()) {
                insert("order_items", values(
                        "order_id", orderId,
                        "product", line.get("product"),
                        "qty", line.get("qty")));
                items++;
            }
        }
        ctx.log("imported " + customerId.size() + " customers, " + orders + " orders, " + items + " items");
    }
}
