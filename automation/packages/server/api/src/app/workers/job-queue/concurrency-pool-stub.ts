import { FastifyBaseLogger } from 'fastify'

// HERMES: the EE concurrency-pool service was removed (AG-EE / EE_REMOVAL_PLAN G5).
// HERMES has no tenant/BU quota model (C13), so there is no pool and jobs run
// unthrottled. Returning null means "no pool assigned" / "no limit", which the callers
// already handle as the unlimited path.
export const concurrencyPoolService = (_log: FastifyBaseLogger) => ({
    getProjectPoolId: async (_projectId: string): Promise<string | null> => null,
    getPoolLimit: async (_poolId: string): Promise<number | null> => null,
})
