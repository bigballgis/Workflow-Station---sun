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
    async install({ pieces, publicApiUrl, engineToken }: InstallParams): Promise<void> {
        const groupedPieces = groupPiecesByPackagePath(pieces, basePath, getSettings)
        const installPromises = Object.entries(groupedPieces).map(async ([packagePath, piecesInGroup]) => {
            await installPieces(packagePath, piecesInGroup, log, { publicApiUrl, engineToken }, getSettings)
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

async function installPieces(rootWorkspace: string, pieces: PiecePackage[], log: ApLogger, bundleSource: BundleSource, getSettings: () => SandboxSettings): Promise<void> {
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
                    const failures = await installPiecesIndividually(rootWorkspace, piecesToInstall, log)

                    if (!isEmpty(failures)) {
                        const names = failures.map(({ piece }) => `${piece.pieceName}@${piece.pieceVersion}`).join(', ')
                        // Carry pnpm's own message: this Error becomes the API's
                        // ENGINE_OPERATION_FAILURE body, which is all an admin importing a
                        // piece from Admin Center ever sees. Without it the real cause
                        // (offline-store miss, unreachable registry, bad tarball) is lost and
                        // the UI can only say "import failed".
                        const [{ error: firstError }] = failures
                        throw new Error(
                            `[pieceInstaller] Failed to install: ${names}: ${firstError.message}`,
                            { cause: firstError },
                        )
                    }

                    log.info({
                        rootWorkspace,
                        piecesCount: piecesToInstall.length,
                    }, '[pieceInstaller] Installed pieces using pnpm')
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

// HERMES-PATCH-032: every piece installs INSIDE ITS OWN DIRECTORY with `--ignore-workspace`,
// one pnpm run each. Upstream ran a single `pnpm install --filter ./pieces/<new>` at the
// SHARED workspace root, which is where UAT's "Import Piece (.tgz)" died:
//
//   `--filter` narrows what gets LINKED, not what gets RESOLVED. Adding one member makes pnpm
//   re-resolve the whole workspace, and every member here depends on a local `bundle.tgz`
//   (createPiecePackageJson) whose transitive deps are re-read on each install. So importing a
//   zero-dependency in-house piece pulled the dependencies of pieces prewarmed long before it
//   (@zip.js/zip.js, unpdf, jsdom, pg-format — the deps of piece-file-helper/-pdf/-text-helper/
//   -postgres) and dialled the fail-closed NPM_CONFIG_REGISTRY. 71s of pnpm retries, then
//   ERR_PNPM_META_FETCH_FAIL -> ENGINE_OPERATION_FAILURE -> "上传失败" in Admin Center.
//   Reproduced locally on pnpm 9.15.9 (the image's version), where the same install takes
//   691ms once it runs in the piece's own directory. See hermes/TRIM_LOG.md 2026-08-27.
//
// Installing per directory means a new piece can only ever need ITS OWN closure — the air-gap
// requirement (X-3) becomes checkable per piece instead of "every piece ever installed must
// stay resolvable forever". It also removes the sibling-pruning hazard documented on
// pieceCheckIfAlreadyInstalled: a workspace-level install could empty another member's
// node_modules, and no workspace-level install runs at runtime any more.
//
// Sequential, not Promise.all: concurrent pnpm processes contend on the shared store, and a
// runtime install is the rare path (prewarmed pieces never get here).
async function installPiecesIndividually(
    rootWorkspace: string,
    pieces: PiecePackage[],
    log: ApLogger,
): Promise<PieceInstallFailure[]> {
    const failures: PieceInstallFailure[] = []
    for (const piece of pieces) {
        const { error } = await tryCatch(async () =>
            pkgRunner(log).install({
                path: piecePath(rootWorkspace, piece),
                filtersPath: [],
                ignoreWorkspace: true,
            }),
        )
        if (error) {
            log.error({
                piece: `${piece.pieceName}@${piece.pieceVersion}`,
                error,
            }, '[pieceInstaller] Piece installation failed, rolling back')
            await rollbackInstallation(rootWorkspace, [piece])
            failures.push({ piece, error })
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
    //
    // HERMES-PATCH-032: the runtime install no longer runs HERE — it runs per piece directory
    // with --ignore-workspace (installPiecesIndividually). These three files stay because the
    // build-time prewarm (hermes/prewarm-pieces.sh) does install at this root, and because they
    // fence the cache directory off: without a pnpm-workspace.yaml of its own, a pnpm run in
    // this directory walks up and adopts the AP monorepo's workspace at /usr/src/app.
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
        const url = pieceBundleEndpointUrl(bundleBaseUrl(publicApiUrl), piece)
        const response = await fetch(url, { headers: { Authorization: `Bearer ${engineToken}` } })
        if (!response.ok) {
            throw new Error(`Failed to fetch piece bundle ${piece.pieceName}@${piece.pieceVersion}: ${response.status} ${response.statusText}`)
        }
        await fileSystemUtils.threadSafeMkdir(dirname(bundlePath))
        await writeFile(bundlePath, Buffer.from(await response.arrayBuffer()))
    }))
}

/**
 * HERMES-PATCH-031: base URL for pulling a piece bundle back from AP.
 *
 * `publicApiUrl` derives from AP_FRONTEND_URL, which is the address a **browser** uses.
 * This fetch, however, runs inside the AP container — so a browser-facing address is the
 * wrong thing to dial. In dev, AP_FRONTEND_URL is `http://localhost:8085/` (the edge
 * gateway); nothing listens on 8085 inside the container, so every ARCHIVE piece install
 * died with a bare `TypeError: fetch failed`, surfacing to the admin as
 * ENGINE_OPERATION_FAILURE with no hint of the cause. Admin Center's "Import Piece (.tgz)"
 * was unusable for exactly this reason — not because of the allowlist or the offline store,
 * which this path never consults.
 *
 * AP_INTERNAL_API_URL overrides the base for this call only. Webhook URLs still come from
 * the public one, because those genuinely must be reachable from outside.
 * Unset => previous behaviour, so a deployment whose public URL happens to resolve from
 * inside the pod keeps working untouched.
 */
function bundleBaseUrl(publicApiUrl: string): string {
    const internal = process.env['AP_INTERNAL_API_URL']
    if (isNil(internal) || internal.trim().length === 0) {
        return publicApiUrl
    }
    const trimmed = internal.trim().replace(/\/+$/, '')
    return `${trimmed}/api/`
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
    publicApiUrl: string
    engineToken: string
}

type PieceInstallFailure = {
    piece: PiecePackage
    error: Error
}

type BundleSource = {
    publicApiUrl: string
    engineToken: string
}

type PieceInstallationResult = {
    piecesToInstall: PiecePackage[]
}
