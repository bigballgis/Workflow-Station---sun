-- Verify enabled field was added
SELECT id, name, version, is_active, enabled 
FROM dw_function_units 
WHERE id = 10
ORDER BY id;
