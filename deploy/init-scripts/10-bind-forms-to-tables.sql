-- ============================================
-- 绑定表单到数据表
-- ============================================

-- 将请假时间调整表单绑定到 leave_time_adjustments 表
UPDATE dw_form_definitions 
SET bound_table_id = 2 
WHERE id = 4 AND bound_table_id IS NULL;

-- 确认绑定结果
SELECT f.id, f.form_name, f.form_type, f.bound_table_id, t.table_name
FROM dw_form_definitions f
LEFT JOIN dw_table_definitions t ON f.bound_table_id = t.id
WHERE f.function_unit_id = 1;
