-- Query function units to check is_active field
SELECT id, name, version, is_active, deployed_at 
FROM dw_function_units 
WHERE id IN (10, 11)
ORDER BY id;
