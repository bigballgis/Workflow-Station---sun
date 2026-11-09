-- Data snapshot of one database as sorted "table|row" lines: every row of every public table except Flowable's
-- (act_*, flw_*). Run-dependent values are masked: *_at / *_time / *_date / timestamp / valid_from / valid_to columns
-- are left out, and an "id" that is a UUID reads "<uuid>" (some seeds generate it). UUIDs in other columns stay, so
-- a row that references a fixed-UUID seed row still says which one. Used by verify-init-parity.sh to compare the
-- seed data of the Docker init with the k8s init; run with psql -q -f.
\pset format unaligned
\pset tuples_only on
\pset footer off
CREATE FUNCTION pg_temp.rows_of(t text) RETURNS SETOF text LANGUAGE plpgsql AS $f$
BEGIN
  RETURN QUERY EXECUTE format(
    'SELECT %L || ''|'' || coalesce((SELECT jsonb_object_agg(k, CASE WHEN k = ''id'' AND v #>> ''{}'' ~ ''^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'' THEN to_jsonb(''<uuid>''::text) ELSE v END)'
    || ' FROM jsonb_each(to_jsonb(x)) AS e(k, v) WHERE k !~ ''(_at|_time|_date)$'' AND k NOT IN (''timestamp'', ''valid_from'', ''valid_to''))::text, ''{}'') FROM %I x',
    t, t);
END
$f$;
SELECT r FROM information_schema.tables tb, LATERAL pg_temp.rows_of(tb.table_name) r
WHERE tb.table_schema = 'public' AND tb.table_type = 'BASE TABLE'
  AND tb.table_name NOT LIKE 'act\_%' AND tb.table_name NOT LIKE 'flw\_%'
ORDER BY r;
