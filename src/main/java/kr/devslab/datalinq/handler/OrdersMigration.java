/*
 * Copyright 2026 devslab
 * SPDX-License-Identifier: Apache-2.0
 */

package kr.devslab.datalinq.handler;

import kr.devslab.datalinq.engine.MigrationHandler;
import kr.devslab.datalinq.engine.Row;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Example custom migration: one flat source result set -> a master table ({@code orders})
 * plus a detail table ({@code order_items}). The detail FK is the master's generated key.
 *
 * <p>{@code source.sql} is expected to return one row per order line with columns:
 * {@code order_no, customer_id, product_id, qty}.
 *
 * <p>Referenced from operation.properties as {@code handler=orders}; registered in
 * {@code META-INF/services/kr.devslab.datalinq.engine.MigrationHandler}.
 */
public class OrdersMigration extends MigrationHandler {

    @Override
    public String name() {
        return "orders";
    }

    @Override
    public void migrate() throws Exception {
        List<Row> rows = query("source.sql");

        Map<String, List<Row>> byOrder = rows.stream()
                .collect(Collectors.groupingBy(r -> r.str("order_no"), LinkedHashMap::new, Collectors.toList()));

        int masters = 0;
        int details = 0;
        for (Map.Entry<String, List<Row>> entry : byOrder.entrySet()) {
            Row head = entry.getValue().get(0);

            long orderId = insert("orders", values(
                    "order_no", head.get("order_no"),
                    "customer_id", head.get("customer_id")));
            masters++;

            for (Row line : entry.getValue()) {
                insert("order_items", values(
                        "order_id", orderId,
                        "product_id", line.get("product_id"),
                        "qty", line.get("qty")));
                details++;
            }
        }
        ctx.log("orders: " + masters + " master rows, " + details + " detail rows");
    }
}
