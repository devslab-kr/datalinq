-- Flat result set (one row per order line). The handler groups by order_no and
-- splits it into orders (master) + order_items (detail).
SELECT
    o.order_no AS order_no,
    o.cust_id  AS customer_id,
    d.prod_id  AS product_id,
    d.qty      AS qty
FROM dbo.orders o
JOIN dbo.order_details d ON d.order_no = o.order_no
ORDER BY o.order_no, d.line_no;
