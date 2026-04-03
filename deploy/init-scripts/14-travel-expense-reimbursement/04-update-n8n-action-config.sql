-- =============================================================================
-- 14-travel-expense-reimbursement: Update N8N Action Config
-- 差旅报销功能单元：更新 N8N 动作配置为新的扩展格式
--
-- Changes:
--   1. Add `frontendOutputMapping` to dw_action_definitions (AI 识别发票) configJson
--      - SubTable_Mapping for expense_items (append mode)
--      - SubTable_Mapping for invoices (update mode)
--      - Main_Form_Mapping for total_amount (sum aggregation)
--   2. Add `fileNameTargetField` to invoices sub-form upload column props
--
-- The existing `outputMapping` (backend format) is kept unchanged.
-- The new `frontendOutputMapping` is used by the frontend AutoFill_Engine.
--
-- NOTE: This script updates dw_action_definitions (developer-workstation source).
--       When deployed to admin-center via the deployment pipeline, the updated
--       configJson (including frontendOutputMapping) will be exported and imported
--       into sys_action_definitions automatically.
--
-- Dependencies: 03-form-table-bindings.sql (Form Table Bindings)
-- Execution order: 00 → 01 → 02 → 03 → 04
-- =============================================================================

DO $migration$
DECLARE
    v_function_unit_id      BIGINT;
    v_n8n_action_id         BIGINT;
    v_reimbursement_form_id BIGINT;
    v_items_table_id        BIGINT;
    v_invoices_table_id     BIGINT;
    v_binding_items_id      BIGINT;
    v_binding_invoices_id   BIGINT;
    v_frontend_mapping      JSONB;
    v_invoices_binding_key  TEXT;
    v_current_config        JSONB;
    v_sub_forms             JSONB;
    v_invoices_sub_form     JSONB;
    v_rule_array            JSONB;
    v_updated_rule          JSONB;
    v_elem                  JSONB;
    v_elem_props            JSONB;
    v_idx                   INT;
