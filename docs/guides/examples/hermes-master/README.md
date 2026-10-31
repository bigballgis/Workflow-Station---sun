# Hermes Master via Automation → Content Organizer

Hermes Master (the DW-wide assistant) no longer calls the AI gateway directly by default: DW posts each turn to an
Automation flow, whose Content Organizer **Chat** step answers with the CO prompt setting "Hermes Master".

```
DW  POST /ai-generation/hermes-master/chat
 └─ HermesMasterAutomationClient ── POST <AP>/api/v1/webhooks/<flowId>/sync  {message, userInput, staffId}
     └─ flow hermes-master-chat: Catch Webhook → Content Organizer · Chat 1.0.3 → Return Response {reply, error}
         └─ Content Organizer completion (prompt setting "Hermes Master", variable {user_input} = userInput)
```

| File | What | Import in the target environment |
|---|---|---|
| `flow-hermes-master-chat.json` | Automation flow `hermes-master-chat` | Admin Center → **Automation Flows** → Import (publish), Public workspace |

## Prerequisites

- content-organizer piece **1.0.3** (adds the `chat` action) and the Content Organizer configuration of the
  [OCR guide](../../content-organizer-ocr-guide.md) §8, plus `CONTENT_ORGANIZER_CHAT_PROMPT_SETTING_ID` /
  `CONTENT_ORGANIZER_CHAT_PROMPT_VARIABLE_ID` (§4.3, §6.5).
- Every DW user who chats needs an employee ID (`sys_users.employee_id`); Content Organizer audits each call by it.
  Without one the bubble says so (`HERMES_MASTER_NO_EMPLOYEE_ID`) instead of borrowing another identity.

## DW settings (`ai-generation.hermes-master.*`)

| Env | Default | Meaning |
|---|---|---|
| `HERMES_MASTER_PROVIDER` | `AUTOMATION` | `GAIP` switches back to the direct AI gateway path (`GROUP_AI_GATEWAY_URL` + per-user AMToken) |
| `HERMES_MASTER_FLOW_KEY` | `hermes-master-chat` | Business key of the flow; resolved to the environment's flow id on every call |
| `ACTIVEPIECES_WEBHOOK_BASE_URL` | `http://localhost:8086` (docker profile: `http://activepieces:80`) | Same variable as the engine's service tasks |
| `HERMES_MASTER_TIMEOUT_SECONDS` | `110` | Must stay below the browser's 120s request timeout |

## Contract

- Request body: `{"message": "<latest user message>", "userInput": "<page + design digest + transcript>", "staffId": "<employee id>"}`
- Response body: `{"reply": "<Markdown>", "error": ""}`; when the Chat step fails (it runs with *continue on failure*),
  `reply` is empty and `error` carries the piece error, which DW shows as `Content Organizer failed: <message>`.
- A run that never reaches Return Response answers `204`; DW reports that as no reply, never as success.

Verified in dev on 2026-10-30 against `mock-content-organizer` (webhook answered with a Markdown reply in ~4s; an empty
staff ID came back as the piece's error message).
