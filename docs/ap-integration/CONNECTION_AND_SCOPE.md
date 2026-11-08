# Automation connection and data scope

This page lists the APIs that separate **who an automation runs as** from **which Function Unit data it may touch**. It does not change [DECISIONS.md](DECISIONS.md) C-3: Activepieces still does not hold `SERVICE_INTERNAL_TOKEN`.

## Callers

| Caller | Credential | What it proves |
|---|---|---|
| Developer Workstation browser | Login cookie | The signed-in developer |
| user-portal → admin-center | `X-Service-Token` (`SERVICE_INTERNAL_TOKEN`) | The caller is the portal |
| Function Unit Data piece → user-portal | `X-Internal-Token` (`PORTAL_INTERNAL_API_TOKEN`) | The caller is the automation engine |

Admin context path: `/api/v1/admin`. Portal context path: `/api/portal`.

## Admin, signed-in user

| Method | Path | Result when refused |
|---|---|---|
| GET | `/automation-service-accounts` | Header `X-Dev-Group-Id`. 403 unless the caller can publish in that workspace. Public: `SYS_ADMIN` only |
| POST | `/automation-service-accounts` | Header `X-Dev-Group-Id`. Same people who can publish in that workspace. Public: `SYS_ADMIN` only. 404 unknown user. Body: `userId`, `displayName`. Optional `platformDefault=true` is `SYS_ADMIN` only. Omitting it does not clear an existing platform default |
| GET | `/automation-runtime-connections/{workspaceId}` | 401, or 403 if the workspace is not yours. No binding returns the platform default account |
| PUT | `/automation-runtime-connections/{workspaceId}` | Same people who can publish in that workspace. Public: `SYS_ADMIN` only. A team workspace: a member with `DEVELOPER`, `TECH_LEAD`, or `TEAM_LEAD`, or `SYS_ADMIN` |
| DELETE | `/automation-runtime-connections/{workspaceId}` | Same as PUT. Afterwards the workspace uses the platform default |
| GET | `/automation/flows/{flowId}/scope` | 404 when the flow has no published scope |
| PUT | `/automation/flows/{flowId}/scope` | Header `X-Dev-Group-Id` is the workspace. Body: `functionUnitId`, `capabilities` (`read`, `query`, `write`, `subtable`, `start`), optional `serviceAccountUserId` (empty uses the workspace default; an unknown account is rejected and the scope is not saved). Optional `catalogIds` must be that Function Unit's catalog |

`__ALL__` and a blank workspace are rejected. Responses contain `userId` and `displayName` only.

## Admin, portal only

| Method | Path | Result |
|---|---|---|
| GET | `/internal/automation-runtime?externalProjectId=&flowId=` | `flowId` is optional. A flow with its own account uses that account when the scope belongs to the workspace. Otherwise the workspace binding, then the platform default. 403 when the scope belongs to another workspace |
| GET | `/internal/automation-scopes?externalProjectId=&flowId=` | 404 when that flow has no scope. The portal then refuses the data call |

`hermes-main` is Public. `hermes-dg-<developer group id>` is a team workspace.

## Portal, automation engine only

Every call needs `externalProjectId` and `flowId`. A body field `actor`, `triggeredBy`, `userId`, or `startUserId` is rejected. Writes also need `Idempotency-Key`. Field and sub-table writes also need `lockVersion`. The same key with the same body replays the first result. The same key with a different body is 409. `start` opens a process for the published Function Unit as the flow's runtime identity and does not accept sub-table rows.

| Method | Path |
|---|---|
| GET | `/internal/automation/function-units/{functionUnitCode}/schema` |
| GET | `/internal/automation/records/{processInstanceId}/schema` |
| GET | `/internal/automation/records/{processInstanceId}` |
| POST | `/internal/automation/processes/start` |
| POST | `/internal/automation/records/query` |
| PATCH | `/internal/automation/records/{processInstanceId}/fields` |
| POST | `/internal/automation/records/{processInstanceId}/sub-tables/{bindingId}/rows` |
| PUT | `/internal/automation/records/{processInstanceId}/sub-tables/{bindingId}/rows/{rowId}` |
| DELETE | `/internal/automation/records/{processInstanceId}/sub-tables/{bindingId}/rows/{rowId}` |

Query requires at least one main-table field filter and `size` from 1 to 50. A flow with no scope, the wrong catalog, or a missing capability is 403. The history user is the workspace service account.

## Not in this round

Email or HTTP steps do not gain a connection picker. No API returns a VAULT password or connection secret. Activepieces cannot call the admin internal APIs.
