-- Flat result set; the order-import handler splits it into customers (dedup) +
-- orders (master, FK to customer) + order_items (detail).
SELECT
    c.cust_code AS cust_code,
    c.cust_name AS cust_name,
    o.order_no  AS order_no,
    d.prod_name AS product,
    d.qty       AS qty
FROM dbo.orders o
JOIN dbo.customers c     ON c.cust_code = o.cust_code
JOIN dbo.order_details d ON d.order_no = o.order_no
ORDER BY o.order_no, d.line_no;
