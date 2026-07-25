import { FastifyBaseLogger } from 'fastify'

// HERMES: the EE worker-group service was removed (AG-EE / EE_REMOVAL_PLAN G5).
// HERMES has no worker-group / dedicated-worker concept, so every worker is SHARED.
// Stubs: no group id, never a canary platform — the callers already treat null / false
// as the shared, non-canary default.
export const workerGroupService = (_log: FastifyBaseLogger) => ({
    getWorkerGroupId: async (_params: { platformId: string }): Promise<string | null> => null,
    isCanaryPlatform: async (_params: { platformId: string }): Promise<boolean> => false,
})
