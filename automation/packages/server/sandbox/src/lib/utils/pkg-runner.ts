import { tryCatch } from '@activepieces/core-utils'
import { apDayjsDuration, fileSystemUtils } from '@activepieces/server-utils'
import { type ApLogger } from '@activepieces/server-utils'
import { CommandOutput, spawnWithKill } from './exec'

// HERMES-PATCH-005: bun removed (FR-A02/FR-A07, D-13). The runtime piece / CODE-step
// dependency installer used the `bun` binary; it now uses `pnpm`. This must NOT hoist:
// the engine's piece loader resolves a piece at
// `pieces/<name>-<ver>/node_modules/<name>` — the per-workspace-member node_modules
// that bun's isolated linker produced. pnpm's isolated (symlinked) linker matches this
// exactly (npm hoists to the root node_modules and breaks it with PieceNotFound).
// The installer writes a pnpm-workspace.yaml + `.npmrc` (node-linker=isolated)
// alongside the root package.json — see piece-installer.ts#createRootPackageJson.
// The `build` step already uses esbuild.
export const pkgRunner = (log: ApLogger) => ({
    async install({ path, filtersPath, ignoreWorkspace = false }: InstallParams): Promise<CommandOutput> {
        const filterArgs: string[] = filtersPath
            .map(sanitizeFilterPath)
            .flatMap((p) => ['--filter', `./${p}`])
        // HERMES-PATCH-032: `--ignore-workspace` makes this install stand alone — `path` is
        // resolved as the ONLY package, and no pnpm-workspace.yaml above it is consulted.
        // The piece installer needs that (see piece-installer.ts#installPiecesIndividually):
        // a filtered install at a shared workspace root still re-resolves every OTHER member,
        // and in an air-gapped cluster that reaches the fail-closed registry and fails.
        const workspaceArgs = ignoreWorkspace ? ['--ignore-workspace'] : []
        // HERMES-PATCH-005 (piece-admin P3): air-gapped runtime install. When
        // AP_PIECES_OFFLINE_INSTALL=true, resolve exclusively from the pnpm store
        // baked into the image (Dockerfile seeds it with the pieces-framework/-common/
        // shared/tslib closure — exactly what build-piece pins into a piece tarball).
        // ARCHIVE piece installs then need no registry at all (X-3); anything outside
        // the baked closure fails loudly instead of silently reaching for a registry.
        // Default off: dev keeps resolving REGISTRY whitelist pieces from npm.
        // --registry here is a cache NAMESPACE, not a network target: pnpm keys its
        // offline metadata cache by registry hostname (~/.cache/pnpm/metadata-v1.3/<host>/),
        // so this must match the registry used by the Dockerfile store-bake step, and it
        // must override the fail-closed NPM_CONFIG_REGISTRY env (CLI wins over env).
        // --offline still guarantees zero network access.
        const offlineArgs = process.env['AP_PIECES_OFFLINE_INSTALL'] === 'true'
            ? [
                '--offline',
                '--registry=https://registry.npmjs.org/',
                `--config.store-dir=${process.env['AP_PIECES_OFFLINE_STORE_DIR'] ?? '/usr/src/app/pnpm-offline-store'}`,
            ]
            : []
        const args = [
            'install',
            '--ignore-scripts',
            '--config.node-linker=isolated',
            '--config.confirmModulesPurge=false',
            ...workspaceArgs,
            ...offlineArgs,
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
    /** Install `path` on its own, ignoring any pnpm workspace it sits in. Default false. */
    ignoreWorkspace?: boolean
}

type BuildParams = {
    path: string
    entryFile: string
    outputFile: string
}
