import { rm, writeFile } from 'node:fs/promises'
import path, { dirname, join } from 'node:path'
import { ensureTrailingSlash, groupBy, isEmpty, isNil, tryCatch } from '@activepieces/core-utils'
import { type ApLogger, fileSystemUtils, memoryLock, wideEvent } from '@activepieces/server-utils'
import { ExecutionMode, getPieceNameFromAlias, PackageType, PiecePackage, PieceType } from '@activepieces/shared'
import writeFileAtomic from 'write-file-atomic'
import { SandboxSettings } from '../../types'
import { pkgRunner } from '../../utils/pkg-runner'
import { cacheUtils } from '../cache-paths'

const VALID_SCOPED_NAME_REGEX = /^@[^/]+\/[^/]+$/
const VALID_UNSCOPED_NAME_REGEX = /^[^/]+$/
const relativePiecePath = (piece: PiecePackage) => join('./', 'pieces', `${piece.pieceName}-${piece.pieceVersion}`)
const piecePath = (rootWorkspace: string, piece: PiecePackage) => join(rootWorkspace, 'pieces', `${piece.pieceName}-${piece.pieceVersion}`)

export const pieceInstaller = (log: ApLogger, basePath: string, getSettings: () => SandboxSettings) => ({
    async install({ pieces, includeFilters, publicApiUrl, engineToken }: InstallParams): Promise<void> {
        const groupedPieces = groupPiecesByPackagePath(pieces, basePath, getSettings)
        const installPromises = Object.entries(groupedPieces).map(async ([packagePath, piecesInGroup]) => {
            await installPieces(packagePath, piecesInGroup, includeFilters, log, { publicApiUrl, engineToken }, getSettings)
        })
        await Promise.all(installPromises)
    },

    getCustomPiecesPath(platformId: string): string {
        return getCustomPiecesPath(basePath, platformId, getSettings)
    },
})

function getCustomPiecesPath(basePath: string, platformId: string, getSettings: () => SandboxSettings): string {
    const paths = cacheUtils(basePath)
    switch (getSettings().EXECUTION_MODE) {
        case ExecutionMode.SANDBOX_PROCESS:
        case ExecutionMode.SANDBOX_CODE_AND_PROCESS:
            return path.resolve(paths.getGlobalCachePathLatestVersion(), 'custom_pieces', platformId)
        case ExecutionMode.UNSANDBOXED:
        case ExecutionMode.SANDBOX_CODE_ONLY:
            return paths.getGlobalCacheCommonPath()
        default:
            throw new Error('Invalid execution mode')
    }
}

