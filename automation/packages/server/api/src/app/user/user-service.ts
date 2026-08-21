import { ActivepiecesError, apId, assertNotNullOrUndefined, Cursor, ErrorCode, isNil, PlatformId, ProjectId, SeekPage, spreadIfDefined, UserId } from '@activepieces/core-utils'
import { ApEdition, PlatformRole, ProjectType, User, UserIdentity, UserStatus, UserWithMetaInformation } from '@activepieces/shared'
import dayjs from 'dayjs'
import { FastifyBaseLogger } from 'fastify'
import { nanoid } from 'nanoid'
import { EntityManager, In, IsNull } from 'typeorm'
import { userIdentityRepository, userIdentityService } from '../authentication/user-identity/user-identity-service'
import { repoFactory } from '../core/db/repo-factory'
// HERMES: ee deleted (EE_REMOVAL_PLAN G2/G7) — project-member repo lives at its CE path;
// platform-plan and platform-project-service are gone (see update()/delete() below).
import { buildPaginator } from '../helper/pagination/build-paginator'
import { paginationHelper } from '../helper/pagination/pagination-utils'
import { system } from '../helper/system/system'
import { platformService } from '../platform/platform.service'
import { projectRepo } from '../project/project-repo'
import { projectMemberRepo } from '../project/project-role.service'
import { projectService } from '../project/project-service'
import { UserEntity, UserSchema } from './user-entity'


export const userRepo = repoFactory(UserEntity)

