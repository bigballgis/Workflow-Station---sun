import { createHash } from 'crypto'
import { cryptoUtils } from '@activepieces/server-utils'
import {
    AuthenticationResponse,
    isNil,
    PlatformRole,
    PrincipalType,
    Project,
    ProjectType,
    User,
    UserIdentity,
    UserIdentityProvider,
} from '@activepieces/shared'
import { FastifyBaseLogger } from 'fastify'
import { accessTokenManager } from '../authentication/lib/access-token-manager'
import { userIdentityService } from '../authentication/user-identity/user-identity-service'
import { platformService } from '../platform/platform.service'
import { projectMemberService } from '../project/project-member.service'
import { projectRepo } from '../project/project-repo'
import { projectService } from '../project/project-service'
import { userService } from '../user/user-service'
import { externalTokenExtractor } from './lib/external-token-extractor'

// CE reimplementation of the (removed) ee managed-authn-service, trimmed for the
// HERMES shared-project model (L7 per-user provisioning, audit-to-person):
//   * getOrCreate the (single, shared) project by externalProjectId,
//   * getOrCreate the AP user by externalUserId (=> user.externalId maps every
//     AP action back to the originating DW person for audit),
//   * ensure the user owns a PERSONAL project (private sandbox in the sidebar),
//   * upsert the project membership with the token's projectRole,
//   * mint an AP USER token.
// The ee embed extras are intentionally dropped: concurrency pools (G5 stub),
// project pieces-filter limits (project-plan deleted in EE removal), and the
// per-external-project isolation — HERMES binds all provisioned users to one
// shared project, so there is no per-tenant limit/pool to maintain.
export const managedAuthnService = (log: FastifyBaseLogger) => ({
    async externalToken({
        externalAccessToken,
    }: AuthenticateParams): Promise<AuthenticationResponse> {
        const externalPrincipal = await externalTokenExtractor(log).extract(
            externalAccessToken,
        )

        const { project } = await getOrCreateProject({
            platformId: externalPrincipal.platformId,
            externalProjectId: externalPrincipal.externalProjectId,
        }, log)

        const user = await getOrCreateUser(externalPrincipal, log)

        await ensurePersonalProject({
            userId: user.id,
            platformId: externalPrincipal.platformId,
            firstName: externalPrincipal.externalFirstName,
        }, log)

        await projectMemberService(log).upsert({
            projectId: project.id,
            userId: user.id,
            projectRoleName: externalPrincipal.projectRole,
        })

        const identity = await userIdentityService(log).getOneOrFail({
            id: user.identityId,
        })

        const token = await accessTokenManager(log).generateToken({
            id: user.id,
            type: PrincipalType.USER,
            platform: {
                id: externalPrincipal.platformId,
            },
            tokenVersion: identity.tokenVersion,
        }, 7 * 24 * 60 * 60)
        return {
            id: user.id,
            platformRole: user.platformRole,
            status: user.status,
            externalId: user.externalId,
            platformId: user.platformId,
            firstName: identity.firstName,
            lastName: identity.lastName,
            email: identity.email,
            trackEvents: identity.trackEvents,
            newsLetter: identity.newsLetter,
            verified: identity.verified,
            token,
            projectId: project.id,
        }
    },
})

const getOrCreateUser = async (
    params: GetOrCreateUserParams,
    log: FastifyBaseLogger,
): Promise<User> => {
    const existingUser = await userService(log).getByPlatformAndExternalId({
        platformId: params.platformId,
        externalId: params.externalUserId,
    })

    if (!isNil(existingUser)) {
        // HERMES is the source of truth for role and display name, and both are only
        // written when the shadow user is first provisioned. Re-sync on every exchange so
        // an existing shadow user picks up a changed role / a real display name instead of
        // being stuck with whatever the very first token carried.
        return syncExistingUser({ existingUser, params }, log)
    }
    const identity = await getOrCreateUserIdentity(params, log)
    const user = await userService(log).create({
        externalId: params.externalUserId,
        platformId: params.platformId,
        identityId: identity.id,
        platformRole: params.platformRole,
    })
    return user
}

const syncExistingUser = async (
    { existingUser, params }: SyncExistingUserParams,
    log: FastifyBaseLogger,
): Promise<User> => {
    await syncIdentity({ identityId: existingUser.identityId, params }, log)

    if (existingUser.platformRole === params.platformRole) {
        return existingUser
    }
    await userService(log).update({
        id: existingUser.id,
        platformId: params.platformId,
        platformRole: params.platformRole,
    })
    return { ...existingUser, platformRole: params.platformRole }
}

