-- Check all tables related to function units
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
  AND table_name LIKE '%function%'
ORDER BY table_name;
