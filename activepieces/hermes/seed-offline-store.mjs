/**
 * HERMES-PATCH(piece-admin P3): seed the baked pnpm offline store.
 *
 * The store exists for ONE job: let the worker install an ARCHIVE piece tarball at
 * runtime with `--offline` (`AP_PIECES_OFFLINE_INSTALL=true`), no registry reachable
 * (X-3). So the closure it must hold is defined by **what the in-house piece tarballs
 * actually pin**, which is what this script reads — the tarball-bearing entries of
 * `hermes/pieces.json`, then each tarball's own `package/package.json` dependencies.
 *
 * It used to derive the versions from the vendored source tree
 * (`packages/{shared,pieces/framework,pieces/common}/package.json`) on the theory that
 * "read from the tree ⇒ can never drift". That was the wrong anchor, twice over:
 *
 *   1. A tarball pins the versions of whenever IT was built. `2db9b6ca6` bumped
 *      `@activepieces/shared` 0.78.1 → 0.78.2 without rebuilding the pieces, so the
 *      tree and the tarballs disagreed and the store got seeded with a version no piece
 *      asks for.
 *   2. Our bumps are local. `@activepieces/shared@0.78.2` does not exist on npmjs and
 *      never will, so `pnpm install` at that version 404s and the whole image build dies.
 *
 * KNOWN GAP (fail-loud by design): a self-developed piece rebuilt against a locally
 * bumped `packages/shared` pins that unpublished version, and seeding it here will fail
 * with ERR_PNPM_NO_MATCHING_VERSION. There is no registry that can serve it — the fix
 * then is to publish/mirror the workspace packages into the internal Nexus (or pack them
 * from the workspace) and point this script at that source. Failing the build is the
 * correct outcome: a silently short store means the piece import fails in the cluster,
 * where there is no network left to recover with.
 */
import { execFileSync } from 'node:child_process'
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const REGISTRY = 'https://registry.npmjs.org/'

const hermesDir = process.argv[2]
if (!hermesDir) {
    throw new Error('usage: node seed-offline-store.mjs <hermes-dir> <store-dir>')
}
const storeDir = process.argv[3]
if (!storeDir) {
    throw new Error('usage: node seed-offline-store.mjs <hermes-dir> <store-dir>')
}

const allowlist = JSON.parse(readFileSync(join(hermesDir, 'pieces.json'), 'utf-8'))
const inHousePieces = allowlist.filter((piece) => piece.tarball)
if (inHousePieces.length === 0) {
    console.log('[seed-offline-store] no tarball-bearing pieces in pieces.json — nothing to seed')
    process.exit(0)
}

const dependencies = {}
for (const piece of inHousePieces) {
    const tarballPath = join(hermesDir, 'tarballs', piece.tarball)
    const manifest = JSON.parse(execFileSync('tar', ['-xzOf', tarballPath, 'package/package.json'], {
        encoding: 'utf-8',
        maxBuffer: 16 * 1024 * 1024,
    }))
    for (const [name, range] of Object.entries(manifest.dependencies ?? {})) {
        // Every pin goes in under an alias (`npm:` protocol). Two in-house pieces built
        // against different baselines pin different versions of the same package, and one
        // package.json cannot depend on both under its real name; the store itself is
        // content-addressed per package@version, so aliasing costs nothing and keeps
        // "seed exactly what the tarballs ask for" literally true.
        dependencies[aliasFor({ name, range })] = `npm:${name}@${range}`
    }
    console.log(`[seed-offline-store] ${piece.name}@${piece.version} pins ${JSON.stringify(manifest.dependencies ?? {})}`)
}

function aliasFor({ name, range }) {
    const slug = `${name}-${range}`.replace(/[^a-zA-Z0-9]+/g, '-').replace(/^-+|-+$/g, '').toLowerCase()
    return `seed-${slug}`
}

const seedDir = mkdtempSync(join(tmpdir(), 'ap-store-seed-'))
try {
    writeFileSync(join(seedDir, 'package.json'), JSON.stringify({
        name: 'ap-offline-store-seed',
        version: '1.0.0',
        dependencies,
    }))
    execFileSync('pnpm', [
        'install',
        '--ignore-scripts',
        '--ignore-workspace',
        `--registry=${REGISTRY}`,
        '--config.node-linker=isolated',
        `--config.store-dir=${storeDir}`,
    ], { cwd: seedDir, stdio: 'inherit' })
}
finally {
    rmSync(seedDir, { recursive: true, force: true })
}
