import { ActivepiecesError, assertNotNullOrUndefined, ErrorCode, isNil } from '@activepieces/core-utils'
import { cryptoUtils } from '@activepieces/server-utils'
import { ApEdition, ApFlagId, AuthenticationResponse, PlatformWithoutSensitiveData, User, UserIdentity, UserIdentityProvider } from '@activepieces/shared'
import { FastifyBaseLogger } from 'fastify'
import { flagService } from '../flags/flag.service'
import { system } from '../helper/system/system'
import { AppSystemProp } from '../helper/system/system-props'
import { platformService } from '../platform/platform.service'
import { userService } from '../user/user-service'
import { authenticationUtils } from './authentication-utils'
import { userIdentityService } from './user-identity/user-identity-service'

export const authenticationService = (log: FastifyBaseLogger) => ({
    async signUp(params: SignUpParams): Promise<AuthenticationResponse> {
        // HERMES-PATCH-022: credential sign-up is closed for EXISTING platforms, explicitly.
        //
        // Identity is HERMES's, not AP's: users arrive through the managed-authn handshake
        // (JWT provider) and are mirrored as shadow users. The sign-up UI went with FR-D2,
        // but `POST /v1/authentication/sign-up` stayed `securityAccess.public()` and is
        // reachable through the Kong `/api/ap` route, which carries no auth plugin.
        //
        // It was already refused — but only because THREE unrelated conditions happened to
        // line up: (1) the upstream ALLOW_OPEN_SIGN_UP switch had no entry in the defaults
        // table, so it read `undefined` and failed the `!== 'true'` test; (2) the EE strip left
        // assertUserIsInvitedToPlatformOrProject throwing unconditionally — a side effect of
        // deleting the invitations domain, not a deliberate gate; (3) getPlatformIdForRequest
        // resolves the oldest platform in CE. Setting that switch while debugging broke the
        // whole chain, so it is now removed outright — refusing here does not depend on it.
        //
        // NOT an unconditional throw: `deploy/k8s/ap-bootstrap-job.yaml` signs the shared
        // account up as AP's very first user on an EMPTY database, which is what creates the
        // initial platform. That path resolves platformId to null and must keep working, so
        // the refusal is scoped to requests that already resolve to a platform.
        if (!isNil(params.platformId)) {
            log.warn({ email: params.email }, 'Credential sign-up rejected: identities come from the HERMES SSO handshake')
            throw new ActivepiecesError({
                code: ErrorCode.AUTHORIZATION,
                params: {
                    message: 'Credential sign-up is disabled: identities are provisioned through the HERMES SSO handshake',
                },
            })
        }

        // Only the empty-database bootstrap reaches this point (platformId is null there):
        // it creates AP's first identity, and deploy's bootstrap job then calls
        // POST /v1/platforms to create the platform itself. The former "sign up into an
        // existing platform" branch is deleted rather than left unreachable — with the
        // guard above it can never run, and dead auth code invites future confusion.
        // HERMES: user-invitations domain removed (AG-EE / EE_REMOVAL_PLAN); no invitation can
        // exist, so only federated providers get pre-verified identities here.
        const isFederatedProvider = params.provider === UserIdentityProvider.GOOGLE || params.provider === UserIdentityProvider.JWT || params.provider === UserIdentityProvider.SAML
        const userIdentity = await userIdentityService(log).create({
            ...params,
            verified: isFederatedProvider,
        })
        await sendVerificationOrAutoVerify(userIdentity, log)
        await flagService(log).save({ id: ApFlagId.USER_CREATED, value: true })
        await authenticationUtils(log).saveNewsLetterSubscriber(userIdentity)

        const preferredPlatformId = await getPreferredPlatformId(userIdentity.id, log)
        if (!isNil(preferredPlatformId)) {
            const user = await userService(log).getOrCreateWithProject({
                identity: userIdentity,
                platformId: preferredPlatformId,
            })
            log.info({ email: params.email, provider: params.provider, preferredPlatformId }, 'User signed up with invitation, returning preferred platform token')
            const authResponse =  await authenticationUtils(log).getProjectAndToken({
                userId: user.id,
                platformId: preferredPlatformId,
                projectId: null,
            })
            await authenticationUtils(log).sendTelemetry({ identity: userIdentity, user, projectId: authResponse.projectId ?? '' })
            return authResponse
        }
        log.info({ email: params.email, provider: params.provider }, 'User signed up without platform')
        return authenticationUtils(log).getOnboardingResponse({ identityId: userIdentity.id })

    },
    async signInWithPassword(params: SignInWithPasswordParams): Promise<AuthenticationResponse> {
        const identity = await userIdentityService(log).verifyIdentityPassword(params)
        const platformId = isNil(params.predefinedPlatformId) ? await getPreferredPlatformId(identity.id, log) : params.predefinedPlatformId

        if (isNil(platformId)) { // always cloud
            log.info({ email: params.email }, 'User signed in without an active platform on cloud, returning onboarding token')
            return authenticationUtils(log).getOnboardingResponse({ identityId: identity.id })
        }

        await authenticationUtils(log).assertEmailAuthIsEnabled({
            platformId,
            provider: UserIdentityProvider.EMAIL,
        })
        await authenticationUtils(log).assertDomainIsAllowed({
            email: params.email,
            platformId,
        })
        const user = await userService(log).getOneByIdentityAndPlatform({
            identityId: identity.id,
            platformId,
        })
        assertNotNullOrUndefined(user, 'User not found')
        log.info({ email: params.email, platform: { id: platformId } }, 'User signed in with password')
        return authenticationUtils(log).getProjectAndToken({
            userId: user.id,
            platformId,
            projectId: null,
        })
    },
    async federatedAuthn(params: FederatedAuthnParams): Promise<AuthenticationResponse> {
        const platformId = isNil(params.predefinedPlatformId) ? await getPreferredPlatformIdForFederatedAuthn(params.email, log) : params.predefinedPlatformId
        const userIdentity = await userIdentityService(log).getIdentityByEmail(params.email)

        if (isNil(platformId)) { // always cloud
            if (!isNil(userIdentity)) {
                return authenticationUtils(log).getOnboardingResponse({ identityId: userIdentity.id })
            }
            return authenticationService(log).signUp({
                email: params.email,
                firstName: params.firstName,
                lastName: params.lastName,
                newsLetter: params.newsLetter,
                trackEvents: params.trackEvents,
                provider: params.provider,
                platformId: null,
                password: await cryptoUtils.generateRandomPassword(),
                imageUrl: params.imageUrl,
            })
        }

        if (params.provider == UserIdentityProvider.SAML) {
            await authenticationUtils(log).assertEmailMatchesSsoDomain({
                email: params.email,
                platformId,
            })
        }

        if (isNil(userIdentity)) {
            return authenticationService(log).signUp({
                email: params.email,
                firstName: params.firstName,
                lastName: params.lastName,
                newsLetter: params.newsLetter,
                trackEvents: params.trackEvents,
                provider: params.provider,
                platformId,
                password: await cryptoUtils.generateRandomPassword(),
                imageUrl: params.imageUrl,
            })
        }
        const user = await userService(log).getOrCreateWithProject({
            identity: userIdentity,
            platformId,
        })
        // HERMES: user-invitations domain removed (AG-EE / EE_REMOVAL_PLAN); nothing to provision.
        return authenticationUtils(log).getProjectAndToken({
            userId: user.id,
            platformId,
            projectId: null,
        })
    },
    async switchPlatform(params: SwitchPlatformParams): Promise<AuthenticationResponse> {
        const platforms = await platformService(log).listPlatformsForIdentityWithAtleastProject({ identityId: params.identityId })
        const platform = platforms.find((platform) => platform.id === params.platformId)
        await assertUserCanSwitchToPlatform(platform)

        assertNotNullOrUndefined(platform, 'Platform not found')
        const user = await getUserForPlatform(params.identityId, platform, log)
        log.info({ user: { id: user.id }, platform: { id: platform.id } }, 'User switched platform')
        return authenticationUtils(log).getProjectAndToken({
            userId: user.id,
            platformId: platform.id,
            projectId: null,
        })
    },
})

