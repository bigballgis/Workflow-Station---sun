import { PrincipalType } from '@activepieces/shared'
import { FastifyBaseLogger, FastifyReply, FastifyRequest } from 'fastify'
import { beforeEach, describe, expect, it, vi } from 'vitest'

/**
 * HERMES / CE scope note (AG-EE, EE_REMOVAL_PLAN G5).
 *
 * The middleware itself still ships, but the only thing that can ever switch it ON —
 * `workerGroupService().isCanaryPlatform()` — is now the CE stub in
 * `src/app/workers/worker-group-stub.ts`, which returns `false` for every platform.
 * So the CE contract this file pins is: **the middleware never proxies**, no matter how
 * CANARY_APP_URL is configured or how the platform id is resolved.
 *
 * The stub is deliberately NOT mocked here — mocking it would only prove that a mock
 * returns what the mock was told to return. The EE-era tests that asserted actual
 * forwarding (and the whole `canary-proxy.integration.test.ts` reply-from harness) were
 * removed with the EE service they exercised.
 */

const { mockSystemGet } = vi.hoisted(() => ({
    mockSystemGet: vi.fn(),
}))

vi.mock('../../../../../src/app/helper/system/system', () => ({
    system: {
        get: (...args: unknown[]) => mockSystemGet(...args),
    },
}))

const mockFlowExecutionCacheGet = vi.fn()

vi.mock('../../../../../src/app/flows/flow/flow-execution-cache', () => ({
    flowExecutionCache: () => ({
        get: (...args: unknown[]) => mockFlowExecutionCacheGet(...args),
    }),
}))

import { canaryRoutingMiddleware } from '../../../../../src/app/core/canary/canary-routing.middleware'
import { AppSystemProp } from '../../../../../src/app/helper/system/system-props'

// --- helpers ---

const mockLog: FastifyBaseLogger = {
    debug: vi.fn(),
    info: vi.fn(),
    warn: vi.fn(),
    error: vi.fn(),
    fatal: vi.fn(),
    trace: vi.fn(),
    child: vi.fn(),
    silent: vi.fn(),
    level: 'info',
} as unknown as FastifyBaseLogger

function makeRequest(overrides: Partial<FastifyRequest> = {}): FastifyRequest {
    return {
        headers: {},
        method: 'GET',
        url: '/v1/test',
        body: null,
        params: {},
        principal: undefined,
        log: mockLog,
        ...overrides,
    } as unknown as FastifyRequest
}

function makeReply(): FastifyReply {
    const reply: Record<string, unknown> = {
        sent: false,
        status: vi.fn().mockReturnThis(),
        headers: vi.fn().mockReturnThis(),
        send: vi.fn().mockReturnValue(undefined),
    }
    // If the middleware ever calls this in CE the test hangs on awaitProxy's 'finish'
    // promise rather than silently passing, so a regression here is loud.
    reply.from = vi.fn()
    return reply as unknown as FastifyReply
}

function canaryUrlConfigured(): void {
    mockSystemGet.mockImplementation((prop: AppSystemProp) =>
        prop === AppSystemProp.CANARY_APP_URL ? 'http://canary:3000' : undefined,
    )
}

// --- tests ---

describe('canaryRoutingMiddleware', () => {
    beforeEach(() => {
        vi.clearAllMocks()
        mockFlowExecutionCacheGet.mockResolvedValue({ exists: false })
    })

    it('does nothing when CANARY_APP_URL is not set', async () => {
        mockSystemGet.mockReturnValue(undefined)
        const reply = makeReply()

        await canaryRoutingMiddleware(makeRequest(), reply)

        expect(reply.from).not.toHaveBeenCalled()
    })

    it('does nothing for WebSocket upgrade requests', async () => {
        canaryUrlConfigured()
        const reply = makeReply()

        await canaryRoutingMiddleware(makeRequest({ headers: { upgrade: 'websocket' } }), reply)

        expect(reply.from).not.toHaveBeenCalled()
    })

    it('does nothing when platform ID cannot be resolved', async () => {
        canaryUrlConfigured()
        const reply = makeReply()

        await canaryRoutingMiddleware(makeRequest({ params: {}, principal: undefined }), reply)

        expect(reply.from).not.toHaveBeenCalled()
    })

    it('CE: never proxies a platform resolved from the principal — no platform is canary', async () => {
        canaryUrlConfigured()
        const reply = makeReply()

        await canaryRoutingMiddleware(makeRequest({
            url: '/v1/flows',
            principal: { type: PrincipalType.USER, platform: { id: 'platform-abc' } } as never,
        }), reply)

        expect(reply.from).not.toHaveBeenCalled()
    })

    it('CE: never proxies a platform resolved from the flowId cache — no platform is canary', async () => {
        canaryUrlConfigured()
        mockFlowExecutionCacheGet.mockResolvedValue({ exists: true, platformId: 'platform-xyz' })
        const reply = makeReply()

        await canaryRoutingMiddleware(makeRequest({
            method: 'POST',
            url: '/v1/webhooks/flow-1',
            params: { flowId: 'flow-1' },
        }), reply)

        expect(mockFlowExecutionCacheGet).toHaveBeenCalledWith({ flowId: 'flow-1', simulate: false })
        expect(reply.from).not.toHaveBeenCalled()
    })
})
