export * from './lib/core/common/telemetry'
export * from './lib/core/common/telemetry-pii'
export * from './lib/core/authentication/dto/authentication-response'
export * from './lib/core/authentication/dto/sign-up-request'
export * from './lib/core/authentication/dto/sign-in-request'
export * from './lib/core/authentication/model/principal-type'
export * from './lib/core/authentication/model/principal'
export * from './lib/core/authentication/user-identity'
export * from './lib/core/user'
export * from './lib/core/federated-authn'
export * from './lib/core/file'
export * from './lib/core/flag'
export * from './lib/core/property/markdown'
export * from './lib/core/store-entry/dto/store-entry-request'
export * from './lib/core/store-entry/store-entry'
export * from './lib/core/health'
// Foundation utilities/types live in @activepieces/core-utils; shared re-exports the whole
// surface here once (instead of via per-file `export *` shim files under lib/core/common).
// The local ./lib/form-errors file remains for internal relative imports only.
export * from '@activepieces/core-utils'

// management
export * from './lib/management/platform'
export * from './lib/management/project'
export * from './lib/management/project-role/project-role.request'
export * from './lib/management/invitations'
export * from './lib/management/analytics'
export * from './lib/management/ai-providers'
export * from './lib/management/ai-tools'
export * from './lib/management/template'

// automation — flows / flow-run / engine / agents / workers were extracted to
// @activepieces/core-execution (SRE-163); shared re-exports them for backward compat.
export * from '@activepieces/core-execution'
export * from './lib/automation/app-connection/app-connection'
export * from './lib/automation/app-connection/dto/read-app-connection-request'
export * from './lib/automation/app-connection/dto/upsert-app-connection-request'
export * from './lib/automation/variable'
export * from './lib/automation/pieces'
export * from './lib/automation/webhook'
export * from './lib/automation/trigger'
export * from './lib/automation/forms'
export * from './lib/automation/mcp'
export * from './lib/automation/knowledge-base'
// HERMES: `./lib/ee/agent` deleted. Its keep-note claimed the flow-version migration chain
// needed it for AgentPieceProps/AGENT_TOOLS — that was wrong: both live in
// packages/core/piece-types/src/lib/agents.ts (re-exported via @activepieces/core-execution),
// which is what migrate-v7/v8/v14/v15/v16/v20/v22 actually import. Every symbol the ee/agent
// dir exported (PersistedAgentMessage, AgentConversation, chatVisibility, agentToolPhases, …)
// had zero consumers outside its own three unit tests, which went with it.
export * from './lib/automation/tables'
export * from './lib/automation/project-release/project-release'
export * from './lib/automation/project-release/project-release.request'
export * from './lib/automation/project-release/project-replace'
export * from './lib/automation/project-release/project-state'
export * from './lib/automation/websocket'

// ee
// HERMES: CE strip — removed exports for deleted ee contracts (api-key, product-embed,
// alerts, event-destinations, scim, embed-subdomain, piece-set); no surviving server/web
// consumer imports them. Kept dirs each retain a live consumer — see per-line notes.
export * from './lib/ee/billing' // HERMES: kept — surviving platform domain uses OPEN_SOURCE_PLAN/hasActiveSubscription/PurchasablePlan et al.
export * from './lib/ee/audit-events'
export * from './lib/ee/git-repo' // HERMES: kept — web flow-actions-menu imports GitBranchType
export * from './lib/ee/project-members/project-member-request'
export * from './lib/ee/project-members/project-member'
export * from './lib/ee/signing-key'
export * from './lib/ee/managed-authn'
export * from './lib/ee/oauth-apps' // HERMES: kept — web features/connections oauth-apps api/hooks
export * from './lib/ee/otp' // HERMES: kept — web sign-in + server authentication.service (EMAIL_VERIFICATION)
export * from './lib/ee/authn' // HERMES: kept — rolePermissions used by DB role seed/migration; requests used by web auth api
// HERMES: `./lib/ee/secret-managers` deleted. Its keep-note pointed at
// web/src/app/connections/secret-input.tsx, but FR-D2 had already reduced that component to
// a plain controlled input — it no longer imports SECRET_MANAGER_PROVIDERS_METADATA or any
// other symbol from the dir, and nothing else in server/ or web/ did either.
export * from './lib/management/project/project-requests'
