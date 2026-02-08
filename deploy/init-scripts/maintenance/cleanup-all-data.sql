-- Cleanup script to remove all function units and related data
-- This script will delete all data in the correct order to respect foreign key constraints

BEGIN;

-- Display current data counts before cleanup
DO $$
DECLARE
    fu_count INTEGER;
    proc_def_count INTEGER;
    proc_inst_count INTEGER;
    form_count INTEGER;
    table_count INTEGER;
    action_count INTEGER;
    task_count INTEGER;
    hist_task_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO fu_count FROM dw_function_units;
    SELECT COUNT(*) INTO proc_def_count FROM dw_process_definitions;
    SELECT COUNT(*) INTO proc_inst_count FROM up_process_instance;
    SELECT COUNT(*) INTO form_count FROM dw_form_definitions;
    SELECT COUNT(*) INTO table_count FROM dw_table_definitions;
    SELECT COUNT(*) INTO action_count FROM dw_action_definitions;
    SELECT COUNT(*) INTO task_count FROM act_ru_task;
    SELECT COUNT(*) INTO hist_task_count FROM act_hi_taskinst;
    
    RAISE NOTICE 'Current data counts:';
    RAISE NOTICE '  - Function Units: %', fu_count;
    RAISE NOTICE '  - Process Definitions: %', proc_def_count;
    RAISE NOTICE '  - Process Instances: %', proc_inst_count;
    RAISE NOTICE '  - Form Definitions: %', form_count;
    RAISE NOTICE '  - Table Definitions: %', table_count;
    RAISE NOTICE '  - Action Definitions: %', action_count;
    RAISE NOTICE '  - Runtime Tasks: %', task_count;
    RAISE NOTICE '  - Historical Tasks: %', hist_task_count;
END $$;

-- Delete Flowable task data (must be done before process instances)
DO $$
DECLARE
    task_count INTEGER;
    hist_task_count INTEGER;
BEGIN
    -- Delete runtime tasks
    SELECT COUNT(*) INTO task_count FROM act_ru_task;
    DELETE FROM act_ru_task;
    RAISE NOTICE 'Deleted % runtime tasks', task_count;
    
    -- Delete historical tasks
    SELECT COUNT(*) INTO hist_task_count FROM act_hi_taskinst;
    DELETE FROM act_hi_taskinst;
    RAISE NOTICE 'Deleted % historical tasks', hist_task_count;
    
    -- Delete extended task info
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'wf_extended_task_info') THEN
        DELETE FROM wf_extended_task_info;
        RAISE NOTICE 'Deleted all extended task info';
    END IF;
    
    -- Delete virtual group task history
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'sys_virtual_group_task_history') THEN
        DELETE FROM sys_virtual_group_task_history;
        RAISE NOTICE 'Deleted all virtual group task history';
    END IF;
END $$;

-- Delete Flowable runtime data
DO $$
DECLARE
    exec_count INTEGER;
    var_count INTEGER;
BEGIN
    -- Delete runtime identity links first (has FK to execution)
    DELETE FROM act_ru_identitylink;
    RAISE NOTICE 'Deleted all runtime identity links';
    
    -- Delete runtime event subscriptions (has FK to execution)
    DELETE FROM act_ru_event_subscr;
    RAISE NOTICE 'Deleted all runtime event subscriptions';
    
    -- Delete runtime entity links
    DELETE FROM act_ru_entitylink;
    RAISE NOTICE 'Deleted all runtime entity links';
    
    -- Delete runtime variables
    SELECT COUNT(*) INTO var_count FROM act_ru_variable;
    DELETE FROM act_ru_variable;
    RAISE NOTICE 'Deleted % runtime variables', var_count;
    
    -- Delete runtime jobs
    DELETE FROM act_ru_job;
    DELETE FROM act_ru_timer_job;
    DELETE FROM act_ru_suspended_job;
    DELETE FROM act_ru_deadletter_job;
    DELETE FROM act_ru_external_job;
    DELETE FROM act_ru_history_job;
    RAISE NOTICE 'Deleted all runtime jobs';
    
    -- Delete runtime activity instances
    DELETE FROM act_ru_actinst;
    RAISE NOTICE 'Deleted all runtime activity instances';
    
    -- Delete runtime executions (process instances) - must be last
    SELECT COUNT(*) INTO exec_count FROM act_ru_execution;
    DELETE FROM act_ru_execution;
    RAISE NOTICE 'Deleted % runtime executions', exec_count;
END $$;

-- Delete Flowable historical data
DO $$
DECLARE
    hist_proc_count INTEGER;
    hist_act_count INTEGER;
    hist_var_count INTEGER;
BEGIN
    -- Delete historical variables
    SELECT COUNT(*) INTO hist_var_count FROM act_hi_varinst;
    DELETE FROM act_hi_varinst;
    RAISE NOTICE 'Deleted % historical variables', hist_var_count;
    
    -- Delete historical activity instances
    SELECT COUNT(*) INTO hist_act_count FROM act_hi_actinst;
    DELETE FROM act_hi_actinst;
    RAISE NOTICE 'Deleted % historical activity instances', hist_act_count;
    
    -- Delete historical process instances
    SELECT COUNT(*) INTO hist_proc_count FROM act_hi_procinst;
    DELETE FROM act_hi_procinst;
    RAISE NOTICE 'Deleted % historical process instances', hist_proc_count;
    
    -- Delete other historical data
    DELETE FROM act_hi_detail;
    DELETE FROM act_hi_comment;
    DELETE FROM act_hi_attachment;
    DELETE FROM act_hi_identitylink;
    DELETE FROM act_hi_entitylink;
    DELETE FROM act_hi_tsk_log;
    RAISE NOTICE 'Deleted all other Flowable historical data';
