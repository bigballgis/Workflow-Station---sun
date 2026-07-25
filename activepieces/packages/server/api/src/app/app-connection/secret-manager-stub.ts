import { FastifyBaseLogger } from 'fastify'

// HERMES: the EE secret-managers service was removed (AG-EE / EE_REMOVAL_PLAN G4).
// HERMES exposes no HTTP-side secret manager (C9) and AP retains AP_ENCRYPTION_KEY, so
// connection values are used as-is — there is no external secret-manager reference to
// resolve. These identity stubs preserve the original call sites without behavioural
// change beyond dropping secret-manager indirection.
export const secretManagersService = (_log: FastifyBaseLogger) => ({
    resolveString: async ({ key }: ResolveStringParams): Promise<string> => key,
    resolveObject: async <T extends Record<string, unknown>>({ value }: ResolveObjectParams<T>): Promise<T> => value,
})

export function containsSecretManagerReference(_value: unknown): boolean {
    return false
}

type ResolveStringParams = { key: string, platformId: string, projectIds?: string[], throwOnFailure?: boolean }
type ResolveObjectParams<T> = { value: T, platformId: string, projectIds?: string[], throwOnFailure?: boolean }
