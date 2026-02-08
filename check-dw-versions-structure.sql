-- Check dw_versions table structure
SELECT column_name, data_type, is_nullable
FROM information_schema.columns
WHERE table_name = 'dw_versions'
ORDER BY ordinal_position;
