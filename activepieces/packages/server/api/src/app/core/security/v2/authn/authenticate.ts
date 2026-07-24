import { isNil, Principal, PrincipalType } from '@activepieces/shared'
import { FastifyBaseLogger } from 'fastify'
import { nanoid } from 'nanoid'
import { accessTokenManager } from '../../../../authentication/lib/access-token-manager'

// HERMES: EE API-key authentication removed (AG-EE / EE_REMOVAL_PLAN G3). CE has no API
// keys (apiKeysEnabled=false), so the 'Bearer sk-' path is gone; only JWT is accepted.
export const authenticateOrThrow = async (log: FastifyBaseLogger, rawToken: string | null): Promise<Principal> => {
    if (!isNil(rawToken) && rawToken.startsWith('Bearer ')) {
        const trimBearerPrefix = rawToken.replace('Bearer ', '')
        return accessTokenManager(log).verifyPrincipal(trimBearerPrefix)
    }
    return {
        id: nanoid(),
        type: PrincipalType.UNKNOWN,
    }
}