const syncIdentity = async (
    { identityId, params }: SyncIdentityParams,
    log: FastifyBaseLogger,
): Promise<void> => {
    const identity = await userIdentityService(log).getOneOrFail({ id: identityId })
    const update: { firstName?: string, lastName?: string, email?: string } = {}
    if (identity.firstName !== params.externalFirstName || identity.lastName !== params.externalLastName) {
        update.firstName = params.externalFirstName
        update.lastName = params.externalLastName
    }
    // Shadow identities minted before the token carried an email claim hold the
    // sha256 hash email; upgrade them to the real address on the next handshake.
    // email is a unique column, so if another identity already holds the target
    // address, keep the current one instead of failing the whole sign-in.
    if (!isNil(params.externalEmail)) {
        const targetEmail = cleanEmailOtherwiseCompareFails(params.externalEmail)
        if (identity.email !== targetEmail) {
            const holder = await userIdentityService(log).getIdentityByEmail(targetEmail)
            if (isNil(holder) || holder.id === identityId) {
                update.email = targetEmail
            }
            else {
                log.warn({ identityId, targetEmail, holderId: holder.id }, '[managedAuthn#syncIdentity] target email already belongs to another identity, keeping current email')
            }
        }
    }
    if (Object.keys(update).length > 0) {
        await userIdentityService(log).update(identityId, update)
    }
}

const getOrCreateUserIdentity = async (
    params: GetOrCreateUserParams,
    log: FastifyBaseLogger,
): Promise<UserIdentity> => {
    // Real email when the token carries one (same person across sign-ins maps to
    // the same identity); hash fallback for accounts without a mail attribute.
    const cleanedEmail = isNil(params.externalEmail)
        ? generateEmailHash(params)
        : cleanEmailOtherwiseCompareFails(params.externalEmail)
    const existingIdentity = await userIdentityService(log).getIdentityByEmail(cleanedEmail)
    if (!isNil(existingIdentity)) {
        return existingIdentity
    }
    const identity = await userIdentityService(log).create({
        email: cleanedEmail,
        password: await cryptoUtils.generateRandomPassword(),
        firstName: params.externalFirstName,
        lastName: params.externalLastName,
        trackEvents: true,
        newsLetter: false,
        provider: UserIdentityProvider.JWT,
        verified: true,
    })
    return identity
}
// Every shadow user also owns a PERSONAL project (private sandbox next to the shared
// HERMES TEAM project). Visibility for PERSONAL projects is ownerId-based
// (applyProjectsAccessFilters), so no project_member row is needed. Runs on every
// handshake: pre-existing shadow users pick theirs up on the next sign-in, and a
// personal project soft-deleted by user removal is recreated on re-provisioning.
const ensurePersonalProject = async (
    { userId, platformId, firstName }: EnsurePersonalProjectParams,
    log: FastifyBaseLogger,
): Promise<void> => {
    const existingProject = await projectRepo().findOneBy({
        platformId,
        ownerId: userId,
        type: ProjectType.PERSONAL,
    })
    if (!isNil(existingProject)) {
        return
    }
    await projectService(log).create({
        displayName: `${firstName}'s Project`,
        ownerId: userId,
        platformId,
        type: ProjectType.PERSONAL,
    })
}

const getOrCreateProject = async ({
    platformId,
    externalProjectId,
}: GetOrCreateProjectParams, log: FastifyBaseLogger): Promise<{ project: Project, isNewProject: boolean }> => {
    const existingProject = await projectService(log).getByPlatformIdAndExternalId({
        platformId,
        externalId: externalProjectId,
    })

    if (!isNil(existingProject)) {
        return { project: existingProject, isNewProject: false }
    }

    const platform = await platformService(log).getOneOrThrow(platformId)

    const project = await projectService(log).create({
        displayName: externalProjectId,
        ownerId: platform.ownerId,
        platformId,
        externalId: externalProjectId,
        type: ProjectType.TEAM,
    })

    return { project, isNewProject: true }
}

function generateEmailHash(params: { platformId: string, externalUserId: string }): string {
    const inputString = `managed_${params.platformId}_${params.externalUserId}`
    return cleanEmailOtherwiseCompareFails(createHash('sha256').update(inputString).digest('hex'))
}

function cleanEmailOtherwiseCompareFails(email: string): string {
    return email.trim().toLowerCase()
}

type AuthenticateParams = {
    externalAccessToken: string
}

type GetOrCreateUserParams = {
    platformId: string
    externalUserId: string
    externalProjectId: string
    externalFirstName: string
    externalLastName: string
    externalEmail?: string
    platformRole: PlatformRole
}

type GetOrCreateProjectParams = {
    platformId: string
    externalProjectId: string
}

type EnsurePersonalProjectParams = {
    userId: string
    platformId: string
    firstName: string
}

type SyncExistingUserParams = {
    existingUser: User
    params: GetOrCreateUserParams
}

type SyncIdentityParams = {
    identityId: string
    params: GetOrCreateUserParams
}