async function assertUserCanSwitchToPlatform(platform: PlatformWithoutSensitiveData | undefined): Promise<void> {
    if (isNil(platform)) {
        throw new ActivepiecesError({
            code: ErrorCode.AUTHORIZATION,
            params: {
                message: 'The user is not a member of the platform',
            },
        })
    }
}

async function getUserForPlatform(identityId: string, platform: PlatformWithoutSensitiveData, log: FastifyBaseLogger): Promise<User> {
    const user = await userService(log).getOneByIdentityAndPlatform({
        identityId,
        platformId: platform.id,
    })
    if (isNil(user)) {
        throw new ActivepiecesError({
            code: ErrorCode.AUTHORIZATION,
            params: {
                message: 'User is not member of the platform',
            },
        })
    }
    return user
}

async function sendVerificationOrAutoVerify(userIdentity: UserIdentity, log: FastifyBaseLogger): Promise<void> {
    // HERMES: EE OTP email-verification removed (AG-EE / EE_REMOVAL_PLAN). HERMES owns the
    // identity domain; CE auto-verifies the AP shadow identity.
    await userIdentityService(log).verify(userIdentity.id)
}

async function getPreferredPlatformIdForFederatedAuthn(email: string, log: FastifyBaseLogger): Promise<string | null> {
    const identity = await userIdentityService(log).getIdentityByEmail(email)
    if (isNil(identity)) {
        return null
    }
    return getPreferredPlatformId(identity.id, log)
}

async function getPreferredPlatformId(identityId: string, log: FastifyBaseLogger): Promise<string | null> {
    const edition = system.getEdition()
    if (edition === ApEdition.CLOUD) {
        const platforms = await platformService(log).listPlatformsForIdentityWithAtleastProject({ identityId }) // this only gets platforms where user is active
        const identity = await userIdentityService(log).getOneOrFail({ id: identityId })
        const lastUsed = !isNil(identity.lastLoggedInPlatformId) ? platforms.find((p) => p.id === identity.lastLoggedInPlatformId) : undefined
        const licensed = platforms.find((p) => !isNil(p.plan.licenseKey))
        return lastUsed?.id ?? licensed?.id ?? platforms[0]?.id ?? null
    }
    return null
}



type FederatedAuthnParams = {
    email: string
    firstName: string
    lastName: string
    newsLetter: boolean
    trackEvents: boolean
    provider: UserIdentityProvider
    predefinedPlatformId: string | null
    imageUrl?: string
}

type SignUpParams = {
    email: string
    firstName: string
    lastName: string
    password: string
    platformId: string | null
    trackEvents: boolean
    newsLetter: boolean
    provider: UserIdentityProvider
    imageUrl?: string
}

type SignInWithPasswordParams = {
    email: string
    password: string
    predefinedPlatformId: string | null
}

type SwitchPlatformParams = {
    identityId: string
    platformId: string
}