export const userService = (log: FastifyBaseLogger) => ({
    async create(params: CreateParams): Promise<User> {
        const isActive = params.isActive ?? true
        const user: NewUser = {
            id: apId(),
            identityId: params.identityId,
            platformRole: params.platformRole,
            status: isActive ? UserStatus.ACTIVE : UserStatus.INACTIVE,
            externalId: params.externalId,
            platformId: params.platformId,
        }
        return userRepo().save(user)
    },
    async getOrCreateWithProject({ identity, platformId }: GetOrCreateWithProjectParams): Promise<User> {
        const user = await this.getOneByIdentityAndPlatform({
            identityId: identity.id,
            platformId,
        })
        if (isNil(user)) {
            const newUser = await this.create({
                identityId: identity.id,
                platformId,
                platformRole: PlatformRole.MEMBER,
            })

            await projectService(log).create({
                displayName: identity.firstName + '\'s Project',
                ownerId: newUser.id,
                platformId,
                type: ProjectType.PERSONAL,
            })
            return newUser
        }
        return user
    },
    async updateLastActiveDate({ id }: UpdateLastActiveDateParams): Promise<void> {
        await userRepo().update({ id }, { lastActiveDate: dayjs().toISOString() })
    },
    async update({ id, status, platformId, platformRole, externalId }: UpdateParams): Promise<UserWithMetaInformation> {
        const user = await this.getOrThrow({ id })
        assertNotNullOrUndefined(user.platformId, 'platformId')

        if (user.platformId !== platformId) {
            throw new ActivepiecesError({
                code: ErrorCode.ENTITY_NOT_FOUND,
                params: {
                    entityType: 'user',
                    entityId: id,
                },
            })
        }

        const platform = await platformService(log).getOneOrThrow(user.platformId)
        if (platform.ownerId === user.id && status === UserStatus.INACTIVE) {
            throw new ActivepiecesError({
                code: ErrorCode.VALIDATION,
                params: {
                    message: 'Admin cannot be deactivated',
                },
            })
        }

        // HERMES: the EE reactivation path (platformPlanService.checkUsersExceededLimit in a
        // transaction) is removed with the ee platform-plan domain — CE has no per-plan user
        // limit, so reactivation is a plain update (0.84 parity).
        await userRepo().update({
            id,
            platformId,
        }, {
            ...spreadIfDefined('status', status),
            ...spreadIfDefined('platformRole', platformRole),
            ...spreadIfDefined('externalId', externalId),
        })

        return this.getMetaInformation({ id })
    },
    async getUsersByIdentityId({ identityId }: GetUsersByIdentityIdParams): Promise<Pick<User, 'id' | 'platformId'>[]> {
        return userRepo().find({ where: { identityId } }).then((users) => users.map((user) => ({ id: user.id, platformId: user.platformId })))
    },
    async countByPlatformId(platformId: string): Promise<number> {
        return userRepo().countBy({ platformId })
    },
    async countActiveByPlatformId({ platformId, entityManager }: CountActiveByPlatformIdParams): Promise<number> {
        return userRepo(entityManager).countBy({ platformId, status: UserStatus.ACTIVE })
    },
    async list({ platformId, externalId, cursorRequest, limit }: ListParams): Promise<SeekPage<UserWithMetaInformation>> {
        const decodedCursor = paginationHelper.decodeCursor(cursorRequest)
        const paginator = buildPaginator({
            entity: UserEntity,
            query: {
                limit,
                afterCursor: decodedCursor.nextCursor,
                beforeCursor: decodedCursor.previousCursor,
            },
        })
        const { data, cursor } = await paginator.paginate(userRepo().createQueryBuilder('user').where({
            platformId,
            ...spreadIfDefined('externalId', externalId),
        }))

        const usersWithMetaInformation = await Promise.all(data.map(this.getMetaInformation))
        return paginationHelper.createPage<UserWithMetaInformation>(usersWithMetaInformation, cursor)
    },
    async getByIdentityId({ identityId }: GetByIdentityId): Promise<UserSchema[]> {
        return userRepo().find({ where: { identityId } })
    },
    async getOneByIdentityAndPlatform({ identityId, platformId }: GetOneByIdentityIdParams): Promise<User | null> {
        return userRepo().findOneBy({ identityId, platformId: isNil(platformId) ? IsNull() : platformId })
    },
    async get({ id }: IdParams): Promise<User | null> {
        return userRepo().findOneBy({ id })
    },
    async getOrThrow({ id }: IdParams): Promise<User> {
        const user = await userRepo().findOneBy({ id })
        if (isNil(user)) {
            throw new ActivepiecesError({
                code: ErrorCode.ENTITY_NOT_FOUND,
                params: { entityType: 'user', entityId: id },
            })
        }
        return user
    },
    async getOneOrFail({ id }: IdParams): Promise<User> {
        return userRepo().findOneOrFail({ where: { id } })
    },
    async getOneByIdAndPlatformIdOrThrow({ id, platformId }: GetOneByIdAndPlatformIdParams): Promise<UserWithMetaInformation> {
        const user = await userRepo().findOne({ where: { id, platformId } })
        if (isNil(user)) {
            throw new ActivepiecesError({
                code: ErrorCode.ENTITY_NOT_FOUND,
                params: { entityType: 'user', entityId: id },
            })
        }
        return this.getMetaInformation({ id })
    },
    async delete({ id, platformId }: DeleteParams): Promise<void> {
        const platformOwnerId = await assertNotPlatformOwner({ id, platformId, log })
        await retirePersonalProject({ userId: id, platformId, newOwnerId: platformOwnerId })
        await userRepo().delete({
            id,
            platformId,
        })
    },
    async removeFromPlatform({ id, platformId }: DeleteParams): Promise<void> {
        const platformOwnerId = await assertNotPlatformOwner({ id, platformId, log })
        const user = await this.getOneOrFail({ id })
        await retirePersonalProject({ userId: id, platformId, newOwnerId: platformOwnerId })
        await userRepo().update({
            id,
            platformId,
        }, {
            platformId: null,
        })
        await userIdentityRepository().update(user.identityId, {
            tokenVersion: nanoid(),
        })
        await userIdentityRepository().update({
            id: user.identityId,
            lastLoggedInPlatformId: platformId,
        }, {
            lastLoggedInPlatformId: null,
        })
    },

    async getByPlatformRole(id: PlatformId, role: PlatformRole): Promise<UserSchema[]> {
        return userRepo().find({ where: { platformId: id, platformRole: role }, relations: { identity: true } })
    },
    async listProjectUsers({ platformId, projectId }: ListUsersForProjectParams): Promise<UserWithMetaInformation[]> {
        const users = await getUsersForProject(platformId, projectId)
        const usersWithMetaInformation = await userRepo().find({ where: { platformId, id: In(users) }, relations: { identity: true } }).then((users) => users.map(this.getMetaInformation))
        return Promise.all(usersWithMetaInformation)
    },
    async getByPlatformAndExternalId({
        platformId,
        externalId,
    }: GetByPlatformAndExternalIdParams): Promise<User | null> {
        return userRepo().findOneBy({
            platformId,
            externalId,
        })
    },
    async getMetaInformation({ id }: IdParams): Promise<UserWithMetaInformation> {
        const user = await userRepo().findOneByOrFail({ id })
        const identity = await userIdentityService(log).getBasicInformation(user.identityId)
        return {
            id: user.id,
            email: identity.email,
            firstName: identity.firstName,
            lastName: identity.lastName,
            platformId: user.platformId,
            platformRole: user.platformRole,
            status: user.status,
            externalId: user.externalId,
            created: user.created,
            updated: user.updated,
            lastActiveDate: user.lastActiveDate,
            imageUrl: identity.imageUrl,
        }
    },

    async addOwnerToPlatform({
        id,
        platformId,
    }: UpdatePlatformIdParams): Promise<void> {
        await userRepo().update(id, {
            updated: dayjs().toISOString(),
            platformRole: PlatformRole.ADMIN,
            platformId,
        })
    },

    isUserPrivileged(user: User): boolean {
        return user.platformRole === PlatformRole.ADMIN || user.platformRole === PlatformRole.OPERATOR
    },
})


