-- =============================================================================
-- Insert Additional Table Types for Simple Approval Workflow
-- Created on 2026-02-20
-- Demonstrates SUB, ACTION, and RELATION table types
-- =============================================================================

DO $additional_tables$
DECLARE
    v_function_unit_id BIGINT;
    v_main_table_id BIGINT;
    v_sub_table_id BIGINT;
    v_action_table_id BIGINT;
    v_relation_table_id BIGINT;
BEGIN
    -- =========================================================================
    -- Get Function Unit ID
    -- =========================================================================
    SELECT id INTO v_function_unit_id
    FROM dw_function_units
    WHERE code = 'fu-20260403-a1b2c0';

    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit fu-20260403-a1b2c0 not found. Run 00-create-simple-approval.sql first.';
    END IF;

    -- Get main table ID for reference
    SELECT id INTO v_main_table_id
    FROM dw_table_definitions
    WHERE function_unit_id = v_function_unit_id AND table_name = 'Request';

    -- =========================================================================
    -- Step 1: Create SUB Table - Request Items (子表)
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id,
        table_name,
        table_type,
        description,
        created_at,
        updated_at
    ) VALUES (
        v_function_unit_id,
        'RequestItems',
        'SUB',
        'Sub table for request line items',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_type = EXCLUDED.table_type,
        display_name = EXCLUDED.display_name,
        updated_at = CURRENT_TIMESTAMP
    RETURNING id INTO v_sub_table_id;

    RAISE NOTICE 'SUB table created/updated with ID: %', v_sub_table_id;

    -- Delete existing field definitions
    DELETE FROM dw_field_definitions WHERE table_id = v_sub_table_id;

    -- Create fields for SUB table
    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, display_name, sort_order
    ) VALUES
    (v_sub_table_id, 'id', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, 'Item ID', 1),
    (v_sub_table_id, 'request_id', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, 'Foreign key to Request table', 2),
    (v_sub_table_id, 'item_name', 'VARCHAR', 200, NULL, NULL, false, NULL, false, false, 'Item name', 3),
    (v_sub_table_id, 'quantity', 'INTEGER', NULL, NULL, NULL, false, NULL, false, false, 'Quantity', 4),
    (v_sub_table_id, 'unit_price', 'DECIMAL', NULL, 10, 2, false, NULL, false, false, 'Unit price', 5),
    (v_sub_table_id, 'total_price', 'DECIMAL', NULL, 10, 2, false, NULL, false, false, 'Total price', 6),
    (v_sub_table_id, 'remarks', 'TEXT', NULL, NULL, NULL, true, NULL, false, false, 'Item remarks', 7),
    (v_sub_table_id, 'sort_order', 'INTEGER', NULL, NULL, NULL, false, NULL, false, false, 'Display order', 8);

    RAISE NOTICE 'SUB table fields created: 8 fields';

    -- =========================================================================
    -- Step 2: Create ACTION Table - Approval Actions (动作表)
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id,
        table_name,
        table_type,
        description,
        created_at,
        updated_at
    ) VALUES (
        v_function_unit_id,
        'ApprovalActions',
        'ACTION',
        'Action table for tracking approval history',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_type = EXCLUDED.table_type,
        display_name = EXCLUDED.display_name,
        updated_at = CURRENT_TIMESTAMP
    RETURNING id INTO v_action_table_id;

    RAISE NOTICE 'ACTION table created/updated with ID: %', v_action_table_id;

    -- Delete existing field definitions
    DELETE FROM dw_field_definitions WHERE table_id = v_action_table_id;

    -- Create fields for ACTION table
    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, display_name, sort_order
    ) VALUES
    (v_action_table_id, 'id', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, 'Action ID', 1),
    (v_action_table_id, 'request_id', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, 'Foreign key to Request table', 2),
    (v_action_table_id, 'action_type', 'VARCHAR', 50, NULL, NULL, false, NULL, false, false, 'Action type (SUBMIT/APPROVE/REJECT)', 3),
    (v_action_table_id, 'action_by', 'VARCHAR', 100, NULL, NULL, false, NULL, false, false, 'User who performed action', 4),
    (v_action_table_id, 'action_at', 'TIMESTAMP', NULL, NULL, NULL, false, NULL, false, false, 'Action timestamp', 5),
    (v_action_table_id, 'comments', 'TEXT', NULL, NULL, NULL, true, NULL, false, false, 'Action comments', 6),
    (v_action_table_id, 'previous_status', 'VARCHAR', 30, NULL, NULL, true, NULL, false, false, 'Status before action', 7),
    (v_action_table_id, 'new_status', 'VARCHAR', 30, NULL, NULL, false, NULL, false, false, 'Status after action', 8),
    (v_action_table_id, 'ip_address', 'VARCHAR', 50, NULL, NULL, true, NULL, false, false, 'IP address of action', 9);

    RAISE NOTICE 'ACTION table fields created: 9 fields';

    -- =========================================================================
    -- Step 3: Create RELATION Table - Request Attachments (关联表)
    -- =========================================================================
    INSERT INTO dw_table_definitions (
        function_unit_id,
        table_name,
        table_type,
        description,
        created_at,
        updated_at
    ) VALUES (
        v_function_unit_id,
        'RequestAttachments',
        'RELATION',
        'Relation table for request attachments',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_type = EXCLUDED.table_type,
        display_name = EXCLUDED.display_name,
        updated_at = CURRENT_TIMESTAMP
    RETURNING id INTO v_relation_table_id;

    RAISE NOTICE 'RELATION table created/updated with ID: %', v_relation_table_id;

    -- Delete existing field definitions
    DELETE FROM dw_field_definitions WHERE table_id = v_relation_table_id;

    -- Create fields for RELATION table
    INSERT INTO dw_field_definitions (
        table_id, field_name, data_type, length, precision_value, scale,
        nullable, default_value, is_primary_key, is_unique, display_name, sort_order
    ) VALUES
    (v_relation_table_id, 'id', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, 'Attachment ID', 1),
    (v_relation_table_id, 'request_id', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, 'Foreign key to Request table', 2),
    (v_relation_table_id, 'file_name', 'VARCHAR', 255, NULL, NULL, false, NULL, false, false, 'Original file name', 3),
    (v_relation_table_id, 'file_path', 'VARCHAR', 500, NULL, NULL, false, NULL, false, false, 'File storage path', 4),
    (v_relation_table_id, 'file_size', 'BIGINT', NULL, NULL, NULL, false, NULL, false, false, 'File size in bytes', 5),
    (v_relation_table_id, 'file_type', 'VARCHAR', 100, NULL, NULL, false, NULL, false, false, 'MIME type', 6),
    (v_relation_table_id, 'uploaded_by', 'VARCHAR', 100, NULL, NULL, false, NULL, false, false, 'User who uploaded', 7),
    (v_relation_table_id, 'uploaded_at', 'TIMESTAMP', NULL, NULL, NULL, false, NULL, false, false, 'Upload timestamp', 8),
    (v_relation_table_id, 'description', 'TEXT', NULL, NULL, NULL, true, NULL, false, false, 'Attachment description', 9);

    RAISE NOTICE 'RELATION table fields created: 9 fields';

    -- =========================================================================
    -- Summary
    -- =========================================================================
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Additional Tables Setup Complete!';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Function Unit ID: %', v_function_unit_id;
    RAISE NOTICE 'Main Table ID: %', v_main_table_id;
    RAISE NOTICE 'SUB Table ID: % (RequestItems)', v_sub_table_id;
    RAISE NOTICE 'ACTION Table ID: % (ApprovalActions)', v_action_table_id;
    RAISE NOTICE 'RELATION Table ID: % (RequestAttachments)', v_relation_table_id;
    RAISE NOTICE '========================================';
    RAISE NOTICE 'All 4 table types are now available:';
    RAISE NOTICE '  - MAIN: Request (primary data)';
    RAISE NOTICE '  - SUB: RequestItems (line items)';
    RAISE NOTICE '  - ACTION: ApprovalActions (audit trail)';
    RAISE NOTICE '  - RELATION: RequestAttachments (file links)';
    RAISE NOTICE '========================================';

END $additional_tables$;
