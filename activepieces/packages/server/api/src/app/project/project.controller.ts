import { PiecesFilterType, PrincipalType, Project, ProjectWithLimits, SeekPage, SERVICE_KEY_SECURITY_OPENAPI } from '@activepieces/shared'
import { FastifyPluginAsyncZod } from 'fastify-type-provider-zod'
import { StatusCodes } from 'http-status-codes'
import { z } from 'zod'
import { ProjectResourceType } from '../core/security/authorization/common'
import { securityAccess } from '../core/security/authorization/fastify-security'
import { platformService } from '../platform/platform.service'
import { userService } from '../user/user-service'
import { projectService } from './project-service'

// HERMES: CE reimplementation of the /v1/projects controller that lived in app/ee
// (AG-EE / EE_REMOVAL_PLAN G6). The EE platform-project-service (plan/usage/team-project
// limits, billing) is removed; CE returns projects enriched with the open-source default
// plan and zero analytics. GET is the builder Mount-critical surface (one of the 4
// white-screen endpoints). Project management (create/update/delete) was an EE platform-
// admin capability and is intentionally not exposed in CE v1 (single shared-project model).
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
