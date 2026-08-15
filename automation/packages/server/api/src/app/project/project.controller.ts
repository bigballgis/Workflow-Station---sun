import { ActivepiecesError, DefaultProjectRole, ErrorCode, isNil, Metadata, Nullable, Permission, PiecesFilterType, Principal, PrincipalType, Project, ProjectIcon, ProjectType, ProjectWithLimits, SAFE_STRING_PATTERN, SeekPage, SERVICE_KEY_SECURITY_OPENAPI, spreadIfDefined } from '@activepieces/shared'
import { FastifyBaseLogger } from 'fastify'
import { FastifyPluginAsyncZod } from 'fastify-type-provider-zod'
import { StatusCodes } from 'http-status-codes'
import { z } from 'zod'
import { ProjectResourceType } from '../core/security/authorization/common'
import { securityAccess } from '../core/security/authorization/fastify-security'
import { flowService } from '../flows/flow/flow.service'
import { platformService } from '../platform/platform.service'
import { userService } from '../user/user-service'
import { projectMemberService } from './project-member.service'
import { projectRepo, projectService } from './project-service'

// HERMES: the /v1/projects controller. It started as a CE reimplementation of the ee one
// (AG-EE / EE_REMOVAL_PLAN G6) and is now simply ours (D13). The EE platform-project-service
// (plan/usage/billing) is gone; projects are returned with the open-source default plan and
// zero analytics. GET is the builder Mount-critical surface (one of the 4 white-screen
// endpoints); create/update/delete exist because the web UI offers them.
export const projectController: FastifyPluginAsyncZod = async (app) => {
    app.get('/', ListProjectsRequest, async (request) => {
        const userId = request.principal.type === PrincipalType.SERVICE
            ? (await platformService(request.log).getOneOrThrow(request.principal.platform.id)).ownerId
            : request.principal.id
        const user = await userService(request.log).getOneOrFail({ id: userId })
        const projects = await projectService(request.log).getAllForUser({
            platformId: request.principal.platform.id,
            userId,
            isPrivileged: userService(request.log).isUserPrivileged(user),
        })
        return {
            data: projects.map(toProjectWithLimits),
            next: null,
            previous: null,
        }
    })

    app.get('/:id', GetProjectRequest, async (request) => {
        const project = await projectService(request.log).getOneOrThrow(request.projectId)
        return toProjectWithLimits(project)
    })

    app.post('/', CreateProjectRequest, async (request, reply) => {
        const platformId = request.principal.platform.id
        await assertTeamProjectsAllowed(platformId, request.log)

        const ownerId = await resolveOwnerId(request.principal, request.log)
        const project = await projectService(request.log).create({
            ownerId,
            displayName: request.body.displayName,
            platformId,
            type: ProjectType.TEAM,
            ...spreadIfDefined('externalId', request.body.externalId ?? undefined),
            ...spreadIfDefined('metadata', request.body.metadata ?? undefined),
        })
        // A TEAM project is reachable only through project_member (ownerId grants access to
        // PERSONAL projects only, see applyProjectsAccessFilters), so without this the
        // creator cannot open what they just made as soon as they are not platform-privileged.
        await projectMemberService(request.log).upsert({
            projectId: project.id,
            userId: ownerId,
            projectRoleName: DefaultProjectRole.ADMIN,
        })
        await reply.status(StatusCodes.CREATED).send(toProjectWithLimits(project))
    })

    app.post('/:id', UpdateProjectRequest, async (request) => {
        const project = await projectService(request.log).getOneOrThrow(request.params.id)
        // PERSONAL projects have no displayName of their own (the UI labels them
        // "Personal Project"), which is why UpdatePersonalProjectParams omits it upstream.
        const updated = await projectService(request.log).update(project.id, project.type === ProjectType.TEAM
            ? {
                type: ProjectType.TEAM,
                ...spreadIfDefined('displayName', request.body.displayName),
                ...spreadIfDefined('externalId', request.body.externalId),
                ...spreadIfDefined('metadata', request.body.metadata),
                ...spreadIfDefined('icon', request.body.icon),
                ...spreadIfDefined('releasesEnabled', request.body.releasesEnabled),
            }
            : {
                type: ProjectType.PERSONAL,
                ...spreadIfDefined('externalId', request.body.externalId),
                ...spreadIfDefined('metadata', request.body.metadata),
                ...spreadIfDefined('releasesEnabled', request.body.releasesEnabled),
            })
        return toProjectWithLimits(updated)
    })

    // Soft delete only: there is no deletion system job in this build (it went with the EE
    // platform-project-jobs), so the row is marked and disappears from every list while its
    // rows stay put. Hence the "no flows left" guard — a project whose flows are still
    // ENABLED would keep firing webhooks and schedules long after the UI says it is gone.
    app.delete('/:id', DeleteProjectRequest, async (request, reply) => {
        const project = await projectService(request.log).getOneOrThrow(request.params.id)
        await assertProjectIsSafeToDelete(project, request.principal.platform.id, request.log)
        await projectRepo().softDelete({ id: project.id })
        await reply.status(StatusCodes.NO_CONTENT).send()
    })
}

