import { randomUUID } from 'node:crypto'
import { readFile, rm, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { ExecutionMode, FlowVersionState, NetworkMode } from '@activepieces/shared'
import { ApLogger } from '@activepieces/server-utils'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

const installMock = vi.fn()
const buildMock = vi.fn()

vi.mock('../../../../../src/lib/utils/pkg-runner', () => ({
    pkgRunner: () => ({ install: installMock, build: buildMock }),
}))

// eslint-disable-next-line import/first
import { codeBuilder } from '../../../../../src/lib/cache/flow/code/code-builder'
// eslint-disable-next-line import/first
import { codeCache } from '../../../../../src/lib/cache/flow/code/code-cache'
// eslint-disable-next-line import/first
import { SandboxSettings } from '../../../../../src/lib/types'

const folders: string[] = []

function uniqueFolder(): string {
    const folder = join(tmpdir(), `code-builder-test-${randomUUID()}`)
    folders.push(folder)
    return folder
}

function createNoopLog(): ApLogger {
    const log: ApLogger = {
        level: 'silent',
        silent: () => undefined,
        info: () => undefined,
        warn: () => undefined,
        error: () => undefined,
        fatal: () => undefined,
        debug: () => undefined,
        trace: () => undefined,
        child: () => log,
    }
    return log
}

const noopLog = createNoopLog()

const getSettings = (): SandboxSettings => ({
    EXECUTION_MODE: ExecutionMode.SANDBOX_CODE_AND_PROCESS,
    DEV_PIECES: [],
    ENVIRONMENT: 'PRODUCTION',
    REUSE_SANDBOX: undefined,
    FLOW_TIMEOUT_SECONDS: 600,
    MAX_FILE_SIZE_MB: 10,
    MAX_FLOW_RUN_LOG_SIZE_MB: 10,
    NETWORK_MODE: NetworkMode.UNRESTRICTED,
    SANDBOX_MEMORY_LIMIT: '256',
    SANDBOX_PROPAGATED_ENV_VARS: [],
    SSRF_ALLOW_LIST: [],
})

function buildArtifact(packageJson: string) {
    return {
        name: 'step_1',
        flowVersionId: `fv-${randomUUID()}`,
        flowVersionState: FlowVersionState.LOCKED,
        sourceCode: {
            code: 'export const code = async () => 42',
            packageJson,
        },
    }
}

async function runStub(compiledJs: string): Promise<unknown> {
    const moduleExports: { code?: (params: unknown) => Promise<unknown> } = {}
    // Executing the generated stub proves it is syntactically valid JS.
    new Function('exports', compiledJs)(moduleExports)
    return moduleExports.code!({})
}

beforeEach(() => {
    installMock.mockReset()
    buildMock.mockReset()
})

afterEach(async () => {
    for (const f of folders) {
        await rm(f, { recursive: true, force: true })
    }
    folders.length = 0
})

describe('codeBuilder.processCodeStep', () => {
    it('degrades a dependency-install failure into a runtime-throwing stub instead of throwing', async () => {
        const codesFolderPath = uniqueFolder()
        const artifact = buildArtifact('{"dependencies":{"python":"13.4"}}')
        installMock.mockRejectedValue(
            new Error('Exit 1\nstderr: error: No version matching "13.4" found for specifier "python"'),
        )

        await expect(
            codeBuilder(noopLog, getSettings).processCodeStep({ artifact, codesFolderPath }),
        ).resolves.toBe('install-failed')

        // Compilation is skipped once install fails — the step never reaches esbuild.
        expect(buildMock).not.toHaveBeenCalled()

        const stubPath = codeCache(codesFolderPath).compiledStepPath({
            flowVersionId: artifact.flowVersionId,
            stepName: artifact.name,
        })
        const stub = await readFile(stubPath, 'utf8')

        await expect(runStub(stub)).rejects.toThrow('Failed to install dependencies')
        await expect(runStub(stub)).rejects.toThrow('No version matching "13.4"')
    })

    it('escapes backticks, template placeholders, and newlines in the install error message', async () => {
        const codesFolderPath = uniqueFolder()
        const artifact = buildArtifact('{"dependencies":{"pkg":"1.0.0"}}')
        installMock.mockRejectedValue(new Error('boom `backtick` and ${injection}\nsecond line'))

        await expect(
            codeBuilder(noopLog, getSettings).processCodeStep({ artifact, codesFolderPath }),
        ).resolves.toBe('install-failed')

        const stubPath = codeCache(codesFolderPath).compiledStepPath({
            flowVersionId: artifact.flowVersionId,
            stepName: artifact.name,
        })
        const stub = await readFile(stubPath, 'utf8')

        // runStub would throw SyntaxError if the message were interpolated unescaped.
        await expect(runStub(stub)).rejects.toThrow('boom `backtick` and ${injection}')
    })

    it('proceeds to compile when dependency install succeeds', async () => {
        const codesFolderPath = uniqueFolder()
        const artifact = buildArtifact('{"dependencies":{"pkg":"1.0.0"}}')
        installMock.mockResolvedValue({ stdout: '', stderr: '' })
        buildMock.mockResolvedValue({ stdout: '', stderr: '' })

        await expect(
            codeBuilder(noopLog, getSettings).processCodeStep({ artifact, codesFolderPath }),
        ).resolves.toBe('success')

        expect(installMock).toHaveBeenCalledTimes(1)
        expect(buildMock).toHaveBeenCalledTimes(1)
    })

    // HERMES-PATCH-033 regression: cache/v13/codes/<flowVersionId>/<stepName> has no
    // pnpm-workspace.yaml, so pnpm walks up to the AP monorepo's at /usr/src/app and — because
    // the step dir is not one of its members — runs a full "Scope: all N workspace projects"
    // install that never installs the step's own dependencies. Verified on pnpm 9.15.9, the
    // image's exact version. The step must be installed as a standalone package.
    it('installs the step in its own directory with --ignore-workspace, never adopting the AP monorepo workspace', async () => {
        const codesFolderPath = uniqueFolder()
        const artifact = buildArtifact('{"dependencies":{"is-number":"6.0.0"}}')
        installMock.mockResolvedValue({ stdout: '', stderr: '' })
        buildMock.mockResolvedValue({ stdout: '', stderr: '' })

        await expect(
            codeBuilder(noopLog, getSettings).processCodeStep({ artifact, codesFolderPath }),
        ).resolves.toBe('success')

        const stepDir = codeCache(codesFolderPath).stepDir({
            flowVersionId: artifact.flowVersionId,
            stepName: artifact.name,
        })
        expect(installMock.mock.calls.map(([params]) => params)).toEqual([
            { path: stepDir, filtersPath: [], ignoreWorkspace: true },
        ])
    })

    // The `@types/node` default is injected for every step, so the packageJson handed to pnpm is
    // never empty in practice — but a step whose own dependencies are empty must still not fall
    // back to a workspace-scoped install.
    it('keeps --ignore-workspace for a step carrying only the injected @types/node dependency', async () => {
        const codesFolderPath = uniqueFolder()
        const artifact = buildArtifact('{}')
        installMock.mockResolvedValue({ stdout: '', stderr: '' })
        buildMock.mockResolvedValue({ stdout: '', stderr: '' })

        await expect(
            codeBuilder(noopLog, getSettings).processCodeStep({ artifact, codesFolderPath }),
        ).resolves.toBe('success')

        expect(installMock).toHaveBeenCalledTimes(1)
        expect(installMock.mock.calls[0][0]).toMatchObject({ ignoreWorkspace: true })
    })

    it('does not cache a transient install failure — the next build re-runs install and self-heals (GIT-1608)', async () => {
        const codesFolderPath = uniqueFolder()
        const artifact = buildArtifact('{"dependencies":{"pkg":"1.0.0"}}')
        installMock
            .mockRejectedValueOnce(new Error('Exit 1\nstderr: FileNotFound: copying file ts4.8/stream/web.d.ts'))
            .mockResolvedValue({ stdout: '', stderr: '' })
        buildMock.mockResolvedValue({ stdout: '', stderr: '' })

        const builder = codeBuilder(noopLog, getSettings)

        await expect(builder.processCodeStep({ artifact, codesFolderPath })).resolves.toBe('install-failed')
        // Unchanged source must NOT be served from cache — the install is retried.
        await expect(builder.processCodeStep({ artifact, codesFolderPath })).resolves.toBe('success')

        expect(installMock).toHaveBeenCalledTimes(2)
        expect(buildMock).toHaveBeenCalledTimes(1)
    })

    it('rebuilds when the compiled artifact was deleted out of band, instead of serving a phantom cache hit', async () => {
        const codesFolderPath = uniqueFolder()
        const artifact = buildArtifact('{"dependencies":{"pkg":"1.0.0"}}')
        const compiledPath = codeCache(codesFolderPath).compiledStepPath({
            flowVersionId: artifact.flowVersionId,
            stepName: artifact.name,
        })
        installMock.mockResolvedValue({ stdout: '', stderr: '' })
        buildMock.mockImplementation(async () => {
            await writeFile(compiledPath, 'exports.code = async () => 42', 'utf8')
            return { stdout: '', stderr: '' }
        })

        const builder = codeBuilder(noopLog, getSettings)

        await expect(builder.processCodeStep({ artifact, codesFolderPath })).resolves.toBe('success')
        expect(buildMock).toHaveBeenCalledTimes(1)

        await expect(builder.processCodeStep({ artifact, codesFolderPath })).resolves.toBe('success')
        expect(buildMock).toHaveBeenCalledTimes(1)

        await rm(compiledPath)

        await expect(builder.processCodeStep({ artifact, codesFolderPath })).resolves.toBe('success')
        expect(buildMock).toHaveBeenCalledTimes(2)
        await expect(readFile(compiledPath, 'utf8')).resolves.toContain('exports.code')
    })

    it('builds once when concurrent runs provision the same step, instead of each rebuilding over the others', async () => {
        const codesFolderPath = uniqueFolder()
        const artifact = buildArtifact('{"dependencies":{"pkg":"1.0.0"}}')
        const compiledPath = codeCache(codesFolderPath).compiledStepPath({
            flowVersionId: artifact.flowVersionId,
            stepName: artifact.name,
        })
        installMock.mockResolvedValue({ stdout: '', stderr: '' })
        buildMock.mockImplementation(async () => {
            await writeFile(compiledPath, 'exports.code = async () => 42', 'utf8')
            return { stdout: '', stderr: '' }
        })

        const builder = codeBuilder(noopLog, getSettings)

        const concurrentCount = 5
        const statuses = await Promise.all(
            Array.from({ length: concurrentCount }, () => builder.processCodeStep({ artifact, codesFolderPath })),
        )

        expect(statuses).toEqual(Array.from({ length: concurrentCount }, () => 'success'))
        expect(buildMock).toHaveBeenCalledTimes(1)
        await expect(readFile(compiledPath, 'utf8')).resolves.toContain('exports.code')
    })

    it('caches a deterministic compile failure — install is not re-run for unchanged source', async () => {
        const codesFolderPath = uniqueFolder()
        const artifact = buildArtifact('{"dependencies":{"pkg":"1.0.0"}}')
        installMock.mockResolvedValue({ stdout: '', stderr: '' })
        buildMock.mockRejectedValue(new Error('esbuild: Unexpected token'))

        const builder = codeBuilder(noopLog, getSettings)

        await expect(builder.processCodeStep({ artifact, codesFolderPath })).resolves.toBe('compile-failed')
        // Cache hit — the deterministic failure is not retried.
        await expect(builder.processCodeStep({ artifact, codesFolderPath })).resolves.toBe('success')

        expect(installMock).toHaveBeenCalledTimes(1)
    })
})
