import { ActivepiecesError, AddSigningKeyRequestBody,
    ApId,
    ApplicationEventName,
    assertNotNullOrUndefined,
    ErrorCode,
    isNil,
    PrincipalType,
} from '@activepieces/shared'
import { FastifyPluginAsyncZod } from 'fastify-type-provider-zod'
import { StatusCodes } from 'http-status-codes'
import { z } from 'zod'
import { securityAccess } from '../core/security/authorization/fastify-security'
import { applicationEvents } from '../helper/application-events'
import { signingKeyService } from './signing-key-service'

// CE reimplementation of the (removed) ee signing-key controller/module.
// Platform-admin only. HERMES calls POST /v1/signing-keys ONCE to mint the
// RS256 keypair; the private key is returned only on creation and kept by
// HERMES to sign per-DW-user external tokens (see managed-authn). The embed
// feature-gate hook the ee version added is dropped — platformAdminOnly is the
// gate, and there is no embedding plan flag in the vendored CE.
const signingKeyController: FastifyPluginAsyncZod = async (app) => {
    app.post('/', AddSigningKeyRequest, async (req, res) => {
        const platformId = req.principal.platform.id
        const newSigningKey = await signingKeyService.add({
            platformId,
            displayName: req.body.displayName,
        })

        applicationEvents(req.log).sendUserEvent(req, {
            action: ApplicationEventName.SIGNING_KEY_CREATED,
            data: {
                signingKey: newSigningKey,
            },
        })

        return res.status(StatusCodes.CREATED).send(newSigningKey)
    })

    app.get('/', ListSigningKeysRequest, async (req) => {
        const platformId = req.principal.platform.id
        assertNotNullOrUndefined(platformId, 'platformId')
        return signingKeyService.list({
            platformId,
        })
    })

    app.get('/:id', GetSigningKeyRequest, async (req) => {
        const platformId = req.principal.platform.id
        assertNotNullOrUndefined(platformId, 'platformId')
        const signingKey = await signingKeyService.get({
            id: req.params.id,
        })
        if (isNil(signingKey)) {
            throw new ActivepiecesError({
                code: ErrorCode.ENTITY_NOT_FOUND,
                params: {
                    message: `SigningKey with id ${req.params.id} not found`,
                },
            })
        }
        return signingKey
    })

    app.delete('/:id', DeleteSigningKeyRequest, async (req, res) => {
        const platformId = req.principal.platform.id
        assertNotNullOrUndefined(platformId, 'platformId')
        await signingKeyService.delete({
            id: req.params.id,
            platformId,
        })
        return res.status(StatusCodes.OK).send()
    })
}

export const signingKeyModule: FastifyPluginAsyncZod = async (app) => {
    await app.register(signingKeyController, { prefix: '/v1/signing-keys' })
}

const ListSigningKeysRequest = {
    config: {
        security: securityAccess.platformAdminOnly([PrincipalType.USER]),
    },
}
const AddSigningKeyRequest = {
    config: {
        security: securityAccess.platformAdminOnly([PrincipalType.USER]),
    },
    schema: {
        body: AddSigningKeyRequestBody,
    },
}

const GetSigningKeyRequest = {
    config: {
        security: securityAccess.platformAdminOnly([PrincipalType.USER]),
    },
    schema: {
        params: z.object({
            id: ApId,
        }),
    },
}

const DeleteSigningKeyRequest = {
    config: {
        security: securityAccess.platformAdminOnly([PrincipalType.USER]),
    },
    schema: {
        params: z.object({
            id: ApId,
        }),
    },
}
