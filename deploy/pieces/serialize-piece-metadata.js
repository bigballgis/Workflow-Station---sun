#!/usr/bin/env node
// Serializes a SELF-DEVELOPED piece's designer-half metadata from its build output,
// writing metadata/piece-<name>.json in the exact shape generate-metadata-seed.js expects.
//
// Why this exists: fetch-pieces.sh curls cloud.activepieces.com (self-developed pieces
// don't exist there), and asking the local AP for a NEW piece is a chicken-and-egg —
// with AP_PIECES_SYNC_MODE=NONE the catalog is DB-only, so an unseeded piece 404s.
// Instead we load the compiled piece the same way the engine does (dist/src/index.js,
// find the Piece export, call .metadata()) and serialize locally. Zero network.
//
// Usage:  node serialize-piece-metadata.js <piece-folder> [...more]
//   e.g.  node serialize-piece-metadata.js biz-calendar
// Prereq: npm run build-piece -- <piece-folder>  (in activepieces/) has been run,
//         so packages/pieces/*/<piece-folder>/dist/ exists with a pinned package.json.
'use strict'
const fs = require('fs')
const path = require('path')

const repoRoot = path.resolve(__dirname, '..', '..')
const piecesRoot = path.join(repoRoot, 'activepieces', 'packages', 'pieces')
const metadataDir = path.join(__dirname, 'metadata')

// Self-developed pieces live in community/ (see HOWTO §1.1); custom/ and core/ are
// searched as fallbacks so the script keeps working if conventions shift.
const SEARCH_DIRS = ['community', 'custom', 'core']

function findPieceDist(folderName) {
    for (const sub of SEARCH_DIRS) {
        const dist = path.join(piecesRoot, sub, folderName, 'dist')
        if (fs.existsSync(path.join(dist, 'package.json'))) return dist
    }
    throw new Error(
        `no built dist for piece folder "${folderName}" under ${SEARCH_DIRS.join('/')}. ` +
        'Run: cd activepieces && npm run build-piece -- ' + folderName,
    )
}

function serializeOne(folderName) {
    const dist = findPieceDist(folderName)
    // dist/package.json has workspace deps pinned to real versions by build-piece.
    const pkg = JSON.parse(fs.readFileSync(path.join(dist, 'package.json'), 'utf8'))

    // Engine-equivalent load: dist/src/index.js, pick the export that is a Piece
    // (has a .metadata() method). dist/node_modules is symlinked by build-piece,
    // so framework imports resolve.
    const mod = require(path.join(dist, 'src', 'index.js'))
    const piece = Object.values(mod).find((v) => v && typeof v.metadata === 'function')
    if (!piece) throw new Error(`${folderName}: no Piece export found in dist/src/index.js`)
    const m = piece.metadata()

    const out = {
        name: pkg.name,
        version: pkg.version,
        displayName: m.displayName,
        logoUrl: m.logoUrl,
        description: m.description ?? '',
        minimumSupportedRelease: m.minimumSupportedRelease,
        maximumSupportedRelease: m.maximumSupportedRelease,
        auth: m.auth ?? null,
        actions: m.actions ?? {},
        triggers: m.triggers ?? {},
        categories: m.categories ?? [],
        authors: m.authors ?? [],
        i18n: m.i18n ?? null,
    }

    // generate-metadata-seed.js locates the file by the npm package's short name
    // (name.split('/')[1]) — i.e. piece-<name>.json, NOT <name>.json.
    const short = pkg.name.split('/')[1]
    const outPath = path.join(metadataDir, `${short}.json`)
    fs.writeFileSync(outPath, JSON.stringify(out, null, 2) + '\n')
    console.log(`wrote ${path.relative(process.cwd(), outPath)} (${pkg.name}@${pkg.version}, ` +
        `${Object.keys(out.actions).length} actions, ${Object.keys(out.triggers).length} triggers)`)
    console.log(`  activepieces/hermes/pieces.json entry: { "name": "${pkg.name}", "version": "${pkg.version}" }`)
}

const folders = process.argv.slice(2)
if (folders.length === 0) {
    console.error('usage: node serialize-piece-metadata.js <piece-folder> [...more]')
    process.exit(1)
}
for (const f of folders) serializeOne(f)
