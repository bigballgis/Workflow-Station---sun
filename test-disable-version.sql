-- Disable version 1.0.0 to test the filtering
UPDATE dw_function_units 
SET enabled = false 
WHERE id = 10 AND version = '1.0.0';

-- Verify the update
SELECT id, name, version, is_active, enabled 
FROM dw_function_units 
WHERE id IN (10, 11)
ORDER BY id;