async function resolveOwnerId(principal: Principal, log: FastifyBaseLogger): Promise<string> {
    if (principal.type === PrincipalType.SERVICE) {
        return (await platformService(log).getOneOrThrow(principal.platform.id)).ownerId
    }
    return principal.id
}

async function assertProjectIsSafeToDelete(project: Project, callerPlatformId: string, log: FastifyBaseLogger): Promise<void> {
    if (project.platformId !== callerPlatformId) {
        throw new ActivepiecesError({
            code: ErrorCode.ENTITY_NOT_FOUND,
            params: {
                entityType: 'project',
                entityId: project.id,
            },
        })
    }
    if (project.type === ProjectType.PERSONAL) {
        throw new ActivepiecesError({
            code: ErrorCode.VALIDATION,
            params: {
                message: 'Personal projects cannot be deleted',
            },
        })
    }
    // An externalId means HERMES provisioned this project (managed-authn stamps it), and the
    // whole integration resolves the shared project by that id — deleting it would strand
    // every embedded builder and every BPMN service task.
    if (!isNil(project.externalId)) {
        throw new ActivepiecesError({
            code: ErrorCode.VALIDATION,
            params: {
                message: `Project ${project.id} is provisioned externally (externalId=${project.externalId}) and cannot be deleted here`,
            },
        })
    }
    const flows = await flowService(log).count({ projectId: project.id })
    if (flows > 0) {
        throw new ActivepiecesError({
            code: ErrorCode.VALIDATION,
            params: {
                message: `Project still has ${flows} flow(s); delete them first — this build soft-deletes projects and would leave their triggers running`,
            },
        })
    }
}

// HERMES: adapted from the 0.84 TeamProjectsLimit enum (NONE/ONE/UNLIMITED) to 0.88's
// numeric plan contract: plan.billedTeamProjectsLimit is `number | null | undefined`
// (nil = unlimited, 0 = none, n = max n). NOTE the CE default differs between forks:
// 0.84 OPEN_SOURCE_PLAN was UNLIMITED, 0.88 OPEN_SOURCE_PLAN ships
// billedTeamProjectsLimit=1, so this build rejects a second TEAM project out of the box.
async function assertTeamProjectsAllowed(platformId: string, log: FastifyBaseLogger): Promise<void> {
    const platform = await platformService(log).getOneWithPlanOrThrow(platformId)
    const limit = platform.plan.billedTeamProjectsLimit
    if (isNil(limit)) {
        return
    }
    if (limit === 0) {
        throw new ActivepiecesError({
            code: ErrorCode.VALIDATION,
            params: {
                message: 'Team projects are not available on this platform plan',
            },
        })
    }
    const teamProjects = await projectService(log).countByPlatformIdAndType(platformId, ProjectType.TEAM)
    if (teamProjects >= limit) {
        throw new ActivepiecesError({
            code: ErrorCode.FEATURE_DISABLED,
            params: {
                message: `Maximum of ${limit} team project(s) reached for this platform plan`,
            },
        })
    }
}

