-- SOURCE: MS SQL Server. Column ALIASES must match the MariaDB target columns
-- (this is the whole "mapping" - no target INSERT needs to be written).
SELECT
    line_id     AS approval_line_id,
    emp_code    AS employee_code,
    step_no     AS step_order,
    approver_id AS approver_id
FROM dbo.approval_lines
WHERE use_yn = 'Y';
