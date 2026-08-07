import { ActivepiecesError, DefaultProjectRole, ErrorCode, isNil, PlatformRole, SigningKey, SigningKeyId } from '@activepieces/shared'
import { FastifyBaseLogger } from 'fastify'
import { z } from 'zod'
import { JwtSignAlgorithm, jwtUtils } from '../../helper/jwt-utils'
import { projectRoleService } from '../../project/project-role.service'
import { signingKeyService } from '../../signing-key/signing-key-service'

// Verifies the HERMES-signed external token: reads `kid` from the JWT header, looks up the
// SigningKey publicKey, verifies RS256, maps the payload to an ExternalPrincipal.
//
// The payload schema is OURS, not the upstream ee token contract (D13): admin-center's
// ServiceTaskApiClient#buildExternalToken is the only signer that will ever exist here, so
// every field is required and there are no version variants to negotiate. The upstream
// v1/v2/v3 union plus its `pieces` / `concurrencyPool*` fields were parsed-then-discarded —
// keeping them made "who could send this?" unanswerable for the next reader.
const ALGORITHM = JwtSignAlgorithm.RS256

export const externalTokenExtractor = (log: FastifyBaseLogger) => {
    return {
        async extract(token: string): Promise<ExternalPrincipal> {
            const decoded = jwtUtils.decode<ExternalTokenPayload>({ jwt: token })

            const signingKeyId = decoded?.header?.kid

            if (isNil(signingKeyId)) {
                throw new ActivepiecesError({
                    code: ErrorCode.INVALID_BEARER_TOKEN,
                    params: {
                        message: 'signing key id is not found in the header',
                    },
                })
            }

            const signingKey = await getSigningKey({
                signingKeyId,
            })

            try {
                const payload = await jwtUtils.decodeAndVerify<ExternalTokenPayload>({
                    jwt: token,
                    key: signingKey.publicKey,
                    algorithm: ALGORITHM,
                    issuer: null,
                })

                const projectRole = await projectRoleService.getOneOrThrow({
                    name: payload.role,
                    platformId: signingKey.platformId,
                })

                return {
                    platformId: signingKey.platformId,
                    externalUserId: payload.externalUserId,
                    externalProjectId: payload.externalProjectId,
                    externalFirstName: payload.firstName,
                    externalLastName: payload.lastName,
                    projectRole: projectRole.name,
                    platformRole: payload.platformRole,
                }
            }
            catch (error) {
                log.error({ err: error }, '[externalTokenExtractor#extract] Failed to extract external token')

                throw new ActivepiecesError({
                    code: ErrorCode.INVALID_BEARER_TOKEN,
                    params: {
                        message:
                            error instanceof Error ? error.message : 'error decoding token',
                    },
                })
            }
        },
    }
}

const getSigningKey = async ({
    signingKeyId,
}: GetSigningKeyParams): Promise<SigningKey> => {
    const signingKey = await signingKeyService.get({
        id: signingKeyId,
    })

    if (isNil(signingKey)) {
        throw new ActivepiecesError({
            code: ErrorCode.INVALID_BEARER_TOKEN,
            params: {
                message: `signing key not found signingKeyId=${signingKeyId}`,
            },
        })
    }

    return signingKey
}

export const ExternalTokenPayload = z.object({
    externalUserId: z.string().min(1),
    externalProjectId: z.string().min(1),
    firstName: z.string().min(1),
    lastName: z.string(),
    role: z.enum(DefaultProjectRole),
    platformRole: z.enum(PlatformRole),
})

export type ExternalTokenPayload = z.infer<typeof ExternalTokenPayload>

export type ExternalPrincipal = {
    platformId: string
    externalUserId: string
    externalProjectId: string
    externalFirstName: string
    externalLastName: string
    projectRole: string
    platformRole: PlatformRole
}

type GetSigningKeyParams = {
    signingKeyId: SigningKeyId
}
