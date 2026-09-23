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
`filter_fk_field_id`. Designer now allows that when the filter FK differs;
identical table+filter still returns `BINDING_EXISTS`. This seed still inserts
both rows so local env has the vehicle without clicking the designer.

## Apply (dev Postgres)

```bash
docker cp deploy/init-scripts/21-dual-binding-p0/00-init.sql platform-postgres-dev:/tmp/21-p0-dual.sql
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -v ON_ERROR_STOP=1 -f /tmp/21-p0-dual.sql
docker cp deploy/init-scripts/21-dual-binding-p0/01-patch-initiator.sql platform-postgres-dev:/tmp/21-p0-dual-01.sql
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -v ON_ERROR_STOP=1 -f /tmp/21-p0-dual-01.sql
```

`00-init.sql` creates the Function Unit (rebuild-by-code). Do not edit that file;
`01-patch-initiator.sql` is the append-only upgrade: it looks up `p0-dual-binding-test`
by **code** and converges BPMN to two `INITIATOR` user tasks (`Task_FillDual` auto-completes
on start; `Task_ReviewDual` remains a To Do so Portal Save can send `subTableBindingScopes`).
Re-running `01` is safe.

Catalog publish is not in this SQL: use Developer Workstation Deploy (auto-enable).
