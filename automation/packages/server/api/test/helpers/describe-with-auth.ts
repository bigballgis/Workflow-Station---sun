import { FastifyInstance } from 'fastify'
import { createTestContext, TestContext, TestContextParams } from './test-context'

export function describeWithAuth(
    name: string,
    getApp: () => FastifyInstance,
    fn: (setup: () => Promise<TestContext>) => void,
    params?: TestContextParams,
): void {
    // HERMES: only USER remains — SERVICE principals were minted from an EE api-key, and both
    // the api-key module and its auth path went with the EE removal (G6). There is no way to
    // obtain a service token in this build, so a [SERVICE] variant could only ever fail.
    describe(`${name} [USER]`, () => {
        fn(() => createTestContext(getApp(), params))
    })
}