async function installPieces(rootWorkspace: string, pieces: PiecePackage[], includeFilters: boolean, log: ApLogger, bundleSource: BundleSource, getSettings: () => SandboxSettings): Promise<void> {
    const devPieces = getSettings().DEV_PIECES
    const nonDevPieces = pieces.filter(piece => !devPieces.includes(getPieceNameFromAlias(piece.pieceName)))
    const { validPieces, invalidPieces } = partitionValidPieceNames(nonDevPieces)
    if (!isEmpty(invalidPieces)) {
        log.error({
            rootWorkspace,
            invalidPieces: invalidPieces.map(piece => `${piece.pieceName}@${piece.pieceVersion}`),
        }, '[pieceInstaller] Skipping pieces with invalid package names to protect the shared lockfile')
    }
    const { piecesToInstall } = await partitionPiecesToInstall(rootWorkspace, validPieces)

    if (isEmpty(piecesToInstall)) {
        log.debug({ rootWorkspace }, '[pieceInstaller] No new pieces to install (already installed)')
        return
    }
    log.info({
        rootWorkspace,
        piecesToInstall: piecesToInstall.map(piece => `${piece.pieceName}-${piece.pieceVersion}`),
    }, '[pieceInstaller] Installing pieces in workspace')

    await memoryLock.runExclusive({
        key: `install-pieces-${rootWorkspace}`,
        fn: async () => {
            const { piecesToInstall } = await partitionPiecesToInstall(rootWorkspace, validPieces)
            if (isEmpty(piecesToInstall)) {
                log.info({ rootWorkspace }, '[pieceInstaller] No new pieces to install in lock (already installed)')
                return
            }
            log.info({
                rootWorkspace,
                pieces: piecesToInstall.map(piece => `${piece.pieceName}-${piece.pieceVersion}`),
            }, '[pieceInstaller] acquired lock and starting to install pieces')

            await createRootPackageJson({
                path: rootWorkspace,
            })

            await saveBundlesToDiskIfNotCached(rootWorkspace, piecesToInstall, bundleSource)

            await Promise.all(piecesToInstall.map(piece => createPiecePackageJson({
                rootWorkspace,
                piecePackage: piece,
            })))

            await wideEvent.timed({
                name: 'pkgInstall',
                fn: async () => {
                    const { error: batchError } = await tryCatch(async () => pkgRunner(log).install({
                        path: rootWorkspace,
                        filtersPath: includeFilters ? piecesToInstall.map(relativePiecePath) : [],
                    }))

                    if (isNil(batchError)) {
                        await markPiecesAsUsed(rootWorkspace, piecesToInstall)
                        log.info({
                            rootWorkspace,
                            piecesCount: piecesToInstall.length,
                        }, '[pieceInstaller] Installed registry pieces using pnpm')
                        return
                    }

                    if (piecesToInstall.length === 1) {
                        log.error({ rootWorkspace, error: batchError }, '[pieceInstaller] Piece installation failed, rolling back')
                        await rollbackInstallation(rootWorkspace, piecesToInstall)
                        throw batchError
                    }

                    log.warn({
                        rootWorkspace,
                        pieces: piecesToInstall.map(piece => `${piece.pieceName}-${piece.pieceVersion}`),
                        error: batchError,
                    }, '[pieceInstaller] Batch install failed, retrying pieces individually')

                    const failedPieces = await tryInstallPiecesIndividually(rootWorkspace, piecesToInstall, log)

                    if (failedPieces.length > 0) {
                        const names = failedPieces.map(p => `${p.pieceName}@${p.pieceVersion}`).join(', ')
                        throw new Error(`[pieceInstaller] Failed to install: ${names}`)
                    }

                    log.info({
                        rootWorkspace,
                        piecesCount: piecesToInstall.length,
                    }, '[pieceInstaller] Installed registry pieces using pnpm (individual fallback)')
                },
            })
        },
    })
}

// A workspace member name (and its dependency key) must be a plain npm package name. A relative
// path such as `../../../common/pieces/@activepieces/piece-x` — fed in via stale `usedPieces` data
// from a since-reverted build — made the package manager write an unparseable resolution token into
// the SHARED workspace lockfile (observed with the bun the fork removed; pnpm-lock.yaml is just as
// shared). That lock then fails to parse on the next install and takes down EVERY piece in the
// workspace (so cache pre-warm and the deploy fail). Worse, because the install joins the name onto
// `<workspace>/pieces/`, a `..` name escapes a per-platform `custom_pieces/<id>` workspace and lands
// the poisoned member inside the shared `common` workspace. Such names are skipped at the source.
export function isValidPackageName(name: string): boolean {
    if (name.includes('..')) {
        return false
    }
    return VALID_SCOPED_NAME_REGEX.test(name) || VALID_UNSCOPED_NAME_REGEX.test(name)
}

function partitionValidPieceNames(pieces: PiecePackage[]): { validPieces: PiecePackage[], invalidPieces: PiecePackage[] } {
    return {
        validPieces: pieces.filter(piece => isValidPackageName(piece.pieceName)),
        invalidPieces: pieces.filter(piece => !isValidPackageName(piece.pieceName)),
    }
}

async function rollbackInstallation(rootWorkspace: string, pieces: PiecePackage[]): Promise<void> {
    await Promise.all(pieces.map(piece => rm(path.resolve(rootWorkspace, relativePiecePath(piece)), {
        recursive: true,
        force: true,
    })))
}

async function tryInstallPiecesIndividually(
    rootWorkspace: string,
    pieces: PiecePackage[],
    log: ApLogger,
): Promise<PiecePackage[]> {
    const failures: PiecePackage[] = []
    for (const piece of pieces) {
        const { error } = await tryCatch(async () =>
            pkgRunner(log).install({
                path: rootWorkspace,
                filtersPath: [relativePiecePath(piece)],
            }),
        )
        if (error) {
            log.error({
                piece: `${piece.pieceName}@${piece.pieceVersion}`,
                error,
            }, '[pieceInstaller] Individual piece installation failed, rolling back')
            await rollbackInstallation(rootWorkspace, [piece])
            failures.push(piece)
        }
        else {
            await markPiecesAsUsed(rootWorkspace, [piece])
        }
    }
    return failures
}

