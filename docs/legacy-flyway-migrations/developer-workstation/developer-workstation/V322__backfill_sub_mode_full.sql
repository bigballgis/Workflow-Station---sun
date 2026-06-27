-- Backfill sub_mode = 'FULL' for existing SUB bindings that have no sub_mode set.
-- NULL means the binding was created before sub_mode was introduced; FULL is the intended default.
UPDATE dw_form_table_bindings
SET sub_mode = 'FULL'
WHERE binding_type = 'SUB'
  AND sub_mode IS NULL;
