import { isNil, McpToolDefinition, Permission } from '@activepieces/shared'
import { FastifyBaseLogger } from 'fastify'

// HERMES: EE per-project-role MCP tool gating removed (AG-EE / EE_REMOVAL_PLAN G11). CE
// does not gate MCP tools by project role (the EE rbac path was CLOUD/ENTERPRISE-only);
// API-layer RBAC still governs access. MCP tools are allowed at this layer.
export async function resolvePermissionChecker(_params: {
    userId: string
    projectId: string
    log: FastifyBaseLogger
}): Promise<PermissionChecker> {
    return ALLOW_ALL
}

export const ALLOW_ALL: PermissionChecker = {
    check: () => null,
    wrapExecute: ({ execute }) => execute,
}

function buildChecker(check: PermissionChecker['check']): PermissionChecker {
    return {
        check,
        wrapExecute: ({ execute, permission, toolTitle }) => {
            const error = check(permission, toolTitle)
            return isNil(error) ? execute : async () => error
        },
    }
}

export type PermissionChecker = {
    check: (permission: Permission | undefined, toolTitle: string) => McpToolErrorResult | null
    wrapExecute: (params: { execute: McpToolDefinition['execute'], permission: Permission | undefined, toolTitle: string }) => McpToolDefinition['execute']
}

type McpToolErrorResult = {
    content: Array<{ type: 'text', text: string }>
    isError: boolean
}
