# DW Copilot (VS Code)

Lets IT developers design Developer Workstation Function Units from GitHub Copilot.
Design: `docs/plans/dw-vscode-copilot-extension.md` (business) and `docs/plans/dw-vscode-copilot-extension-p1-tech.md` (P1 technical) in the repository.

## Install

VS Code → Extensions → `…` → **Install from VSIX…** → `dw-copilot-<version>.vsix`.

## Configure and sign in

1. Settings → `dwCopilot.environments`, one entry per DW site origin (the host that serves `/login/`, `/dev/` and `/api/v1`):

   ```json
   "dwCopilot.environments": [
     { "name": "Local", "url": "http://localhost:3000" }
   ]
   ```

2. **DW: Select environment** (or click the `DW:` status bar item).
3. **DW: Sign in** — the browser opens the DW login page (SSO); after login it hands control back to VS Code.
   Requires admin-center to whitelist `vscode://workflow-station.dw-copilot/` (in `application.yml` since P1).

## Use

Copilot Chat → **Agent** mode, then describe what you want, e.g.

> In function unit "Travel Request", add a MAIN table travel_request (traveler VARCHAR(100), destination VARCHAR(200), start_date DATE) and verify it.

Or type `@dw …` in Ask mode.

| Tool | Writes? | Notes |
|---|---|---|
| `#dwListWorkspaces`, `#dwListFunctionUnits` | no | find ids |
| `#dwCreateFunctionUnit` | yes | asks to confirm |
| `#dwFormatSpec`, `#dwGetDesign` | no | format + current design, read before writing |
| `#dwDryRun` | no | same checks as apply |
| `#dwApplyDesign` | yes | dry run → stops on ERROR → backup zip → confirm → write |
| `#dwUndo` | yes | only scopes that return an undo token |
| `#dwExportFunctionUnit` | no | zip into `<folder>/dw-exports/` for Admin Center import |

**Restore**: every apply first saves the previous design as a zip (**DW: Open backups folder**).
Import it in DW with the same name: DW keeps the current design as a version and replaces it with the backup.

## Develop

```bash
npm install
npm test          # compile + node:test unit tests
npm run package   # dw-copilot-<version>.vsix
```
