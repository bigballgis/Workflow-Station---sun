# 21-dual-binding-p0

Local P0 vehicle for same-table multi-binding. **Not** loaded by `00-init-all`.

## Why a dedicated Function Unit

`uk_dw_table_name` is global. `MiSubTaskSubTableRowMerger.foreignKeyTargetsMainTable`
keys off **table name** across all bindings. Adding a second FK to Demo `attachment`
would make every MI save of `dw:attachment` mixed MAIN+SUB → `null` → participant
guard on, which would break Sub task attachment isolation.

This seed uses unique names:

| Table | Type | Role |
|---|---|---|
| `p0_dual_case` | MAIN | Case |
| `p0_dual_party` | SUB | Parties (`case_id` → MAIN) |
| `p0_dual_file` | SUB | Files (`case_id` → MAIN **and** `party_id` → party) |

PROCESS form `P0 Dual Case Form` binds `p0_dual_file` twice with different
`filter_fk_field_id`. Designer switch `existsByFormIdAndTableId` / `BINDING_EXISTS`
stays closed; this insert bypasses it on purpose.

## Apply (dev Postgres)

```bash
docker cp deploy/init-scripts/21-dual-binding-p0/00-init.sql platform-postgres-dev:/tmp/21-p0-dual.sql
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -v ON_ERROR_STOP=1 -f /tmp/21-p0-dual.sql
```

Function unit code: `p0-dual-binding-test`. Assigned to Public group `vg-dev-public`.
Does not seed `sys_function_units` (Portal start needs a later catalog publish).