BEGIN
    -- -----------------------------------------------------------------
    -- Look up IDs
    -- -----------------------------------------------------------------
    SELECT id INTO v_function_unit_id
    FROM dw_function_units
    WHERE code = 'fu-20260403-a1b2c3';

    IF v_function_unit_id IS NULL THEN
        RAISE EXCEPTION 'Function unit fu-20260403-a1b2c3 not found.';
    END IF;

    SELECT id INTO v_n8n_action_id
    FROM dw_action_definitions
    WHERE function_unit_id = v_function_unit_id AND action_name = 'AI 识别发票';

    IF v_n8n_action_id IS NULL THEN
        RAISE EXCEPTION 'N8N action "AI 识别发票" not found.';
    END IF;

    SELECT id INTO v_reimbursement_form_id
    FROM dw_form_definitions
    WHERE function_unit_id = v_function_unit_id AND form_name = 'Reimbursement Form';

    SELECT id INTO v_items_table_id
    FROM dw_table_definitions
    WHERE function_unit_id = v_function_unit_id AND table_name = 'expense_items';

    SELECT id INTO v_invoices_table_id
    FROM dw_table_definitions
    WHERE function_unit_id = v_function_unit_id AND table_name = 'invoices';

    IF v_reimbursement_form_id IS NULL OR v_items_table_id IS NULL OR v_invoices_table_id IS NULL THEN
        RAISE EXCEPTION 'Required form or table definitions not found.';
    END IF;

    -- Look up form_table_binding IDs for the sub-tables
    SELECT id INTO v_binding_items_id
    FROM dw_form_table_bindings
    WHERE form_id = v_reimbursement_form_id AND table_id = v_items_table_id AND binding_type = 'SUB';

    SELECT id INTO v_binding_invoices_id
    FROM dw_form_table_bindings
    WHERE form_id = v_reimbursement_form_id AND table_id = v_invoices_table_id AND binding_type = 'SUB';

    IF v_binding_items_id IS NULL OR v_binding_invoices_id IS NULL THEN
        RAISE EXCEPTION 'Sub-table bindings not found. Run 03-form-table-bindings.sql first.';
    END IF;

    -- -----------------------------------------------------------------
    -- 1. Build frontendOutputMapping JSON array
    -- -----------------------------------------------------------------
    v_frontend_mapping := jsonb_build_array(
        -- SubTable_Mapping for expense_items (append mode)
        jsonb_build_object(
            'targetType', 'sub_table',
            'targetBindingId', v_binding_items_id,
            'sourceArrayKey', 'InvoiceRecognitionResults',
            'fillMode', 'append',
            'fieldMappings', jsonb_build_array(
                jsonb_build_object(
                    'sourceField', 'invoiceType',
                    'targetField', 'expense_type',
                    'valueMapping', jsonb_build_object(
                        '火车票', '交通',
                        '机票行程单', '交通',
                        '出租车发票', '交通',
                        '酒店住宿发票', '住宿',
                        '餐饮发票', '餐饮'
                    ),
                    'defaultValue', '其他'
                ),
                jsonb_build_object('sourceField', 'invoiceDate', 'targetField', 'expense_date'),
                jsonb_build_object('sourceField', 'totalAmount', 'targetField', 'amount'),
                jsonb_build_object('sourceField', 'description', 'targetField', 'description')
            )
        ),
        -- SubTable_Mapping for invoices (update mode)
        jsonb_build_object(
            'targetType', 'sub_table',
            'targetBindingId', v_binding_invoices_id,
            'sourceArrayKey', 'InvoiceRecognitionResults',
            'fillMode', 'update',
            'fieldMappings', jsonb_build_array(
                jsonb_build_object(
                    'targetField', 'description',
                    'formatTemplate', '{invoiceType} | No.{invoiceNumber} | ¥{totalAmount} | {invoiceDate}',
                    'separator', ' | '
                )
            )
        ),
        -- Main_Form_Mapping for total_amount (sum aggregation)
        jsonb_build_object(
            'targetType', 'field',
            'source', 'sum:InvoiceRecognitionResults.totalAmount',
            'targetField', 'total_amount'
        )
    );

    -- Add frontendOutputMapping to dw_action_definitions (developer-workstation source)
    UPDATE dw_action_definitions
    SET config_json = jsonb_set(
        config_json::jsonb,
        '{frontendOutputMapping}',
        v_frontend_mapping
    ),
    updated_at = CURRENT_TIMESTAMP
    WHERE id = v_n8n_action_id;

    RAISE NOTICE 'Updated dw_action_definitions (id=%): added frontendOutputMapping', v_n8n_action_id;

    -- Also update sys_action_definitions if it exists (for already-deployed environments)
    -- sys_action_definitions uses a different function_unit_id (UUID from sys_function_units),
    -- so we match by action_name only. This is safe because action_name is unique per function unit.
    UPDATE sys_action_definitions
    SET config_json = jsonb_set(
        config_json::jsonb,
        '{frontendOutputMapping}',
        v_frontend_mapping
    ),
    updated_at = CURRENT_TIMESTAMP
    WHERE action_name = 'AI 识别发票'
      AND action_type = 'N8N_ACTION';

    -- -----------------------------------------------------------------
    -- 2. Add fileNameTargetField to invoices sub-form upload column
    -- -----------------------------------------------------------------
    v_invoices_binding_key := v_binding_invoices_id::text;

    -- Read current config_json for the Reimbursement Form
    SELECT config_json::jsonb INTO v_current_config
    FROM dw_form_definitions
    WHERE id = v_reimbursement_form_id;

    -- Get the invoices sub-form object
    v_sub_forms := v_current_config -> 'subForms';
    v_invoices_sub_form := v_sub_forms -> v_invoices_binding_key;

    IF v_invoices_sub_form IS NULL THEN
        RAISE EXCEPTION 'Invoices sub-form not found under subForms key %', v_invoices_binding_key;
    END IF;

    -- Iterate the rule array to find the upload column and add fileNameTargetField
    v_rule_array := v_invoices_sub_form -> 'rule';
    v_updated_rule := '[]'::jsonb;

    FOR v_idx IN 0 .. jsonb_array_length(v_rule_array) - 1 LOOP
        v_elem := v_rule_array -> v_idx;
        IF v_elem ->> 'type' = 'upload' AND v_elem ->> 'field' = 'file' THEN
            -- Add fileNameTargetField to the props object
            v_elem_props := v_elem -> 'props';
            v_elem_props := v_elem_props || '{"fileNameTargetField": "file_name"}'::jsonb;
            v_elem := jsonb_set(v_elem, '{props}', v_elem_props);
        END IF;
        v_updated_rule := v_updated_rule || jsonb_build_array(v_elem);
    END LOOP;

    -- Write back the updated rule array
    UPDATE dw_form_definitions
    SET config_json = jsonb_set(
        config_json::jsonb,
        ARRAY['subForms', v_invoices_binding_key, 'rule'],
        v_updated_rule
    )
    WHERE id = v_reimbursement_form_id;

    -- -----------------------------------------------------------------
    -- Done
    -- -----------------------------------------------------------------
    RAISE NOTICE '========================================';
    RAISE NOTICE 'N8N Action Config Migration Complete!';
    RAISE NOTICE 'dw_action_definitions (id=%): added frontendOutputMapping with binding IDs: items=%, invoices=%',
        v_n8n_action_id, v_binding_items_id, v_binding_invoices_id;
    RAISE NOTICE 'sys_action_definitions: also updated if exists (function_unit_id=%, action=AI 识别发票)',
        v_function_unit_id;
    RAISE NOTICE 'Reimbursement Form (id=%): added fileNameTargetField to invoices upload column',
        v_reimbursement_form_id;
    RAISE NOTICE '========================================';

END $migration$;
