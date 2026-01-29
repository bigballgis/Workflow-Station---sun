
-- ============================================
-- 删除所有数据库对象脚本
-- 警告：此脚本将删除 workflow_platform 数据库中 public schema 的所有对象
-- 包括：表、索引、序列、视图、函数、类型和约束
-- 
-- 安全措施：
-- 1. 仅删除 public schema 中的对象
-- 2. 仅在工作流平台数据库 (workflow_platform) 中执行
-- 3. 不会删除其他数据库或 schema 中的对象
-- ============================================

-- 0. 安全检查：确保当前连接的数据库是 workflow_platform
DO $$
DECLARE
    current_db TEXT;
BEGIN
    SELECT current_database() INTO current_db;
    
    IF current_db != 'workflow_platform' THEN
        RAISE EXCEPTION '安全错误：当前数据库是 "%"，但此脚本只能在工作流平台数据库 (workflow_platform) 中执行。请先连接到正确的数据库。', current_db;
    END IF;
    
    RAISE NOTICE '安全检查通过：当前数据库是 workflow_platform';
END $$;

-- 1. 禁用外键约束检查（PostgreSQL 不支持，但我们可以先删除外键）
SET session_replication_role = 'replica';

-- 2. 删除所有外键约束
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT 
            conname AS constraint_name,
            conrelid::regclass AS table_name
        FROM pg_constraint
        WHERE contype = 'f'
        AND connamespace = 'public'::regnamespace
    ) LOOP
        EXECUTE 'ALTER TABLE ' || r.table_name || ' DROP CONSTRAINT IF EXISTS ' || r.constraint_name || ' CASCADE';
        RAISE NOTICE 'Dropped foreign key: % on table %', r.constraint_name, r.table_name;
    END LOOP;
END $$;

-- 3. 删除所有表（CASCADE 会自动删除依赖的索引、约束等）
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT tablename
        FROM pg_tables
        WHERE schemaname = 'public'
        ORDER BY tablename
    ) LOOP
        EXECUTE 'DROP TABLE IF EXISTS public.' || quote_ident(r.tablename) || ' CASCADE';
        RAISE NOTICE 'Dropped table: %', r.tablename;
    END LOOP;
END $$;

-- 4. 删除所有序列
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT sequence_name
        FROM information_schema.sequences
        WHERE sequence_schema = 'public'
        ORDER BY sequence_name
    ) LOOP
        EXECUTE 'DROP SEQUENCE IF EXISTS public.' || quote_ident(r.sequence_name) || ' CASCADE';
        RAISE NOTICE 'Dropped sequence: %', r.sequence_name;
    END LOOP;
END $$;

-- 5. 删除所有视图
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT table_name
        FROM information_schema.views
        WHERE table_schema = 'public'
        ORDER BY table_name
    ) LOOP
        EXECUTE 'DROP VIEW IF EXISTS public.' || quote_ident(r.table_name) || ' CASCADE';
        RAISE NOTICE 'Dropped view: %', r.table_name;
    END LOOP;
END $$;

-- 6. 删除所有函数
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT routine_name, routine_type
        FROM information_schema.routines
        WHERE routine_schema = 'public'
        ORDER BY routine_name
    ) LOOP
        EXECUTE 'DROP ' || r.routine_type || ' IF EXISTS public.' || quote_ident(r.routine_name) || ' CASCADE';
        RAISE NOTICE 'Dropped %: %', r.routine_type, r.routine_name;
    END LOOP;
END $$;

-- 7. 删除所有类型（如果存在）
DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN (
        SELECT typname
        FROM pg_type
        WHERE typnamespace = 'public'::regnamespace
        AND typtype = 'c'  -- composite types
    ) LOOP
        EXECUTE 'DROP TYPE IF EXISTS public.' || quote_ident(r.typname) || ' CASCADE';
        RAISE NOTICE 'Dropped type: %', r.typname;
    END LOOP;
END $$;

-- 8. 恢复外键约束检查
SET session_replication_role = 'origin';

-- 9. 显示清理结果
SELECT 
    'Tables' AS object_type,
    COUNT(*) AS remaining_count
FROM pg_tables
WHERE schemaname = 'public'
UNION ALL
SELECT 
    'Sequences' AS object_type,
    COUNT(*) AS remaining_count
FROM information_schema.sequences
WHERE sequence_schema = 'public'
UNION ALL
SELECT 
    'Views' AS object_type,
    COUNT(*) AS remaining_count
FROM information_schema.views
WHERE table_schema = 'public';

-- 完成
SELECT 'Database cleanup completed!' AS status;
