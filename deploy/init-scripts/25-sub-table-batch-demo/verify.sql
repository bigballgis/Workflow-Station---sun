\set ON_ERROR_STOP on
-- Run with psql -f .../verify.sql. Every design write is rolled back.
-- Requires no pre-existing sub-table-batch-demo FU, to test creation rather than skip.
BEGIN;
DO $precondition$
BEGIN
    IF EXISTS (SELECT 1 FROM dw_function_units WHERE code = 'sub-table-batch-demo') THEN
        RAISE EXCEPTION 'Use a test database without sub-table-batch-demo; verification does not reset existing designs.';
    END IF;
END $precondition$;
\ir 00-init.sql

DO $verify$
DECLARE
    v_fu bigint;
    v_form record;
    v_binding record;
    v_rule jsonb;
    v_xml xml;
BEGIN
    SELECT id INTO STRICT v_fu FROM dw_function_units WHERE code = 'sub-table-batch-demo';
    IF (SELECT count(*) FROM dw_form_definitions WHERE function_unit_id = v_fu) <> 3
        OR (SELECT count(*) FROM dw_table_definitions WHERE function_unit_id = v_fu) <> 2
        OR (SELECT count(*) FROM dw_function_unit_dev_groups WHERE function_unit_id = v_fu) <> 1 THEN
        RAISE EXCEPTION 'Expected three forms, two tables and one development group assignment.';
    END IF;
    FOR v_form IN SELECT * FROM dw_form_definitions WHERE function_unit_id = v_fu LOOP
        SELECT * INTO STRICT v_binding FROM dw_form_table_bindings
            WHERE form_id = v_form.id AND binding_type = 'SUB';
        SELECT value INTO STRICT v_rule FROM jsonb_array_elements(v_form.config_json->'rule')
            WHERE value->>'type' = 'subTable';
        IF v_rule ?| ARRAY['field', 'value', 'validate'] OR v_rule->'props' ? '_bindingId'
            OR (v_rule->>'_bindingId')::bigint IS DISTINCT FROM v_binding.id THEN
            RAISE EXCEPTION 'Nonstandard Sub-Table or stale binding on form %', v_form.id;
        END IF;
        IF (SELECT count(*) FROM dw_sub_table_view_fields WHERE view_config_id = v_binding.sub_list_view_id) <> 6 THEN
            RAISE EXCEPTION 'Expected six list columns on form %', v_form.id;
        END IF;
        IF v_form.form_name LIKE '03 - %' THEN
            IF v_binding.binding_link_mode <> 'miParticipantRow' OR v_binding.foreign_key_field <> 'line_key'
                OR (v_rule->'props'->>'allowBatchAssign')::boolean
                OR (v_rule->'props'->>'allowBatchAdd')::boolean
                OR (v_rule->'props'->>'allowBatchEdit')::boolean
                OR (v_rule->'props'->>'allowBatchDelete')::boolean THEN
                RAISE EXCEPTION 'Child must be current-row scoped with batch operations disabled.';
            END IF;
        ELSE
            IF v_binding.binding_link_mode <> 'structuralFk' OR v_binding.foreign_key_field <> 'case_ref'
                OR v_binding.filter_fk_field_id IS NULL
                OR NOT (v_rule->'props'->>'allowBatchAssign')::boolean
                OR NOT (v_rule->'props'->>'allowBatchAdd')::boolean
                OR NOT (v_rule->'props'->>'allowBatchEdit')::boolean
                OR NOT (v_rule->'props'->>'allowBatchDelete')::boolean
                OR (v_form.config_json->'batchForms'->v_binding.id::text->>'schemaVersion')::int IS DISTINCT FROM 1
                OR NOT jsonb_path_exists(v_form.config_json->'batchForms'->v_binding.id::text,
                    '$.rule[*] ? (@.type == "miAssignment")') THEN
                RAISE EXCEPTION 'Source form needs structural FK, all four flags and version 1 assignment Batch Form.';
            END IF;
        END IF;
    END LOOP;
    SELECT convert_from(decode(bpmn_xml, 'base64'), 'UTF8')::xml INTO STRICT v_xml
        FROM dw_process_definitions WHERE function_unit_id = v_fu;
    IF cardinality(xpath('//*[local-name()="BPMNShape"]', v_xml)) <> 8
        OR cardinality(xpath('//*[local-name()="BPMNEdge"]', v_xml)) <> 6
        OR cardinality(xpath('//*[local-name()="multiInstanceLoopCharacteristics"]', v_xml)) <> 1 THEN
        RAISE EXCEPTION 'Incomplete BPMN layout or missing multi-instance subprocess.';
    END IF;
    IF EXISTS (SELECT 1 FROM dw_form_stage_bindings s JOIN dw_form_definitions f ON f.id = s.form_id
        WHERE f.function_unit_id = v_fu AND NOT xpath_exists('//*[@id="' || s.stage_id || '"]', v_xml)) THEN
        RAISE EXCEPTION 'Form stage binding references a missing BPMN node.';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM dw_field_definitions d JOIN dw_table_definitions t ON t.id = d.table_id
        WHERE t.function_unit_id = v_fu AND d.field_name = 'line_key'
        AND d.pk_generation_json->>'strategy' = 'uuid') THEN
        RAISE EXCEPTION 'Expected UUID line key.';
    END IF;
    UPDATE dw_form_definitions SET display_name = 'PRESERVE-USER-EDIT' WHERE function_unit_id = v_fu;
    RAISE NOTICE 'PASS: standard components, binding IDs, UUID, Batch Forms, four flags, child scope and BPMN graph.';
END $verify$;

\ir 00-init.sql
DO $idempotency$
BEGIN
    IF (SELECT count(*) FROM dw_form_definitions f JOIN dw_function_units u ON u.id = f.function_unit_id
        WHERE u.code = 'sub-table-batch-demo' AND f.display_name = 'PRESERVE-USER-EDIT') <> 3 THEN
        RAISE EXCEPTION 'Installer overwrote subsequent designer edits.';
    END IF;
    RAISE NOTICE 'PASS: repeat installation preserves edits.';
END $idempotency$;
ROLLBACK;
