import { isNil } from '@activepieces/core-utils'
import { ApplicationEventName, PrincipalType, SignInRequest, SignUpRequest, SwitchPlatformRequest, TelemetryEventName, UserIdentityProvider } from '@activepieces/shared'
import { RateLimitOptions } from '@fastify/rate-limit'
import { FastifyPluginAsyncZod } from 'fastify-type-provider-zod'
import { securityAccess } from '../core/security/authorization/fastify-security'
import { applicationEvents } from '../helper/application-events'
import { networkUtils } from '../helper/network-utils'
import { rejectedPromiseHandler } from '../helper/promise-handler'
import { system } from '../helper/system/system'
import { AppSystemProp } from '../helper/system/system-props'
import { telemetry } from '../helper/telemetry.utils'
import { platformUtils } from '../platform/platform.utils'
import { userService } from '../user/user-service'
import { authenticationService } from './authentication.service'

export const authenticationController: FastifyPluginAsyncZod = async (
    app,
) => {
    app.post('/sign-up', SignUpRequestOptions, async (request) => {

        const platformId = await platformUtils.getPlatformIdForRequest(request)
        const signUpResponse = await authenticationService(request.log).signUp({
            ...request.body,
            provider: UserIdentityProvider.EMAIL,
            platformId: platformId ?? null,
        })

        if (!isNil(signUpResponse.platformId)) {
            applicationEvents(request.log).sendUserEvent({
                platformId: signUpResponse.platformId,
                userId: signUpResponse.id,
                projectId: signUpResponse.projectId ?? undefined,
                ip: networkUtils.extractClientRealIp(request, system.get(AppSystemProp.CLIENT_REAL_IP_HEADER)),
            }, {
                action: ApplicationEventName.USER_SIGNED_UP,
                data: {
                    source: 'credentials',
                },
            })
        }

        return signUpResponse
    })

    /**
     * HERMES-PATCH-028 — do NOT delete this route, and do NOT expose it either.
     *
     * Browser identity never comes from here: admin-center mints a per-actor AP session
     * through managed-authn, and every gateway that fronts AP terminates this path with
     * 404 (Kong `activepieces-authn-block-route`, the dev edge nginx, and the k8s
     * ap-gateway VirtualService). The AP web app no longer ships a credential form.
     *
     * It stays alive because the operational scripts still need it and reach AP on the
     * cluster-internal address (`AP_INTERNAL_URL` -> activepieces-service:80, a ClusterIP
     * that is not published), so they never traverse those gateways:
     *   deploy/scripts/ap-bootstrap-shared-account.js  (empty-database bootstrap)
     *   deploy/scripts/ap-{export,import,import-to-id}.js, ap-verify-provisioning.js
     *   deploy/ci/Jenkinsfile.ap-flows-{export,publish}
     * That "reachable in-cluster, terminated at the edge" split IS the access control —
     * an IP allowlist would not work, since uat/preprod users sit on private ranges too.
     *
     * Deleting it breaks bootstrap on a fresh database; un-blocking it at a gateway hands
     * anyone with an AP password a way around the platform.
     */
    app.post('/sign-in', SignInRequestOptions, async (request) => {

        const predefinedPlatformId = await platformUtils.getPlatformIdForRequest(request)
        const response = await authenticationService(request.log).signInWithPassword({
            email: request.body.email,
            password: request.body.password,
            predefinedPlatformId,
        })

        if (!isNil(response.platformId)) {
            applicationEvents(request.log).sendUserEvent({
                platformId: response.platformId,
                userId: response.id,
                projectId: response.projectId ?? undefined,
                ip: networkUtils.extractClientRealIp(request, system.get(AppSystemProp.CLIENT_REAL_IP_HEADER)),
            }, {
                action: ApplicationEventName.USER_SIGNED_IN,
                data: {},
            })
            rejectedPromiseHandler(telemetry(request.log).trackUser(response.id, {
                name: TelemetryEventName.SIGNED_IN,
                payload: {
                    userId: response.id,
                    platformId: response.platformId,
                },
            }, { platform: response.platformId }), request.log)
        }

        return response
    })

    app.post('/switch-platform', SwitchPlatformRequestOptions, async (request) => {
        const user = await userService(request.log).getOneOrFail({ id: request.principal.id })
        return authenticationService(request.log).switchPlatform({
            identityId: user.identityId,
            platformId: request.body.platformId,
        })
    })

}

const rateLimitOptions: RateLimitOptions = {
    max: Number.parseInt(
        system.getOrThrow(AppSystemProp.API_RATE_LIMIT_AUTHN_MAX),
        10,
    ),
    timeWindow: system.getOrThrow(AppSystemProp.API_RATE_LIMIT_AUTHN_WINDOW),
}



const SwitchPlatformRequestOptions = {
    config: {
        security: securityAccess.publicPlatform([PrincipalType.USER]),
        rateLimit: rateLimitOptions,
    },
    schema: {
        body: SwitchPlatformRequest,
    },
}

const SignUpRequestOptions = {
    config: {
        security: securityAccess.public(),
        rateLimit: rateLimitOptions,
    },
    schema: {
        body: SignUpRequest,
    },
}

const SignInRequestOptions = {
    config: {
        security: securityAccess.public(),
        rateLimit: rateLimitOptions,
    },
    schema: {
        body: SignInRequest,
    },
}
