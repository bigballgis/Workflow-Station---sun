-- =====================================================
-- V2: Fix duplicate unique constraint on dw_form_table_bindings
-- Date: 2026-01-22
-- Purpose: Remove JPA auto-generated duplicate constraint
-- =====================================================

-- Drop the JPA auto-generated constraint (if exists)
-- Keep the manually defined uk_form_table_binding constraint
DO $$
BEGIN
    -- Check if the auto-generated constraint exists
    IF EXISTS (
        SELECT 1 FROM pg_constraint 
        WHERE conname = 'ukn5x4ip72yh1fmc3hth36r953d' 
        AND conrelid = 'dw_form_table_bindings'::regclass
    ) THEN
        -- Drop the duplicate constraint
        ALTER TABLE dw_form_table_bindings DROP CONSTRAINT ukn5x4ip72yh1fmc3hth36r953d;
        RAISE NOTICE 'Dropped duplicate constraint ukn5x4ip72yh1fmc3hth36r953d';
    ELSE
        RAISE NOTICE 'Constraint ukn5x4ip72yh1fmc3hth36r953d does not exist, skipping';
    END IF;
END $$;

-- Verify only one unique constraint remains
DO $$
DECLARE
    constraint_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO constraint_count
    FROM pg_constraint
    WHERE conrelid = 'dw_form_table_bindings'::regclass
    AND contype = 'u';
    
    IF constraint_count = 1 THEN
        RAISE NOTICE 'Success: Only one unique constraint remains (uk_form_table_binding)';
    ELSE
        RAISE WARNING 'Warning: Expected 1 unique constraint, found %', constraint_count;
    END IF;
END $$;
