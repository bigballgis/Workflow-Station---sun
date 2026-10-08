# 23-dual-binding-multi-form

Local vehicle for same-table multi-binding across **two forms**. Not loaded by `00-init-all`.

| Form | Type | Task | File bindings |
|---|---|---|---|
| `P0 MF Fill Form` | PROCESS | `Task_FillMf` (auto-completes for the initiator) | `p0_mf_file` by `case_id` and by `party_id` |
| `P0 MF Review Form` | TASK | `Task_ReviewMf` | the same two filters, separate binding ids |

Tables are `p0_mf_case` / `p0_mf_party` / `p0_mf_file` so this does not share names with `p0-dual-binding-test`.

```bash
docker cp deploy/init-scripts/23-dual-binding-multi-form/00-init.sql platform-postgres-dev:/tmp/23-p0-mf.sql
docker exec -i platform-postgres-dev psql -U platform_dev -d workflow_platform_dev -v ON_ERROR_STOP=1 -f /tmp/23-p0-mf.sql
```

Catalog publish is not in this SQL. `frontend/scripts/verify-p0-dual-binding-multi-form.mjs` deploys it and runs the portal start → review save.
