import { apDayjsDuration, fileSystemUtils } from '@activepieces/server-utils'
import { tryCatch } from '@activepieces/shared'
import { Logger } from 'pino'
import { CommandOutput, spawnWithKill } from '../../utils/exec'

// HERMES: bun removed (X-4). The runtime piece / CODE-step dependency installer used the
// `bun` binary; it now uses `pnpm`. This must NOT hoist: the engine's piece loader
// (piece-loader.ts#traverseAllParentFoldersToFindPiece) resolves a piece at
// `pieces/<name>-<ver>/node_modules/<name>` — the per-workspace-member node_modules that
// bun's isolated linker produced. pnpm's default (isolated / symlinked) linker matches
// this exactly (npm hoists to the root node_modules and breaks it). The installer writes a
// pnpm-workspace.yaml + `.npmrc` (node-linker=isolated) alongside the root package.json.
// The `build` step already uses esbuild.
export const pkgRunner = (log: Logger) => ({
    async install({ path, filtersPath }: InstallParams): Promise<CommandOutput> {
        const filterArgs: string[] = filtersPath
            .map(sanitizeFilterPath)
            .flatMap((p) => ['--filter', `./${p}`])
        const args = [
            'install',
            '--ignore-scripts',
            '--config.node-linker=isolated',
            '--config.confirmModulesPurge=false',
            ...filterArgs,
        ]
        await fileSystemUtils.threadSafeMkdir(path)
        log.debug({ path, args }, '[pkgRunner#install]')
        const { error, data } = await tryCatch(async () => spawnWithKill({
            cmd: 'pnpm',
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