END $$;

-- Delete Flowable process definitions and deployments
DO $$
DECLARE
    procdef_count INTEGER;
    deploy_count INTEGER;
    bytearray_count INTEGER;
BEGIN
    -- Delete process definitions
    SELECT COUNT(*) INTO procdef_count FROM act_re_procdef;
    DELETE FROM act_re_procdef;
    RAISE NOTICE 'Deleted % process definitions from Flowable', procdef_count;
    
    -- Delete byte arrays first (has FK to deployment)
    SELECT COUNT(*) INTO bytearray_count FROM act_ge_bytearray;
    DELETE FROM act_ge_bytearray;
    RAISE NOTICE 'Deleted % Flowable byte arrays', bytearray_count;
    
    -- Delete deployments (this will cascade to deployment resources)
    SELECT COUNT(*) INTO deploy_count FROM act_re_deployment;
    DELETE FROM act_re_deployment;
    RAISE NOTICE 'Deleted % deployments from Flowable', deploy_count;
END $$;

-- Delete process instances (no foreign keys pointing to it)
DO $$
BEGIN
    DELETE FROM up_process_instance;
    RAISE NOTICE 'Deleted all process instances';
END $$;

-- Delete process definitions (references function units)
DO $$
BEGIN
    DELETE FROM dw_process_definitions;
    RAISE NOTICE 'Deleted all process definitions';
END $$;

-- Delete form definitions (references function units)
DO $$
BEGIN
    DELETE FROM dw_form_definitions;
    RAISE NOTICE 'Deleted all form definitions';
END $$;

-- Delete table definitions (references function units)
DO $$
BEGIN
    DELETE FROM dw_table_definitions;
    RAISE NOTICE 'Deleted all table definitions';
END $$;

-- Delete action definitions (references function units)
DO $$
BEGIN
    DELETE FROM dw_action_definitions;
    RAISE NOTICE 'Deleted all action definitions';
END $$;

-- Delete field definitions (references table definitions, but we already deleted tables)
-- This should be handled by CASCADE, but let's be explicit
DO $$
BEGIN
    DELETE FROM dw_field_definitions;
    RAISE NOTICE 'Deleted all field definitions';
END $$;

-- Delete function unit access records (references function units)
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'dw_function_unit_access') THEN
        DELETE FROM dw_function_unit_access;
        RAISE NOTICE 'Deleted all function unit access records';
    ELSE
        RAISE NOTICE 'Table dw_function_unit_access does not exist, skipping';
    END IF;
END $$;

-- Delete versions table (if exists, references function units)
DO $$
BEGIN
    DELETE FROM dw_versions WHERE 1=1;
    RAISE NOTICE 'Deleted all version records';
END $$;

-- Finally, delete function units
-- This should cascade to any remaining dependent records
DO $$
BEGIN
    DELETE FROM dw_function_units;
    RAISE NOTICE 'Deleted all function units';
END $$;

-- Delete sys_function_units (production environment)
DO $$
BEGIN
    -- First delete all dependent records
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'sys_function_unit_contents') THEN
        DELETE FROM sys_function_unit_contents;
        RAISE NOTICE 'Deleted all sys_function_unit_contents';
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'sys_function_unit_access') THEN
        DELETE FROM sys_function_unit_access;
        RAISE NOTICE 'Deleted all sys_function_unit_access';
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'sys_process_definitions') THEN
        DELETE FROM sys_process_definitions;
        RAISE NOTICE 'Deleted all sys_process_definitions';
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'sys_form_definitions') THEN
        DELETE FROM sys_form_definitions;
        RAISE NOTICE 'Deleted all sys_form_definitions';
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'sys_table_definitions') THEN
        DELETE FROM sys_table_definitions;
        RAISE NOTICE 'Deleted all sys_table_definitions';
    END IF;
    
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'sys_action_definitions') THEN
        DELETE FROM sys_action_definitions;
        RAISE NOTICE 'Deleted all sys_action_definitions';
    END IF;
    
    DELETE FROM sys_function_units;
    RAISE NOTICE 'Deleted all sys_function_units';
END $$;

-- Display final counts
DO $$
DECLARE
    fu_count INTEGER;
    proc_def_count INTEGER;
    proc_inst_count INTEGER;
    task_count INTEGER;
    hist_task_count INTEGER;
BEGIN
    SELECT COUNT(*) INTO fu_count FROM dw_function_units;
    SELECT COUNT(*) INTO proc_def_count FROM dw_process_definitions;
    SELECT COUNT(*) INTO proc_inst_count FROM up_process_instance;
    SELECT COUNT(*) INTO task_count FROM act_ru_task;
    SELECT COUNT(*) INTO hist_task_count FROM act_hi_taskinst;
    
    RAISE NOTICE 'Final data counts:';
    RAISE NOTICE '  - Function Units: %', fu_count;
    RAISE NOTICE '  - Process Definitions: %', proc_def_count;
    RAISE NOTICE '  - Process Instances: %', proc_inst_count;
    RAISE NOTICE '  - Runtime Tasks: %', task_count;
    RAISE NOTICE '  - Historical Tasks: %', hist_task_count;
    
    IF fu_count = 0 AND proc_def_count = 0 AND proc_inst_count = 0 AND task_count = 0 AND hist_task_count = 0 THEN
        RAISE NOTICE 'Cleanup completed successfully - all data removed';
    ELSE
        RAISE WARNING 'Some data may remain - please check manually';
    END IF;
END $$;

COMMIT;

DO $$
BEGIN
    RAISE NOTICE 'Database cleanup completed. You can now start fresh testing.';
END $$;
