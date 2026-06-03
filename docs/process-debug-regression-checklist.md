# Process Debug Regression Checklist (Re-runnable)

## 1. Purpose

This checklist is for re-running end-to-end regression of Process Debug MVP capabilities after backend/frontend changes.

Scope covered:

- `simulate` gateway explain (`gatewayEval`)
- `lookup probe` debug API
- `action run` debug API (dry run)
- runtime compatibility in local Docker dev environment

---

## 2. Preconditions (Must Pass)

- [ ] Local dev stack is running from `deploy/environments/dev`
- [ ] `developer-workstation` container is `healthy`
- [ ] At least one function unit with process exists (example: `id=1`)
- [ ] At least one function unit with `RELATED` binding backed by relation table data exists (example: `id=5`)

Quick checks:

```bash
cd deploy/environments/dev
docker compose -f docker-compose.dev.yml --env-file .env ps developer-workstation
```

```bash
curl -sS "http://localhost:8083/api/v1/function-units?page=0&size=20" | jq '.success'
```

---

## 3. Authentication Setup

Login and persist cookie for subsequent API calls:

```bash
curl -sS -c /tmp/dw.cookies \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  "http://localhost:8083/api/v1/auth/login" | jq '.user.username'
```

Expected:

- returns `"admin"`
- `/tmp/dw.cookies` is created

---

## 4. Core Regression Cases

## Case A - Simulate returns gateway explain

```bash
curl -sS -b /tmp/dw.cookies \
  -H "Content-Type: application/json" \
  -d '{}' \
  "http://localhost:8083/api/v1/function-units/1/process/simulate" | jq '.data.steps[] | select(.gatewayEval != null)'
```

Expected:

- [ ] at least one step contains `gatewayEval`
- [ ] `gatewayEval.evaluations[*].condition/result/reason` present
- [ ] `gatewayEval.selectedFlowId` present
- [ ] if fallback happened, `data.warnings` includes `BIZ_DEBUG_GATEWAY_EXPRESSION_UNSUPPORTED`

---

## Case B - Action run success path

```bash
curl -sS -b /tmp/dw.cookies \
  -H "Content-Type: application/json" \
  -d '{"nodeId":"Task_SubmitShowcase","actionId":"1","runtimeVariables":{"requestId":"R-001"},"formData":{"amount":1200},"dryRun":true}' \
  "http://localhost:8083/api/v1/function-units/1/process/debug/actions/run" | jq '.success, .data.success, .data.actionResult.code'
```

Expected:

- [ ] top-level `success=true`
- [ ] `data.success=true`
- [ ] `data.actionResult.code="OK"`
- [ ] response includes `logs` and `durationMs`

---

## Case C - Action run mismatch rejection

```bash
curl -sS -b /tmp/dw.cookies \
  -H "Content-Type: application/json" \
  -d '{"nodeId":"Task_SubmitShowcase","actionId":"4","runtimeVariables":{},"formData":{},"dryRun":true}' \
  "http://localhost:8083/api/v1/function-units/1/process/debug/actions/run" | jq '.success, .error.code, .error.message'
```

Expected:

- [ ] `success=false`
- [ ] `error.code="BIZ_DEBUG_ACTION_NOT_FOUND"`
- [ ] message indicates `actionId does not belong to node`

---

## Case D - Lookup probe success on RELATED binding

Use a binding with real relation table target (example in seeded environment: function unit `5`, form `16`, binding `35`).

```bash
curl -sS -b /tmp/dw.cookies \
  -H "Content-Type: application/json" \
  -d '{"formId":16,"bindingId":35,"lookupConfig":{"searchFields":["name"],"displayFields":["id","name"],"filterConditions":[]},"keyword":"","runtimeVariables":{},"page":0,"size":20,"searchMode":"contains"}' \
  "http://localhost:8083/api/v1/function-units/5/process/debug/lookup/probe" | jq '.success, (.data.columns|length), .data.total'
```

Expected:

- [ ] `success=true`
- [ ] `data.columns.length > 0`
- [ ] `data.rows` shape is valid object list
- [ ] `page/size/total` returned

---

## Case E - Lookup probe invalid binding rejection

```bash
curl -sS -b /tmp/dw.cookies \
  -H "Content-Type: application/json" \
  -d '{"formId":1,"bindingId":999999,"lookupConfig":{"searchFields":["name"],"displayFields":["id","name"],"filterConditions":[]},"keyword":"","runtimeVariables":{},"page":0,"size":20,"searchMode":"contains"}' \
  "http://localhost:8083/api/v1/function-units/1/process/debug/lookup/probe" | jq '.success, .error.code'
```

Expected:

- [ ] `success=false`
- [ ] `error.code="BIZ_DEBUG_LOOKUP_CONFIG_INVALID"`

---

## 5. Frontend Smoke (Optional but Recommended)

- [ ] Open Process Designer debug panel
- [ ] Start debug and reach a gateway node
- [ ] `Decision` tab shows explain rows (`condition/result/reason`)
- [ ] `Actions` tab can run bound action and update variable patches
- [ ] `Node Form` lookup probe button opens result modal and supports row apply
- [ ] `Execution Log` can filter by event type (`GATEWAY_EVAL/LOOKUP_PROBE/ACTION_RUN/VARIABLE_PATCH`)

---

## 6. Failure Triage Guide

## Auth fails (`UNAUTHORIZED`)

- verify login endpoint is `/api/v1/auth/login`
- re-generate cookie file `/tmp/dw.cookies`
- verify admin seed account exists (`admin/admin123`)

## `lookup probe` always empty

- verify binding is `RELATED`
- verify relation table has field definitions and row data
- verify `searchFields/displayFields` use real field names

## action mismatch not rejected

- inspect BPMN extension for node action ids (`custom:property` or `custom_1:values`)
- verify node id in request exactly matches BPMN task id

## service unavailable / reset by peer

- run `docker compose ... ps developer-workstation`
- check logs: `docker compose ... logs --tail=200 developer-workstation`

---

## 7. Regression Record Template

Use this template in PR or release notes:

```md
## Process Debug Regression

- Date:
- Commit/Branch:
- Environment:

### Case Results
- [ ] A Simulate gateway explain
- [ ] B Action run success
- [ ] C Action mismatch rejection
- [ ] D Lookup probe success
- [ ] E Lookup invalid binding rejection

### Notes
- Observed warnings:
- Known gaps:
- Follow-up tasks:
```

