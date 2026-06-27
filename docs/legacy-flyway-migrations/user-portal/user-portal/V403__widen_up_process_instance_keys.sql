-- Align with deploy/init-scripts 03-user-portal-schema.sql and 23-widen-up-process-instance-business-key.sql
-- (V400 used VARCHAR(100) for process_definition_key and business_key.)

ALTER TABLE up_process_instance
    ALTER COLUMN process_definition_key TYPE VARCHAR(255);

ALTER TABLE up_process_instance
    ALTER COLUMN business_key TYPE VARCHAR(255);
