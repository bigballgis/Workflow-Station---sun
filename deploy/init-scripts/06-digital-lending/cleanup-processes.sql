-- =============================================================================
-- Digital Lending System - Cleanup Script
-- Cleans up all process instances and tasks for fresh testing
-- =============================================================================

-- Get the process definition key
\echo 'Cleaning up Digital Lending processes and tasks...'

-- 1. Clean up Flowable runtime tables (active processes and tasks)
DELETE FROM act_ru_identitylink 
WHERE proc_inst_id_ IN (
    SELECT proc_inst_id_ FROM act_ru_execution 
    WHERE proc_def_id_ LIKE 'DigitalLendingProcess:%'
);

DELETE FROM act_ru_variable 
WHERE proc_inst_id_ IN (
    SELECT proc_inst_id_ FROM act_ru_execution 
    WHERE proc_def_id_ LIKE 'DigitalLendingProcess:%'
);

DELETE FROM act_ru_task 
WHERE proc_inst_id_ IN (
    SELECT proc_inst_id_ FROM act_ru_execution 
    WHERE proc_def_id_ LIKE 'DigitalLendingProcess:%'
);

DELETE FROM act_ru_execution 
WHERE proc_def_id_ LIKE 'DigitalLendingProcess:%';

-- 2. Clean up Flowable history tables
DELETE FROM act_hi_identitylink 
WHERE proc_inst_id_ IN (
    SELECT id_ FROM act_hi_procinst 
    WHERE proc_def_id_ LIKE 'DigitalLendingProcess:%'
);

DELETE FROM act_hi_taskinst 
WHERE proc_inst_id_ IN (
    SELECT id_ FROM act_hi_procinst 
    WHERE proc_def_id_ LIKE 'DigitalLendingProcess:%'
);

DELETE FROM act_hi_varinst 
WHERE proc_inst_id_ IN (
    SELECT id_ FROM act_hi_procinst 
    WHERE proc_def_id_ LIKE 'DigitalLendingProcess:%'
);

DELETE FROM act_hi_actinst 
WHERE proc_inst_id_ IN (
    SELECT id_ FROM act_hi_procinst 
    WHERE proc_def_id_ LIKE 'DigitalLendingProcess:%'
);

DELETE FROM act_hi_procinst 
WHERE proc_def_id_ LIKE 'DigitalLendingProcess:%';

-- 3. Clean up User Portal process instances
DELETE FROM up_process_instance 
WHERE process_definition_key = 'DigitalLendingProcess';

-- 4. Show summary
\echo ''
\echo 'Cleanup complete!'
\echo ''
\echo 'Summary:'
SELECT 
    'Active Tasks' as item,
    COUNT(*) as count
FROM act_ru_task 
WHERE proc_def_id_ LIKE 'DigitalLendingProcess:%'
UNION ALL
SELECT 
    'Active Process Instances' as item,
    COUNT(*) as count
FROM act_ru_execution 
WHERE proc_def_id_ LIKE 'DigitalLendingProcess:%'
UNION ALL
SELECT 
    'History Process Instances' as item,
    COUNT(*) as count
FROM act_hi_procinst 
WHERE proc_def_id_ LIKE 'DigitalLendingProcess:%'
UNION ALL
SELECT 
    'User Portal Instances' as item,
    COUNT(*) as count
FROM up_process_instance 
WHERE process_definition_key = 'DigitalLendingProcess';

\echo ''
\echo 'All counts should be 0. You can now start fresh testing!'
\echo ''
