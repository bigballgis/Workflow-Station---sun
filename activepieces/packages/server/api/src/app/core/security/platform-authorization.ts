import {
    ActivepiecesError,
    ErrorCode,
    isNil,
    PlatformRole,
    PlatformWithoutSensitiveData,
    PrincipalType,
    ProjectType,
} from '@activepieces/shared'
import { FastifyRequest, onRequestAsyncHookHandler } from 'fastify'
import { projectService } from '../../project/project-service'
import { userService } from '../../user/user-service'
import { getProjectIdFromRequest } from './v2/authz/authorization-middleware'

// HERMES: CE reimplementation of the platform authorization hooks that previously lived
// in app/ee/authentication/ee-authorization.ts (AG-EE / EE_REMOVAL_PLAN). The original
// depended only on CE services — its licensing was purely positional. Ownership checks
// (real access control) are preserved; the EE plan-feature gate becomes a no-op because
// CE has no paid-plan feature gating (community features are always available).

// EE plan-feature gate is a no-op in CE: there is no paid plan, so any endpoint that is
// registered in CE is available. The handler is accepted for call-site compatibility.
export const platformMustHaveFeatureEnabled = (_handler: (platform: PlatformWithoutSensitiveData) => boolean): onRequestAsyncHookHandler =>
    async (_request, _res) => {
        // intentionally empty — CE does not plan-gate features
    }

export const projectMustBeTeamType: onRequestAsyncHookHandler =
    async (request, _res) => {
        if (request.principal.type !== PrincipalType.USER && request.principal.type !== PrincipalType.SERVICE) {
            return
        }
        const projectId = await getProjectIdFromRequest(request)
        if (isNil(projectId)) {
            throw new ActivepiecesError({
                code: ErrorCode.AUTHORIZATION,
                params: { message: 'Project ID is required' },
            })
        }
        const project = await projectService(request.log).getOneOrThrow(projectId)
        if (project.type !== ProjectType.TEAM) {
            throw new ActivepiecesError({
                code: ErrorCode.VALIDATION,
                params: { message: 'Project must be a team project' },
            })
        }
    }

export const platformMustBeOwnedByCurrentUser: onRequestAsyncHookHandler =
    async (request, _res) => {
        const principal = request.principal
        if (principal.type !== PrincipalType.USER && principal.type !== PrincipalType.SERVICE) {
            throw new ActivepiecesError({
                code: ErrorCode.AUTHORIZATION,
                params: { message: 'You are unauthenticated and cannot access this resource' },
            })
        }
        await assertPlatformOwnedByUser(principal.platform.id, request)
    }

export const platformToEditMustBeOwnedByCurrentUser: onRequestAsyncHookHandler =
    async (request, _res) => {
        if (!request.params || typeof request.params !== 'object' || !('id' in request.params) || typeof request.params.id !== 'string') {
            throw new ActivepiecesError({
                code: ErrorCode.AUTHORIZATION,
                params: { message: 'Platform ID is required' },
            })
        }
        await assertPlatformOwnedByUser(request.params.id, request)
    }

async function assertPlatformOwnedByUser(platformId: string, request: FastifyRequest): Promise<void> {
    if (isNil(platformId)) {
        throw new ActivepiecesError({
            code: ErrorCode.AUTHORIZATION,
            params: { message: 'Platform ID is required' },
        })
    }
    if (request.principal.type === PrincipalType.SERVICE) {
        return
    }
    const user = await userService(request.log).getOneOrFail({ id: request.principal.id })
    const isOwner = user.platformRole === PlatformRole.ADMIN && user.platformId === platformId
    if (!isOwner) {
        throw new ActivepiecesError({
            code: ErrorCode.AUTHORIZATION,
            params: { message: 'User is not owner of the platform' },
        })
    }
}