function groupPiecesByPackagePath(pieces: PiecePackage[], basePath: string, getSettings: () => SandboxSettings): Record<string, PiecePackage[]> {
    const paths = cacheUtils(basePath)
    return groupBy(pieces, (piece) => {
        switch (piece.packageType) {
            case PackageType.ARCHIVE:
                return getCustomPiecesPath(basePath, piece.platformId, getSettings)
            case PackageType.REGISTRY: {
                if (piece.pieceType === PieceType.CUSTOM && !isNil(piece.platformId)) {
                    return getCustomPiecesPath(basePath, piece.platformId, getSettings)
                }
                return paths.getGlobalCacheCommonPath()
            }
            default:
                throw new Error('Invalid package type')
        }
    })
}

async function createRootPackageJson({ path }: { path: string }): Promise<void> {
    const packageJsonPath = join(path, 'package.json')
    await fileSystemUtils.threadSafeMkdir(dirname(packageJsonPath))
    await writeFileAtomic(packageJsonPath, JSON.stringify({
        'name': 'fast-workspace',
        'version': '1.0.0',
        'workspaces': [
            'pieces/**',
        ],
    }, null, 2), 'utf8')
    // HERMES-PATCH-005: bun removed (FR-A07, D-13). pnpm needs a pnpm-workspace.yaml (it
    // does not read the package.json `workspaces` field) and must use the isolated linker
    // so each piece's dependency lands in `pieces/<name>-<ver>/node_modules/<name>` — the
    // layout the engine's piece loader resolves. See pkg-runner.ts.
    await writeFileAtomic(join(path, 'pnpm-workspace.yaml'), 'packages:\n  - "pieces/**"\n', 'utf8')
    await writeFileAtomic(join(path, '.npmrc'), 'node-linker=isolated\nignore-workspace-root-check=true\n', 'utf8')
}

async function createPiecePackageJson({ rootWorkspace, piecePackage }: {
    rootWorkspace: string
    piecePackage: PiecePackage
}): Promise<void> {
    const packageJsonPath = join(piecePath(rootWorkspace, piecePackage), 'package.json')

    const packageJson = {
        'name': `${piecePackage.pieceName}-${piecePackage.pieceVersion}`,
        'version': `${piecePackage.pieceVersion}`,
        'dependencies': {
            [piecePackage.pieceName]: bundleTgzPath(rootWorkspace, piecePackage),
        },
    }
    await fileSystemUtils.threadSafeMkdir(dirname(packageJsonPath))
    await writeFile(packageJsonPath, JSON.stringify(packageJson, null, 2), 'utf8')
}

function bundleTgzPath(rootWorkspace: string, piece: PiecePackage): string {
    return join(piecePath(rootWorkspace, piece), 'bundle.tgz')
}

// Downloads each piece tarball from the engine bundle endpoint (which 307-redirects to npm /
// signed-S3, or streams the custom archive) to a local .tgz. We download here — rather than handing
// the URL to the package-manager install (upstream handed it to bun, since removed) — because a
// package manager derives a cache directory name from the dependency spec, and a long signed-S3 /
// engine-token URL overflows the filesystem name limit (ENAMETOOLONG). A local tgz path also keeps
// pnpm's `--offline` install (AP_PIECES_OFFLINE_INSTALL) free of any URL dependency specs.
// `fetch` follows the redirect and carries the engine token in the Authorization header.
// ARCHIVE pieces are fetched by archiveId (they may not be registered in metadata yet, e.g. during
// EXTRACT_PIECE_METADATA); REGISTRY pieces by name@version.
async function saveBundlesToDiskIfNotCached(rootWorkspace: string, pieces: PiecePackage[], { publicApiUrl, engineToken }: BundleSource): Promise<void> {
    await Promise.all(pieces.map(async (piece) => {
        const bundlePath = bundleTgzPath(rootWorkspace, piece)
        if (await fileSystemUtils.fileExists(bundlePath)) {
            return
        }
        const url = pieceBundleEndpointUrl(publicApiUrl, piece)
        const response = await fetch(url, { headers: { Authorization: `Bearer ${engineToken}` } })
        if (!response.ok) {
            throw new Error(`Failed to fetch piece bundle ${piece.pieceName}@${piece.pieceVersion}: ${response.status} ${response.statusText}`)
        }
        await fileSystemUtils.threadSafeMkdir(dirname(bundlePath))
        await writeFile(bundlePath, Buffer.from(await response.arrayBuffer()))
    }))
}

