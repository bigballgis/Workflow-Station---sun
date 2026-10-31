-- All supported qualification writers (JPA, batch/native SQL, imports and identity synchronization)
-- publish one version per transaction. Local snapshot readers are the only shared-lock holders.
CREATE OR REPLACE FUNCTION we_assignment_authority_lock()
RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    PERFORM set_config('lock_timeout', CASE
        WHEN current_setting('lock_timeout')::interval = interval '0'
          OR current_setting('lock_timeout')::interval > interval '2 seconds'
        THEN '2s' ELSE current_setting('lock_timeout') END, true);
    PERFORM pg_advisory_xact_lock(hashtextextended('assignment:authority',1179));
    RETURN NULL;
END;
$$;

CREATE OR REPLACE FUNCTION we_assignment_authority_publish()
RETURNS trigger LANGUAGE plpgsql SET search_path FROM CURRENT AS $$
BEGIN
    IF current_setting('assignment.authority_touched',true) IS DISTINCT FROM txid_current()::text THEN
        UPDATE ac_assignment_authority_fence SET revision=revision+1 WHERE scope='platform';
        IF NOT FOUND THEN RAISE EXCEPTION 'Assignment authority fence is unavailable'; END IF;
        PERFORM set_config('assignment.authority_touched',txid_current()::text,true);
    END IF;
    RETURN NULL;
END;
$$;

DO $$
DECLARE
    qualification_table text;
BEGIN
    FOREACH qualification_table IN ARRAY ARRAY[
        'sys_users','sys_roles','sys_business_units','sys_user_roles','sys_role_assignments',
        'sys_business_unit_roles','sys_user_business_units','sys_user_business_unit_roles',
        'sys_virtual_groups','sys_virtual_group_members','sys_virtual_group_roles',
        'sys_permissions','sys_role_permissions','admin_permission_delegations','up_delegation_rule'
    ] LOOP
        IF to_regclass(qualification_table) IS NULL THEN
            RAISE EXCEPTION 'Required qualification source % is unavailable',qualification_table;
        END IF;
        EXECUTE format('DROP TRIGGER IF EXISTS assignment_authority_lock ON %I',qualification_table);
        EXECUTE format('CREATE TRIGGER assignment_authority_lock BEFORE INSERT OR UPDATE OR DELETE OR TRUNCATE ON %I '
                       'FOR EACH STATEMENT EXECUTE FUNCTION we_assignment_authority_lock()',qualification_table);
        EXECUTE format('DROP TRIGGER IF EXISTS assignment_authority_publish ON %I',qualification_table);
        IF qualification_table <> 'sys_users' THEN
            EXECUTE format('CREATE TRIGGER assignment_authority_publish AFTER INSERT OR UPDATE OR DELETE OR TRUNCATE ON %I '
                           'FOR EACH STATEMENT EXECUTE FUNCTION we_assignment_authority_publish()',qualification_table);
        ELSE
            -- Login/audit/display-only user updates must not invalidate every in-flight task decision.
            EXECUTE format('CREATE TRIGGER assignment_authority_publish AFTER INSERT OR DELETE OR TRUNCATE ON %I '
                           'FOR EACH STATEMENT EXECUTE FUNCTION we_assignment_authority_publish()',qualification_table);
        END IF;
    END LOOP;
END;
$$;

DROP TRIGGER IF EXISTS assignment_authority_user_change ON sys_users;
CREATE TRIGGER assignment_authority_user_change AFTER UPDATE ON sys_users
FOR EACH ROW WHEN (
    OLD.id IS DISTINCT FROM NEW.id OR OLD.username IS DISTINCT FROM NEW.username
    OR OLD.status IS DISTINCT FROM NEW.status OR OLD.deleted IS DISTINCT FROM NEW.deleted
    OR OLD.entity_manager_id IS DISTINCT FROM NEW.entity_manager_id
    OR OLD.function_manager_id IS DISTINCT FROM NEW.function_manager_id
    OR OLD.locked_until IS DISTINCT FROM NEW.locked_until
) EXECUTE FUNCTION we_assignment_authority_publish();
