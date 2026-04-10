-- =============================================================================
-- 16-meeting-participant-collection: Create Table Definitions & Field Definitions
-- 会议参与人信息收集：创建数据表定义和字段定义
-- Tables: meeting(MAIN), participants(SUB)
--
-- Dependencies: 00-create-function-unit.sql
-- Execution order: 00 → 01 → 02 → 03 → 04
-- =============================================================================

DO $tables$
DECLARE
    v_function_unit_id    BIGINT;
    v_meeting_table_id    BIGINT;  -- meeting (MAIN)
    v_participant_table_id BIGINT; -- participants (SUB)
BEGIN
    SELECT id INTO v_function_unit_id FROM dw_function_units WHERE code = 'fu-20260403-a1b2c5';
    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit fu-20260403-a1b2c5 not found. Run 00-create-function-unit.sql first.';
    END IF;

    -- =========================================================================
    -- Table 1: meeting (MAIN)
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_display_name, table_type, description, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'meeting', 'Meeting Info', 'MAIN',
        'Main meeting table - stores meeting basic info',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_display_name = EXCLUDED.table_display_name,
        table_type  = EXCLUDED.table_type,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_meeting_table_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_meeting_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, description, sort_order
    ) VALUES
    (v_meeting_table_id, 'id',             'BIGINT',    NULL, NULL, NULL, false, NULL, false, false, 'Primary key',    1),
    (v_meeting_table_id, 'topic',          'VARCHAR',   200,  NULL, NULL, false, NULL, false, false, 'Meeting topic',  2),
    (v_meeting_table_id, 'meeting_time',   'TIMESTAMP', NULL, NULL, NULL, false, NULL, false, false, 'Meeting time',   3),
    (v_meeting_table_id, 'location',       'VARCHAR',   200,  NULL, NULL, false, NULL, false, false, 'Meeting location',4),
    (v_meeting_table_id, 'organizer_name', 'VARCHAR',   100,  NULL, NULL, false, NULL, false, false, 'Organizer name', 5),
    (v_meeting_table_id, 'description',    'TEXT',      NULL, NULL, NULL, true,  NULL, false, false, 'Meeting description',6),
    (v_meeting_table_id, 'status',         'VARCHAR',   30,   NULL, NULL, false, '''DRAFT''', false, false, 'Meeting status',7),
    (v_meeting_table_id, 'created_by',     'VARCHAR',   100,  NULL, NULL, false, NULL, false, false, 'Created by',     8),
    (v_meeting_table_id, 'created_at',     'TIMESTAMP', NULL, NULL, NULL, false, NULL, false, false, 'Created at',     9),
    (v_meeting_table_id, 'updated_at',     'TIMESTAMP', NULL, NULL, NULL, true,  NULL, false, false, 'Updated at',     10);

    RAISE NOTICE 'Table meeting (MAIN) created: id=%', v_meeting_table_id;

    -- =========================================================================
    -- Table 2: participants (SUB) - 多实例子流程数据源
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id, table_name, table_display_name, table_type, description, created_at, updated_at
    ) VALUES (
        v_function_unit_id, 'participants', 'Participants', 'SUB',
        'Sub table for meeting participants - serves as multi-instance data source',
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_display_name = EXCLUDED.table_display_name,
        table_type  = EXCLUDED.table_type,
        description = EXCLUDED.description,
        updated_at  = CURRENT_TIMESTAMP
    RETURNING id INTO v_participant_table_id;

    DELETE FROM dw_field_definitions WHERE table_id = v_participant_table_id;

    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, description, sort_order
    ) VALUES
    (v_participant_table_id, 'id',                  'BIGINT',  NULL, NULL, NULL, false, NULL, false, false, 'Primary key',                                      1),
    (v_participant_table_id, 'meeting_id',          'BIGINT',  NULL, NULL, NULL, false, NULL, false, false, 'Foreign key - references meeting table',            2),
    (v_participant_table_id, 'name',                'VARCHAR', 100,  NULL, NULL, false, NULL, false, false, 'Participant name',                                  3),
    (v_participant_table_id, 'department',          'VARCHAR', 100,  NULL, NULL, true,  NULL, false, false, 'Department',                                        4),
    (v_participant_table_id, 'email',               'VARCHAR', 255,  NULL, NULL, true,  NULL, false, false, 'Email',                                             5),
    (v_participant_table_id, 'assignee_user_id',    'VARCHAR', 64,   NULL, NULL, true,  NULL, false, false, 'Assignee user ID (multi-instance assigneeField)',   6),
    (v_participant_table_id, 'assignee_display_name','VARCHAR', 200, NULL, NULL, true,  NULL, false, false, 'Assignee display name',                             7),
    (v_participant_table_id, 'attend_status',       'VARCHAR', 20,   NULL, NULL, true,  NULL, false, false, 'Attendance status (YES/NO/PENDING)',                8),
    (v_participant_table_id, 'dietary_preference',  'VARCHAR', 30,   NULL, NULL, true,  NULL, false, false, 'Dietary preference (NONE/VEGETARIAN/HALAL/OTHER)', 9),
    (v_participant_table_id, 'remark',              'TEXT',    NULL, NULL, NULL, true,  NULL, false, false, 'Remarks',                                           10),
    (v_participant_table_id, 'sort_order',          'INTEGER', NULL, NULL, NULL, false, '0',  false, false, 'Sort order',                                        11);

    -- 注意：row_version 列会由 TableDesignComponentImpl 在建表时自动添加（Task 1.2 实现）

    RAISE NOTICE 'Table participants (SUB) created: id=%', v_participant_table_id;

    -- =========================================================================
    -- Summary
    -- =========================================================================
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Tables Setup Complete!';
    RAISE NOTICE 'meeting (MAIN)       : id=%', v_meeting_table_id;
    RAISE NOTICE 'participants (SUB)   : id=%', v_participant_table_id;
    RAISE NOTICE 'Next: run 02-create-bpmn-process.sql';
    RAISE NOTICE '========================================';

END $tables$;
