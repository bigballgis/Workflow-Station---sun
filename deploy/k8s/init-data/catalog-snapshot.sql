-- Schema snapshot of one database as sorted "category|object|definition" lines (schema public): columns,
-- constraints, indexes, functions, triggers, views, sequences, extensions, comments, Flowable schema versions, and
-- the role / database settings of pg_db_role_setting that apply to this database (00-schema/101 sets one). Run it as
-- the account that ran the init: that account's settings read "<init account>", so two databases initialized by
-- differently named accounts still compare. Used by verify-init-parity.sh; run with psql -q -f.
\pset format unaligned
\pset tuples_only on
\pset footer off
SELECT line FROM (
  SELECT 'column|' || c.table_name || '.' || c.column_name || '|' || c.ordinal_position || ' ' || c.data_type || ' ' || c.udt_name
         || coalesce('(' || c.character_maximum_length || ')', '') || coalesce(' p' || c.numeric_precision || ',' || c.numeric_scale, '')
         || ' null=' || c.is_nullable || ' default=' || coalesce(c.column_default, '-') AS line
  FROM information_schema.columns c JOIN information_schema.tables t
    ON t.table_schema = c.table_schema AND t.table_name = c.table_name AND t.table_type = 'BASE TABLE'
  WHERE c.table_schema = 'public'
  UNION ALL
  SELECT 'constraint|' || conrelid::regclass || '.' || conname || '|' || pg_get_constraintdef(oid)
  FROM pg_constraint WHERE connamespace = 'public'::regnamespace AND conrelid <> 0
  UNION ALL
  SELECT 'index|' || tablename || '.' || indexname || '|' || indexdef FROM pg_indexes WHERE schemaname = 'public'
  UNION ALL
  SELECT 'function|' || p.proname || '(' || pg_get_function_identity_arguments(p.oid) || ')|' || md5(pg_get_functiondef(p.oid))
  FROM pg_proc p WHERE p.pronamespace = 'public'::regnamespace AND p.prokind IN ('f', 'p')
  UNION ALL
  SELECT 'trigger|' || tgrelid::regclass || '.' || tgname || '|' || pg_get_triggerdef(oid)
  FROM pg_trigger WHERE NOT tgisinternal AND tgrelid IN (SELECT oid FROM pg_class WHERE relnamespace = 'public'::regnamespace)
  UNION ALL
  SELECT 'view|' || viewname || '|' || md5(definition) FROM pg_views WHERE schemaname = 'public'
  UNION ALL
  SELECT 'sequence|' || sequencename || '|' || data_type || ' start=' || start_value || ' inc=' || increment_by
  FROM pg_sequences WHERE schemaname = 'public'
  UNION ALL
  SELECT 'extension|' || extname || '|' || extversion FROM pg_extension
  UNION ALL
  SELECT 'comment|' || c.relname || coalesce('.' || a.attname, '') || '|' || md5(d.description)
  FROM pg_description d JOIN pg_class c ON c.oid = d.objoid AND d.classoid = 'pg_class'::regclass
  LEFT JOIN pg_attribute a ON a.attrelid = c.oid AND a.attnum = d.objsubid AND d.objsubid > 0
  WHERE c.relnamespace = 'public'::regnamespace
  UNION ALL
  SELECT 'flowable|' || name_ || '|' || value_ FROM act_ge_property WHERE name_ LIKE '%schema.version'
  UNION ALL
  SELECT 'dbsetting|' || CASE WHEN s.setrole = 0 THEN 'all roles' WHEN r.rolname = current_user THEN '<init account>'
                              ELSE r.rolname END
         || ' in ' || CASE WHEN s.setdatabase = 0 THEN 'all databases' ELSE 'this database' END
         || '|' || array_to_string(s.setconfig, ',')
  FROM pg_db_role_setting s LEFT JOIN pg_roles r ON r.oid = s.setrole
  WHERE s.setdatabase IN (0, (SELECT oid FROM pg_database WHERE datname = current_database()))
) s ORDER BY line;
