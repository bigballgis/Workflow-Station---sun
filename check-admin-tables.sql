-- Check Admin Center tables related to function units
SELECT table_name 
FROM information_schema.tables 
WHERE table_schema = 'public' 
  AND (table_name LIKE 'ac_%' OR table_name LIKE 'admin_%')
ORDER BY table_name;
