-- Platform default time zone: Hong Kong time, UTC+8 (user decisions 2026-11-06 and 2026-11-07, WP19).
-- The services already run in it (JVM default zone, Hibernate jdbc.time_zone, Jackson: Asia/Hong_Kong), and their
-- JDBC sessions carry that zone. Sessions that do not set one - psql, GUI clients, SQL run by hand, the init scripts
-- themselves - used the server default, UTC, so a DEFAULT CURRENT_TIMESTAMP written there stored UTC wall-clock time
-- in a timestamp without time zone column, 8 hours off what the services write (out-of-scope list row 89).
--
-- The default is set for the platform's own database account (the one running this script) in this database only:
-- other systems that share the database (Activepieces, other schemas of a shared database) keep theirs, the same
-- account in other databases of the instance keeps its own, and no database ownership or superuser is needed -
-- every account may set its own session defaults. Any other account that writes the platform's tables directly
-- (a DBA, a reporting job) starts its session with SET TIME ZONE 'Asia/Hong_Kong'.
-- Takes effect for NEW sessions of this account in this database (the SET below covers the rest of this one). Rows
-- written before keep their values. 00-init-all.sh runs it first, before the Flowable tables; the k8s schema bundle
-- starts with it. Existing environments: run once as the platform account (psql -v ON_ERROR_STOP=1 -f). Idempotent.

DO $$
BEGIN
    EXECUTE format('ALTER ROLE CURRENT_USER IN DATABASE %I SET timezone = %L', current_database(), 'Asia/Hong_Kong');
END
$$;

SET timezone = 'Asia/Hong_Kong';
