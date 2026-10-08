# 22-dual-binding-mi

Local P3 vehicle: **MI Complete** with two SUB bindings on the same file table.
**Not** loaded by `00-init-all`. Does **not** add a second FK to Demo `attachment`.

| Table | Type | Role |
|---|---|---|
| `p3_mi_case` | MAIN | Case |
| `p3_mi_party` | SUB | MI collection (`case_id` → MAIN, `assignee_user_id`, `miParticipantRow`) |
| `p3_mi_file` | SUB | Files (`case_id` → MAIN **and** `party_id` → party) |

BPMN: Fill (INITIATOR, auto-complete on start) → Release (INITIATOR, Approve injects MI collection) → MI subprocess (`ELEMENT_VARIABLE`) with TASK form that binds `p3_mi_file` twice.

Start form party binding is `miParticipantRow` (same as the collection table). Release Approve is a setup step (injects MI collection). The MI TASK form also uses `miParticipantRow` plus two `p3_mi_file` SUB bindings.

## Apply (dev Postgres)

```bash
docker cp deploy/init-scripts/22-dual-binding-mi/00-init.sql platform-postgres-dev:/tmp/22-p3-mi-dual.sql
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -v ON_ERROR_STOP=1 -f /tmp/22-p3-mi-dual.sql
```

Catalog publish is not in this SQL: Developer Workstation Deploy (auto-enable), or
the verify script below.

## Complete e2e

```bash
cd frontend
node scripts/verify-p3-mi-dual-binding-complete.mjs
```

`SKIP_SQL=1` / `SKIP_DEPLOY=1` skip reseed or DW Deploy. Alice Complete must send
`subTableBindingScopes` (case-file intersection + party-file alice-doc only) and
Bob's remaining task must still contain `bob-doc`.

Release is a setup node: catalog Task Form lookup for that PROCESS form is currently
empty (`formName=null`), so the script Approve-completes it via API with the start
payload to inject the MI collection. Isolation is asserted on the MI subtask UI Complete.
