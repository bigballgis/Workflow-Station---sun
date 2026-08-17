/**
 * Asserts that every `compilerOptions.paths` entry in our tsconfigs points at a file that
 * exists. Exit 1 with the offenders listed, so CI catches it.
 *
 * This is what survived `trim-vendor-pieces.mjs` (deleted 2026-08-07, D13). That script did
 * two unrelated jobs and only one of them still had a reason to exist:
 *
 *   - PERFORM the piece trim, in replayable form with a `--check` mode, so a future rebase
 *     onto a new upstream tag could re-apply it. D12 killed rebase, D13 killed the last of
 *     the ceremony: the trimmed tree is simply our tree now, and `packages/pieces/core`
 *     is gone from it outright.
 *   - ASSERT that no tsconfig path maps to a directory that no longer exists. That has
 *     nothing to do with upstream — a dangling mapping resolves imports to a file that
 *     isn't there, and tsc reports it far from the cause. It also caught a real backlog:
 *     34 dangling piece mappings were still in tsconfig.base.json on 2026-08-07 (VT-16).
 *
 * The old script's other invariant — "packages/pieces/community/ contains exactly the
 * allowlist" — is deliberately NOT reproduced: community/ is where our own pieces live, so
 * that check taxed the very workflow it was supposed to protect (every new in-house piece
 * had to be registered in the script's KEEP map first).
 */
import fs from 'fs'
import path from 'path'
import { fileURLToPath } from 'url'

const TSCONFIGS = [
    'tsconfig.base.json',
    'packages/web/tsconfig.app.json',
    'packages/web/tsconfig.spec.json',
]

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

const dangling = TSCONFIGS.flatMap((relPath) => {
    const abs = path.join(root, relPath)
    if (!fs.existsSync(abs)) {
        return []
    }
    const config = readJson(abs)
    const paths = config?.compilerOptions?.paths ?? {}
    // tsconfig resolves `paths` against baseUrl, which is itself relative to the file.
    const base = path.resolve(path.dirname(abs), config?.compilerOptions?.baseUrl ?? '.')
    return Object.entries(paths).flatMap(([alias, targets]) => (Array.isArray(targets) ? targets : [])
        .filter((target) => !targetExists({ base, target }))
        .map((target) => ({ relPath, alias, target })))
})

if (dangling.length > 0) {
    console.error(`✗ ${dangling.length} tsconfig path mapping(s) point at files that do not exist:\n`)
    for (const { relPath, alias, target } of dangling) {
        console.error(`  ${relPath}: "${alias}" -> ${target}`)
    }
    console.error('\nDelete the mapping, or restore the file it refers to.')
    process.exit(1)
}

console.log(`✓ all tsconfig path mappings resolve (${TSCONFIGS.join(', ')})`)

// A `*` target ("packages/pieces/community/*") can only be checked as far as its fixed prefix; anything
// past the wildcard is per-import and not knowable here.
function targetExists({ base, target }) {
    const wildcard = target.indexOf('*')
    const checkable = wildcard === -1 ? target : path.dirname(target.slice(0, wildcard))
    return fs.existsSync(path.resolve(base, checkable))
}

function readJson(abs) {
    try {
        return JSON.parse(fs.readFileSync(abs, 'utf-8'))
    }
    catch (error) {
        console.error(`✗ ${abs} is not readable JSON: ${error.message}`)
        process.exit(1)
    }
}