function toProjectWithLimits(project: Project): ProjectWithLimits {
    const { deleted: _deleted, ...rest } = project
    return {
        ...rest,
        plan: {
            id: project.id,
            created: project.created,
            updated: project.updated,
            projectId: project.id,
            locked: false,
            name: 'free',
            piecesFilterType: PiecesFilterType.NONE,
            pieces: [],
        },
        analytics: {
            totalUsers: 0,
            activeUsers: 0,
            totalFlows: 0,
            activeFlows: 0,
        },
    }
}

// HERMES: 0.88 shared renamed these contracts to CreatePlatformProjectRequest /
// UpdateProjectPlatformRequest and re-added the EE-only fields (plan pieces filters,
// globalConnectionExternalIds, alertReceiverEmail, workerGroupId...) that D13 deliberately
// dropped. The request contracts below carry only fields this build can honour, so they
// live here (server-side, like ExternalTokenPayload) instead of in shared.
//
// `.strict()` is the point, not decoration: zod's default is to STRIP unknown keys, so a
// client still sending `alertReceiverEmail` would get a 201 and silently no alerts — the
// exact silent-drop this schema exists to remove. Strict turns it into a 400 that names
// the offending field.
const UpdateProjectRequestBody = z.object({
    releasesEnabled: z.boolean().optional(),
    displayName: z.string().regex(new RegExp(SAFE_STRING_PATTERN)).optional(),
    externalId: z.string().optional(),
    metadata: z.optional(Metadata),
    icon: ProjectIcon.optional(),
}).strict()

const CreateProjectRequestBody = z.object({
    displayName: z.string().regex(new RegExp(SAFE_STRING_PATTERN)),
    externalId: Nullable(z.string()),
    metadata: Nullable(Metadata),
}).strict()

const ListProjectsRequest = {
    config: {
        security: securityAccess.publicPlatform([PrincipalType.USER, PrincipalType.SERVICE]),
    },
    schema: {
        tags: ['projects'],
        security: [SERVICE_KEY_SECURITY_OPENAPI],
        response: {
            [StatusCodes.OK]: SeekPage(ProjectWithLimits),
        },
    },
}

const CreateProjectRequest = {
    config: {
        security: securityAccess.publicPlatform([PrincipalType.USER, PrincipalType.SERVICE]),
    },
    schema: {
        tags: ['projects'],
        security: [SERVICE_KEY_SECURITY_OPENAPI],
        body: CreateProjectRequestBody,
        response: {
            [StatusCodes.CREATED]: ProjectWithLimits,
        },
    },
}

const UpdateProjectRequest = {
    config: {
        security: securityAccess.project([PrincipalType.USER, PrincipalType.SERVICE], Permission.WRITE_PROJECT, {
            type: ProjectResourceType.PARAM,
            paramKey: 'id',
        }),
    },
    schema: {
        tags: ['projects'],
        security: [SERVICE_KEY_SECURITY_OPENAPI],
        params: z.object({
            id: z.string(),
        }),
        body: UpdateProjectRequestBody,
        response: {
            [StatusCodes.OK]: ProjectWithLimits,
        },
    },
}

const DeleteProjectRequest = {
    config: {
        security: securityAccess.platformAdminOnly([PrincipalType.USER, PrincipalType.SERVICE]),
    },
    schema: {
        tags: ['projects'],
        security: [SERVICE_KEY_SECURITY_OPENAPI],
        params: z.object({
            id: z.string(),
        }),
    },
}

const GetProjectRequest = {
    config: {
        security: securityAccess.project([PrincipalType.USER, PrincipalType.SERVICE], undefined, {
            type: ProjectResourceType.PARAM,
            paramKey: 'id',
        }),
    },
    schema: {
        params: z.object({
            id: z.string(),
        }),
        response: {
            [StatusCodes.OK]: ProjectWithLimits,
        },
    },
}