function pieceBundleEndpointUrl(publicApiUrl: string, piece: PiecePackage): string {
    const base = `${ensureTrailingSlash(publicApiUrl)}v1/engine/pieces/bundle`
    if (piece.packageType === PackageType.ARCHIVE) {
        return `${base}?archiveId=${encodeURIComponent(piece.archiveId)}`
    }
    return `${base}?name=${encodeURIComponent(piece.pieceName)}&version=${encodeURIComponent(piece.pieceVersion)}`
}

async function partitionPiecesToInstall(rootWorkspace: string, pieces: PiecePackage[]): Promise<PieceInstallationResult> {
    const piecesWithCheck = await Promise.all(
        pieces.map(async (piece) => {
            const installed = await pieceCheckIfAlreadyInstalled(rootWorkspace, piece)
            return { piece, installed }
        }),
    )

    const piecesToInstall = piecesWithCheck.filter(({ installed }) => !installed).map(({ piece }) => piece)

    return {
        piecesToInstall,
    }
}

// HERMES: this probes `node_modules/<pieceName>` — the exact path the engine's piece loader
// resolves (piece-loader.ts#resolveInstalledPieceEntry) — and NOT the bare `node_modules`
// directory upstream checked. The weaker check made a purged install unrecoverable: another
// pnpm run against this shared workspace can empty a member's `node_modules` without removing
// it (observed when the prewarm and the runtime installer disagreed on the pnpm version — a
// filtered install then prunes every sibling member). `ready` survives that, so the bare-dir
// check reported "installed" forever and every run failed with PieceNotFound instead of
// reinstalling. `fileExists` uses access(), which follows symlinks, so pnpm's isolated-linker
// symlink into `node_modules/.pnpm` counts as present only while its target still exists —
// a dangling link reads as missing, which is what we want.
//
// The in-memory `installed` memo upstream kept here is gone with it, and for the same reason:
// it answered from a snapshot taken before the purge, so the disk check never ran and the
// process stayed broken until it was restarted — proven by running the mismatched-pnpm
// scenario with the disk check in place and still getting PieceNotFound. What it saved was
// two access() calls per piece per provision, against a provision that already copies the
// engine, builds code steps and may spawn pnpm. Correctness is worth those two syscalls.
async function pieceCheckIfAlreadyInstalled(rootWorkspace: string, piece: PiecePackage): Promise<boolean> {
    const pieceFolder = piecePath(rootWorkspace, piece)
    const readyExists = await fileSystemUtils.fileExists(join(pieceFolder, 'ready'))
    if (!readyExists) {
        return false
    }
    const packageExists = await fileSystemUtils.fileExists(join(pieceFolder, 'node_modules', piece.pieceName))
    if (!packageExists) {
        await rm(join(pieceFolder, 'ready'), { force: true })
        return false
    }
    return true
}

async function markPiecesAsUsed(rootWorkspace: string, pieces: PiecePackage[]): Promise<void> {
    const writeToDiskJobs = pieces.map(async (piece) => {
        const pieceFolder = piecePath(rootWorkspace, piece)
        await fileSystemUtils.threadSafeMkdir(pieceFolder)
        await writeFileAtomic(
            join(pieceFolder, 'ready'),
            'true',
        )
    })
    await Promise.all(writeToDiskJobs)
}

type InstallParams = {
    pieces: PiecePackage[]
    includeFilters: boolean
    publicApiUrl: string
    engineToken: string
}

type BundleSource = {
    publicApiUrl: string
    engineToken: string
}

type PieceInstallationResult = {
    piecesToInstall: PiecePackage[]
}
