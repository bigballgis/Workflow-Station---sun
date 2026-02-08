-- Check if there's a separate versions table
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
  AND table_name LIKE '%version%'
ORDER BY table_name;
