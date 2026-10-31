-- This trigger publishes presence only; it takes no case or authority lock after a business-row lock.
CREATE OR REPLACE FUNCTION assignment_portal_source_present() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    INSERT INTO up_assignment_source_presence(process_instance_id) VALUES(NEW.id)
        ON CONFLICT(process_instance_id) DO NOTHING;
    RETURN NEW;
END;
$$;
DROP TRIGGER IF EXISTS trg_assignment_source_present ON up_process_instance;
CREATE TRIGGER trg_assignment_source_present AFTER INSERT OR UPDATE OF variables ON up_process_instance
    FOR EACH ROW EXECUTE FUNCTION assignment_portal_source_present();
INSERT INTO up_assignment_source_presence(process_instance_id) SELECT id FROM up_process_instance
    ON CONFLICT(process_instance_id) DO NOTHING;
