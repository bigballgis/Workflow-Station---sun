# 24-fu-call-demo — one Function Unit calling another

Two Function Units that together exercise every part of the cross-Function-Unit
call feature.

| Code | Name | Startup Mode | Role |
|---|---|---|---|
| `fu-call-demo-vendor` | Vendor Qualification Check | **CALLABLE** | the one being called |
| `fu-call-demo-purchase` | Purchase Request (Function Unit Calls) | STANDALONE | the one doing the calling, started by a user |

## What it demonstrates

The caller's process puts all three mechanisms side by side:

```
Start
  → Raise Request              userTask
  → Check Primary Vendor       callActivity                     ← call once
  → Check Additional Vendors   callActivity + multiInstance     ← call once per row
  → Confirm Budget Lines       subProcess   + multiInstance     ← MI sub-task (NOT a call)
  → Approve Purchase           userTask
  → Approved / Rejected
```

1. **Call once** — `Check Primary Vendor` starts exactly one Vendor Qualification
   Check instance for the vendor named on the request. Data passing (call step →
   properties → *Data passing*): `primary_vendor → vendor_name` and
   `department → category` go in; when the check finishes, its `check_result`
   comes back into the request's `primary_vendor_result`, which the approver sees.

2. **Call once per row** — `Check Additional Vendors` carries the same loop
   characteristics, but on a `callActivity`. *Rows from* `extra_vendors`: every row
   starts its own separate child process, receiving that row's `vendor_name` and
   `category`. When each check finishes, its `check_result` is copied back into
   **that same row** (`check_status`) — a per-row call copies results into rows,
   not into the request. Every check is also visible under *Sub-processes*.

   The engine compiles these settings on deploy into Flowable's `flowable:in` /
   `flowable:out` and a row-collection expression; the designer never edits them.

3. **Co-existence with MI sub-tasks** — `Confirm Budget Lines` is the
   *pre-existing* multi-instance sub-task mechanism: it expands **inside** this
   process instance, one user task per `budget_lines` row, and creates no child
   process at all. It is in this demo on purpose — the two mechanisms look
   similar on a diagram, share no code path, and this diagram is the live proof
   they do not interfere.

4. **Strong binding** — rejecting a vendor check rejects the purchase request
   that called it; withdrawing the purchase request terminates any vendor checks
   still running under it.

5. **Startup Mode** — the callee is `CALLABLE`, so it never appears in the Portal
   catalog as something a user starts. Only units set to `CALLABLE` or `BOTH` are
   offered in a call activity's Function Unit picker, and a unit is never offered
   itself.

## Loading it

`00-init.sql` holds both units, exported from the dev DB (2026-10-09). It is a
snapshot: it deletes the units' design rows and writes them back, so re-running
it is safe. When the design changes, regenerate the file from the DB instead of
editing rows by hand.

```bash
# Fresh database: the runners pick it up automatically.
# Existing database: apply by hand.
docker cp deploy/init-scripts/24-fu-call-demo/00-init.sql platform-postgres-dev:/tmp/24-fu-call-demo.sql
MSYS_NO_PATHCONV=1 docker exec platform-postgres-dev \
  psql -U platform_dev -d workflow_platform_dev -v ON_ERROR_STOP=1 -f /tmp/24-fu-call-demo.sql
docker compose -f deploy/environments/dev/docker-compose.dev.yml restart developer-workstation
```

## Deploying it — order matters

> **Deploy `Vendor Qualification Check` first, then `Purchase Request`.**

A call activity resolves its target against **deployed** process definitions, so
deploying the caller first is refused with:

```
Call activity targets Function Unit 'fu-call-demo-vendor', which is not deployed
in this environment. Deploy that Function Unit first, then deploy
'fu-call-demo-purchase'.
```

That is the feature working, not a seeding problem. Deploy the callee and retry.

## Trying it in the Portal

1. Start a **Purchase Request**. Fill in the title, department, amount and the
   primary vendor; add a row or two to **Additional Vendors**, and a budget line
   with a real user id in **Budget Owner**.
2. Submit. The request stops at `Check Primary Vendor` while the vendor check
   runs as its own request.
3. Open the vendor check from the reviewer's To Do list and **Approve** it — the
   purchase request moves on to the additional vendors, one child per row.
4. On the purchase request's detail page, the **Sub-processes** tab shows each
   called check: which vendor, what step it is on, and the verdict once it
   returns. It is read-only, and visible to anyone who can open the request —
   no separate access to the vendor check unit is required.
5. To see the strong binding, **Reject** a vendor check instead: the calling
   purchase request ends as `REJECTED` too.

## Notes

- `calledElement` holds the callee's **code**, never its id — ids are remapped on
  import, codes are not, and the deployed process key equals the code.
- `childFormName` likewise stores the form's **name**, for the same reason.
- No physical tables: row data lives in JSON on the process instance, per the
  `json-row-storage-no-physical-tables` rule.
- `budget_lines` is bound with `binding_link_mode = 'miParticipantRow'`, the
  explicit declaration that one row is one MI participant. `extra_vendors` is a
  plain `structuralFk` sub-table — it feeds call activities, which have nothing
  to do with MI participants.
