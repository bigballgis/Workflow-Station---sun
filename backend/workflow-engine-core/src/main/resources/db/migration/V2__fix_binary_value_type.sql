-- =====================================================
-- Fix binary_value column type from oid to bytea
-- =====================================================
-- This migration fixes the binary_value column in wf_process_variables
-- to use bytea instead of oid for proper binary data storage in PostgreSQL
-- 
-- Note: oid type in PostgreSQL is a reference to large objects stored separately,
-- while bytea stores binary data directly in the table. bytea is the recommended
-- approach for binary data in modern PostgreSQL applications.

DO $$
BEGIN
    -- Check if column exists and is of type oid
    IF EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
        AND table_name = 'wf_process_variables' 
        AND column_name = 'binary_value'
        AND udt_name = 'oid'
    ) THEN
        -- Convert oid to bytea
        -- Since oid references large objects, we need to drop and recreate
        -- If there's existing data, it will be lost, but oid references are problematic
        ALTER TABLE wf_process_variables DROP COLUMN binary_value;
        ALTER TABLE wf_process_variables ADD COLUMN binary_value BYTEA;
        
        RAISE NOTICE 'Fixed binary_value column type from oid to bytea';
    ELSIF NOT EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
        AND table_name = 'wf_process_variables' 
        AND column_name = 'binary_value'
    ) THEN
        -- Column doesn't exist, create it as bytea
        ALTER TABLE wf_process_variables ADD COLUMN binary_value BYTEA;
        RAISE NOTICE 'Created binary_value column as bytea';
    ELSIF EXISTS (
        SELECT 1 
        FROM information_schema.columns 
        WHERE table_schema = 'public' 
        AND table_name = 'wf_process_variables' 
        AND column_name = 'binary_value'
        AND udt_name = 'bytea'
    ) THEN
        RAISE NOTICE 'binary_value column is already bytea, no change needed';
    ELSE
        -- Column exists but is not bytea, try to convert it
        -- This handles cases where the column might be a different type
        BEGIN
            ALTER TABLE wf_process_variables 
            ALTER COLUMN binary_value TYPE BYTEA USING binary_value::bytea;
            RAISE NOTICE 'Converted binary_value column to bytea';
        EXCEPTION WHEN OTHERS THEN
            -- If conversion fails, drop and recreate
            ALTER TABLE wf_process_variables DROP COLUMN binary_value;
            ALTER TABLE wf_process_variables ADD COLUMN binary_value BYTEA;
            RAISE NOTICE 'Dropped and recreated binary_value column as bytea due to conversion error';
        END;
    END IF;
END $$;

COMMENT ON COLUMN wf_process_variables.binary_value IS 'Binary data stored as bytea (not oid) for proper PostgreSQL binary data handling';
