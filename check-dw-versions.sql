-- Check dw_versions table structure
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'dw_versions'
ORDER BY ordinal_position;

-- Check dw_versions data
SELECT * FROM dw_versions WHERE function_unit_id = 10;
