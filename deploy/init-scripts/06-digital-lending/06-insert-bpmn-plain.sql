-- =============================================================================
-- Digital Lending System - Insert BPMN as Plain XML
-- Inserts the BPMN process definition as plain XML (not base64 encoded)
-- =============================================================================

-- Delete existing BPMN definitions for Digital Lending
DELETE FROM dw_process_definitions 
WHERE function_unit_id = (SELECT id FROM dw_function_units WHERE code = 'DIGITAL_LENDING');

-- Insert BPMN as plain XML
-- Note: The BPMN XML will be inserted via a separate command using psql's \set feature
-- This is a placeholder that will be replaced by the actual script

\echo 'Please use the PowerShell script to insert the BPMN XML'
\echo 'Run: .\06-insert-bpmn-plain.ps1'
