BEGIN;
UPDATE dw_form_definitions SET form_type = 'PROCESS' WHERE form_type = 'MAIN';
UPDATE dw_form_definitions SET form_type = 'TASK' WHERE form_type = 'SUB';
DELETE FROM dw_form_definitions WHERE form_type = 'POPUP';
COMMIT;
