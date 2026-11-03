# AI Studio via Automation → Content Organizer

AI Studio's Copilot (advisory chat and *Propose change*) no longer calls the AI gateway directly by default: DW posts
each turn to an Automation flow, whose Content Organizer **Agent Chat** step sends it to the CO agent setting
"Function Unit". Its orchestrator hands the request to the one specialist that owns the phase, and that specialist's
answer comes back as the reply.

```
DW  AI Studio Copilot (advisory turn, or proposal job)
 └─ AiStudioAutomationClient ── POST <AP>/api/v1/webhooks/<flowId>/sync  {message, staffId}
     └─ flow ai-studio-co-agent: Catch Webhook → Content Organizer · Agent Chat 1.0.4 → Return Response {reply, error}
         └─ Content Organizer completion (agent setting "Function Unit": orchestrator → one specialist)
```

| File | What | Import in the target environment |
|---|---|---|
| `flow-ai-studio-co-agent.json` | Automation flow `ai-studio-co-agent` | Admin Center → **Automation Flows** → Import (publish), Public workspace |

## Prerequisites

- content-organizer piece **1.0.4** (adds the `agent_chat` action) and the Content Organizer configuration of the
  [OCR guide](../../content-organizer-ocr-guide.md) §8, plus `CONTENT_ORGANIZER_AGENT_SETTING_ID` (§4.1c): the id of the
  **ACTIVE** row of the agent setting in CO → Use case management → Agent Settings Workbench. Every version is its own
  row with its own id, so activating another version means changing this value.
- The CO agents carry the instructions of
  [developer-workstation-ai-studio-co-agent-instructions.md](../../../design/developer-workstation-ai-studio-co-agent-instructions.md).
- Every DW user of AI Studio needs an employee ID (`sys_users.employee_id`); Content Organizer audits each call by it.
  Without one the Copilot says so (`AI_STUDIO_NO_EMPLOYEE_ID`) instead of borrowing another identity.

## DW settings (`ai-generation.ai-studio.*`)

| Env | Default | Meaning |
|---|---|---|
| `AI_STUDIO_PROVIDER` | `AUTOMATION` | `GAIP` switches back to the direct AI gateway path (`GROUP_AI_GATEWAY_URL` + per-user AMToken) |
| `AI_STUDIO_FLOW_KEY` | `ai-studio-co-agent` | Business key of the flow; resolved to the environment's flow id on every call |
| `ACTIVEPIECES_WEBHOOK_BASE_URL` | `http://localhost:8086` (docker profile: `http://activepieces:80`) | Same variable as the engine's service tasks |
| `AI_STUDIO_TIMEOUT_SECONDS` | `310` | Slightly above Automation's own sync-webhook wait (`AP_WEBHOOK_TIMEOUT_SECONDS=300`) |

**One-click generation (Build with AI) still uses the AI gateway.** Content Organizer answers every request with a
single specialist, so a whole design has to be orchestrated by DW phase by phase — not built yet.

## Contract

- Request body: `{"message": "<sectioned request>", "staffId": "<employee id>"}`. The message sections (`MODE`, `PHASE`,
  `SCOPE`, `CURRENT DESIGN`, `SCHEMA METADATA`, `FORMAT SPECIFICATION`, `DOCUMENTS`, `CONVERSATION`, `USER REQUEST`) are
  built by `AiStudioAgentMessage`; see the
  [output contract](../../../design/developer-workstation-ai-studio-co-output-contract.md).
- Response body: `{"reply": "<explanation, then the GENERATED_DATA block on a proposal>", "error": ""}`. DW parses and
  validates the reply exactly like a gateway answer; a repairable validation failure is asked once more with the
  violation fed back. When the Agent Chat step fails (it runs with *continue on failure*), `reply` is empty and
  `error` carries the piece error, which DW shows as `Content Organizer failed: <message>`.
- A run that never reaches Return Response answers `204`; DW reports that as no reply, never as success.