// Returns the platform owner's id — the caller hands the removed user's personal project to it
// (see retirePersonalProject).
async function assertNotPlatformOwner({ id, platformId, log }: DeleteParams & { log: FastifyBaseLogger }): Promise<UserId> {
    const platform = await platformService(log).getOneOrThrow(platformId)
    if (platform.ownerId === id) {
        throw new ActivepiecesError({
            code: ErrorCode.VALIDATION,
            params: {
                message: 'Platform owner cannot be deleted',
            },
        })
    }
    return platform.ownerId
}

async function getUsersForProject(platformId: PlatformId, projectId: string): Promise<UserId[]> {
    const platformAdmins = await userRepo().find({ where: { platformId, platformRole: PlatformRole.ADMIN } }).then((users) => users.map((user) => user.id))
    const edition = system.getEdition()
    if (edition === ApEdition.COMMUNITY) {
        return platformAdmins
    }
    const projectMembers = await projectMemberRepo().find({ where: { projectId, platformId } }).then((members) => members.map((member) => member.userId))
    return [...platformAdmins, ...projectMembers]
}

type UpdateLastActiveDateParams = {
    id: UserId
}

type GetOneByIdAndPlatformIdParams = {
    id: UserId
    platformId: PlatformId
}
type ListUsersForProjectParams = {
    projectId: ProjectId
    platformId: PlatformId
}

type DeleteParams = {
    id: UserId
    platformId: PlatformId
}


type ListParams = {
    platformId: PlatformId
    externalId?: string
    cursorRequest: Cursor
    limit?: number
}

type GetByIdentityId = {
    identityId: string
}


type GetOneByIdentityIdParams = {
    identityId: string
    platformId: PlatformId | null
}

type UpdateParams = {
    id: UserId
    status?: UserStatus
    platformId: PlatformId
    platformRole?: PlatformRole
    externalId?: string
}

type CreateParams = {
    identityId: string
    platformId: string | null
    externalId?: string
    platformRole: PlatformRole
    isActive?: boolean
}
type GetUsersByIdentityIdParams = {
    identityId: string
}

type CountActiveByPlatformIdParams = {
    platformId: string
    entityManager?: EntityManager
}

type NewUser = Omit<User, 'created' | 'updated'>

type GetByPlatformAndExternalIdParams = {
    platformId: string
    externalId: string
}

type IdParams = {
    id: UserId
}

type UpdatePlatformIdParams = {
    id: UserId
    platformId: string
}

type GetOrCreateWithProjectParams = {
    identity: UserIdentity
    platformId: string
}

// HERMES: CE reimplementation of the personal-project cleanup that lived in the ee
// platform-project-service (AG-EE / EE_REMOVAL_PLAN G7). Retires the user's personal project
// on user removal. The original also scheduled a HARD_DELETE_PROJECT system job,
// which is EE and removed; CE relies on the soft-delete only. Shadow users own a personal
// project since managed-authn provisions one per handshake (ensurePersonalProject), so
// this cleanup is load-bearing; re-provisioning after removal recreates the project.
//
// Ownership is handed to the platform owner BEFORE the soft delete: `project.ownerId` carries
// `fk_project_owner_id` with ON DELETE NO ACTION (postgres 1676238396411-initialize-schema and
// never changed since), and a soft delete leaves the row — and therefore the reference — in
// place. Without the reassignment the subsequent `userRepo().delete()` dies on the constraint
// and DELETE /v1/users/:id returns 500 for every user who owns a personal project, which under
// managed-authn is every user. The reassignment runs first so it cannot depend on whether
// TypeORM's `update()` filters soft-deleted rows.
async function retirePersonalProject({ userId, platformId, newOwnerId }: { userId: string, platformId: string, newOwnerId: string }): Promise<void> {
    const personalProject = await projectRepo().findOneBy({ platformId, ownerId: userId, type: ProjectType.PERSONAL })
    if (!isNil(personalProject)) {
        await projectRepo().update({ id: personalProject.id, platformId }, { ownerId: newOwnerId })
        await projectRepo().softDelete({ id: personalProject.id, platformId })
    }
}
