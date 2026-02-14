-- =============================================================================
-- Insert Table Design for Simple Approval
-- Exported from database on 2026-02-14
-- =============================================================================

DO $table_design$
DECLARE
    v_function_unit_id BIGINT;
    v_table_id BIGINT;
BEGIN
    -- =========================================================================
    -- Get Function Unit ID
    -- =========================================================================
    SELECT id INTO v_function_unit_id
    FROM dw_function_units
    WHERE code = 'SIMPLE_APPROVAL';

    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit SIMPLE_APPROVAL not found. Run 00-create-simple-approval.sql first.';
    END IF;

    -- =========================================================================
    -- Step 1: Create Table Definition
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
        'Request',
        'MAIN',
        'Main request table',
        CURRENT_TIMESTAMP,
        CURRENT_TIMESTAMP
    )
    ON CONFLICT (function_unit_id, table_name) DO UPDATE SET
        table_type = EXCLUDED.table_type,
        description = EXCLUDED.description,
        updated_at = CURRENT_TIMESTAMP
    RETURNING id INTO v_table_id;

    RAISE NOTICE 'Table definition created/updated with ID: %', v_table_id;

    -- =========================================================================
    -- Step 2: Delete existing field definitions to avoid conflicts
    -- =========================================================================
    DELETE FROM dw_field_definitions WHERE table_id = v_table_id;
    RAISE NOTICE 'Existing field definitions deleted for table ID: %', v_table_id;

    -- =========================================================================
    -- Step 3: Create Field Definitions
    -- =========================================================================
    
    -- Field 1: id (Primary Key)
    INSERT INTO dw_field_definitions (
        table_id,
        field_name,
        data_type,
        length,
        precision_value,
        scale,
        nullable,
        default_value,
        is_primary_key,
        is_unique,
        description,
        sort_order
    ) VALUES (
        v_table_id,
        'id',
        'BIGINT',
        NULL,
        NULL,
        NULL,
        false,
        NULL,
        false,
        false,
        'Primary key',
        1
    );

    -- Field 2: request_number
    INSERT INTO dw_field_definitions (
        table_id,
        field_name,
        data_type,
        length,
        precision_value,
        scale,
        nullable,
        default_value,
        is_primary_key,
        is_unique,
        description,
        sort_order
    ) VALUES (
        v_table_id,
        'request_number',
        'VARCHAR',
        50,
        NULL,
        NULL,
        false,
        NULL,
        false,
        false,
        'Request number (unique)',
        2
    );

    -- Field 3: request_date
    INSERT INTO dw_field_definitions (
        table_id,
        field_name,
        data_type,
        length,
        precision_value,
        scale,
        nullable,
        default_value,
        is_primary_key,
        is_unique,
        description,
        sort_order
    ) VALUES (
        v_table_id,
        'request_date',
        'TIMESTAMP',
        NULL,
        NULL,
        NULL,
        false,
        NULL,
        false,
        false,
        'Request date',
        3
    );

    -- Field 4: title
    INSERT INTO dw_field_definitions (
        table_id,
        field_name,
        data_type,
        length,
        precision_value,
        scale,
        nullable,
        default_value,
        is_primary_key,
        is_unique,
        description,
        sort_order
    ) VALUES (
        v_table_id,
        'title',
        'VARCHAR',
        200,
        NULL,
        NULL,
        false,
        NULL,
        false,
        false,
        'Request title',
        4
    );

    -- Field 5: description
    INSERT INTO dw_field_definitions (
        table_id,
        field_name,
        data_type,
        length,
        precision_value,
        scale,
        nullable,
        default_value,
        is_primary_key,
        is_unique,
        description,
        sort_order
    ) VALUES (
        v_table_id,
        'description',
        'TEXT',
        NULL,
        NULL,
        NULL,
        false,
        NULL,
        false,
        false,
        'Request description',
        5
    );

    -- Field 6: status
    INSERT INTO dw_field_definitions (
        table_id,
        field_name,
        data_type,
        length,
        precision_value,
        scale,
        nullable,
        default_value,
        is_primary_key,
        is_unique,
        description,
        sort_order
    ) VALUES (
        v_table_id,
        'status',
        'VARCHAR',
        30,
        NULL,
        NULL,
        false,
        NULL,
        false,
        false,
        'Request status',
        6
    );

    -- Field 7: approval_comments
    INSERT INTO dw_field_definitions (
        table_id,
        field_name,
        data_type,
        length,
        precision_value,
        scale,
        nullable,
        default_value,
        is_primary_key,
        is_unique,
        description,
        sort_order
    ) VALUES (
        v_table_id,
        'approval_comments',
        'TEXT',
        NULL,
        NULL,
        NULL,
        true,
        NULL,
        false,
        false,
        'Approval comments',
        7
    );

    -- Field 8: created_by
    INSERT INTO dw_field_definitions (
        table_id,
        field_name,
        data_type,
        length,
        precision_value,
        scale,
        nullable,
        default_value,
        is_primary_key,
        is_unique,
        description,
        sort_order
    ) VALUES (
        v_table_id,
        'created_by',
        'VARCHAR',
        100,
        NULL,
        NULL,
        false,
        NULL,
        false,
        false,
        'Created by',
        8
    );

    -- Field 9: created_at
    INSERT INTO dw_field_definitions (
        table_id,
        field_name,
        data_type,
        length,
        precision_value,
        scale,
        nullable,
        default_value,
        is_primary_key,
        is_unique,
        description,
        sort_order
    ) VALUES (
        v_table_id,
        'created_at',
        'TIMESTAMP',
        NULL,
        NULL,
        NULL,
        false,
        NULL,
        false,
        false,
        'Created at',
        9
    );

    -- Field 10: updated_at
    INSERT INTO dw_field_definitions (
        table_id,
        field_name,
        data_type,
        length,
        precision_value,
        scale,
        nullable,
        default_value,
        is_primary_key,
        is_unique,
        description,
        sort_order
    ) VALUES (
        v_table_id,
        'updated_at',
        'TIMESTAMP',
        NULL,
        NULL,
        NULL,
        true,
        NULL,
        false,
        false,
        'Updated at',
        10
    );

    -- =========================================================================
    -- Summary
    -- =========================================================================
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Table Design Setup Complete!';
    RAISE NOTICE '========================================';
    RAISE NOTICE 'Table ID: %', v_table_id;
    RAISE NOTICE 'Table Name: Request';
    RAISE NOTICE 'Fields Count: 10';
    RAISE NOTICE '========================================';

END $table_design$;
