import { apDayjsDuration, fileSystemUtils } from '@activepieces/server-utils'
import { tryCatch } from '@activepieces/shared'
import { Logger } from 'pino'
import { CommandOutput, spawnWithKill } from '../../utils/exec'

// HERMES: bun removed (X-4). The runtime piece / CODE-step dependency installer used the
// `bun` binary; it now uses `npm`. npm reads the same `workspaces` field the installer
// writes into the root package.json and hoists dependencies into the workspace's flat
// node_modules exactly like bun did (pnpm would need a pnpm-workspace.yaml + hoisted
// node-linker), so this is a drop-in replacement — the engine's piece resolution is
// unchanged. The `build` step already uses esbuild.
export const pkgRunner = (log: Logger) => ({
    async install({ path, filtersPath }: InstallParams): Promise<CommandOutput> {
        const filterArgs: string[] = filtersPath
            .map(sanitizeFilterPath)
            .flatMap((p) => ['--workspace', `./${p}`])
        const args = [
            'install',
            '--ignore-scripts',
            '--no-audit',
            '--no-fund',
            ...filterArgs,
        ]
        await fileSystemUtils.threadSafeMkdir(path)
        log.debug({ path, args }, '[pkgRunner#install]')
        const { error, data } = await tryCatch(async () => spawnWithKill({
            cmd: 'npm',
            args,
            options: {
                cwd: path,
            },
            printOutput: false,
            timeoutMs: apDayjsDuration(10, 'minutes').asMilliseconds(),
        }))
        if (error) {
            log.error({ error }, '[pkgRunner#install] Failed to install dependencies')
            throw error
        }
        return data
    },
    async build({ path, entryFile, outputFile }: BuildParams): Promise<CommandOutput> {
        const args = [
            entryFile,
            '--bundle',
            '--platform=node',
            '--format=cjs',
            `--outfile=${outputFile}`,
        ]
        log.debug({ path, entryFile, outputFile, args }, '[pkgRunner#build]')
        return spawnWithKill({
            cmd: 'esbuild',
            args,
            options: { cwd: path },
            printOutput: false,
            timeoutMs: apDayjsDuration(5, 'minutes').asMilliseconds(),
        })
    },
})

function sanitizeFilterPath(filterPath: string): string {
    const allowed = /^(?![.])[a-zA-Z0-9\-_.@/]+$/
    if (!allowed.test(filterPath)) {
        throw new Error(`Invalid filter path ${filterPath}`)
    }
    return filterPath
}

type InstallParams = {
    path: string
    filtersPath: string[]
}

type BuildParams = {
    path: string
    entryFile: string
    outputFile: string
}
