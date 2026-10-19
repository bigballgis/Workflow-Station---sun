-- Migrate Action config_json.postEmail (single block) to postEmailRules (array).
-- Idempotent: only rows with legacy postEmail and no postEmailRules are updated.

UPDATE dw_action_definitions
SET config_json = (config_json - 'postEmail')
    || jsonb_build_object(
        'postEmailRules',
        jsonb_build_array(
            jsonb_build_object(
                'id', 'legacy',
                'name', '',
                'enabled', (
                    config_json #>> '{postEmail,enabled}' IN ('true', 't', '1')
                    OR (config_json #>> '{postEmail,enabled}')::boolean IS TRUE
                ),
                'condition', NULL
            ) || ((config_json->'postEmail') - 'enabled')
        )
    )
WHERE config_json ? 'postEmail'
  AND NOT (config_json ? 'postEmailRules');

UPDATE sys_action_definitions
SET config_json = (config_json - 'postEmail')
    || jsonb_build_object(
        'postEmailRules',
        jsonb_build_array(
            jsonb_build_object(
                'id', 'legacy',
                'name', '',
                'enabled', (
                    config_json #>> '{postEmail,enabled}' IN ('true', 't', '1')
                    OR (config_json #>> '{postEmail,enabled}')::boolean IS TRUE
                ),
                'condition', NULL
            ) || ((config_json->'postEmail') - 'enabled')
        )
    )
WHERE config_json ? 'postEmail'
  AND NOT (config_json ? 'postEmailRules');
