-- Fix: Allow table_id to be NULL for RELATED type bindings
-- Run this manually if Flyway migrations didn't apply

-- 1. Drop NOT NULL constraint on table_id
ALTER TABLE dw_form_table_bindings ALTER COLUMN table_id DROP NOT NULL;

-- 2. Drop unique constraint that prevents multiple NULL table_id rows
ALTER TABLE dw_form_table_bindings DROP CONSTRAINT IF EXISTS uk_form_table_binding;

-- Verify
SELECT column_name, is_nullable 
FROM information_schema.columns 
WHERE table_name = 'dw_form_table_bindings' AND column_name = 'table_id';
