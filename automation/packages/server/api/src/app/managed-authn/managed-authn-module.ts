import { ApplicationEventName, assertNotNullOrUndefined, AuthenticationResponse,
    ManagedAuthnRequestBody,
} from '@activepieces/shared'
import { FastifyPluginAsyncZod } from 'fastify-type-provider-zod'
import { securityAccess } from '../core/security/authorization/fastify-security'
import { applicationEvents } from '../helper/application-events'
import { networkUtils } from '../helper/network-utils'
import { system } from '../helper/system/system'
import { AppSystemProp } from '../helper/system/system-props'
import { managedAuthnService } from './managed-authn-service'

// CE reimplementation of the (removed) ee managed-authn controller/module.
// Public route (auth is the DW-signed external token in the body, verified by
// external-token-extractor against the signing key). Mounted at
// /v1/managed-authn so HERMES's :8085 bridge can exchange a per-DW-user external
// token for an AP USER token.
const managedAuthnController: FastifyPluginAsyncZod = async (
    app,
) => {
    app.post(
        '/external-token',
        ManagedAuthnRequest,
        async (req): Promise<AuthenticationResponse> => {
            const { externalAccessToken } = req.body

            const response = await managedAuthnService(req.log).externalToken({
                externalAccessToken,
            })
            assertNotNullOrUndefined(response.platformId, 'Platform ID is required')
            applicationEvents(req.log).sendUserEvent({
                platformId: response.platformId,
                userId: response.id,
                projectId: response.projectId ?? undefined,
                ip: networkUtils.extractClientRealIp(req, system.get(AppSystemProp.CLIENT_REAL_IP_HEADER)),
            }, {
                action: ApplicationEventName.USER_SIGNED_UP,
                data: {
                    source: 'managed',
                },
            })
            return response
        },
    )
}

export const managedAuthnModule: FastifyPluginAsyncZod = async (app) => {
    await app.register(managedAuthnController, { prefix: '/v1/managed-authn' })
}

const ManagedAuthnRequest = {
    config: {
        security: securityAccess.public(),
    },
    schema: {
        body: ManagedAuthnRequestBody,
    },
}
