#!/usr/bin/env node
// Mirrors every cdn.activepieces.com asset the product actually references into
// activepieces/packages/web/public/ap-cdn/, preserving the URL path.
//
// Why: production is air-gapped (X-2/X-3). Upstream hardcodes the CDN in ~47 places
// and stores it in piece metadata, so without a mirror the builder renders broken
// icons for every step. The Vite plugin `vite-plugins/ap-cdn-rewrite.js` rewrites the
// literal at build time; piece metadata is rewritten by generate-metadata-seed.js.
//
// Run from deploy/pieces/ while ONLINE:  node mirror-ap-cdn.mjs
import { readFileSync, existsSync, mkdirSync, writeFileSync, readdirSync, statSync } from 'fs'
import { dirname, join, resolve } from 'path'

const CDN = 'https://cdn.activepieces.com'
const ROOT = resolve(import.meta.dirname, '../..')
const OUT = join(ROOT, 'activepieces/packages/web/public/ap-cdn')
const SCAN = ['activepieces/packages/web/src', 'activepieces/packages/shared/src']
// Upstream-only surfaces we never expose (EE upsell videos, gamification badges).
const SKIP = [/\/videos\//, /\/badges\//]

function walk(dir, acc = []) {
    for (const entry of readdirSync(dir)) {
        const p = join(dir, entry)
        if (statSync(p).isDirectory()) walk(p, acc)
        else if (/\.(ts|tsx)$/.test(p)) acc.push(p)
    }
    return acc
}

const urls = new Set()
for (const rel of SCAN) {
    for (const file of walk(join(ROOT, rel))) {
        for (const m of readFileSync(file, 'utf8').matchAll(/https:\/\/cdn\.activepieces\.com\/[a-zA-Z0-9/_.-]+\.[a-z0-9]{2,4}/g)) {
            urls.add(m[0])
        }
    }
}
// Piece metadata drives the icons shown for every installed piece.
const metaDir = join(import.meta.dirname, 'metadata')
for (const f of readdirSync(metaDir).filter((f) => f.endsWith('.json'))) {
    const logoUrl = JSON.parse(readFileSync(join(metaDir, f), 'utf8')).logoUrl
    if (logoUrl?.startsWith(CDN)) urls.add(logoUrl)
}

const wanted = [...urls].filter((u) => !SKIP.some((re) => re.test(u))).sort()
console.log(`${wanted.length} assets to mirror (${urls.size - wanted.length} skipped)`)

let ok = 0
const failed = []
for (const url of wanted) {
    const rel = url.slice(CDN.length + 1)
    const dest = join(OUT, rel)
    if (existsSync(dest)) { ok++; continue }
    const res = await fetch(url)
    if (!res.ok) { failed.push(`${res.status} ${rel}`); continue }
    mkdirSync(dirname(dest), { recursive: true })
    writeFileSync(dest, Buffer.from(await res.arrayBuffer()))
    ok++
    console.log(`  + ${rel}`)
}
console.log(`mirrored ${ok}/${wanted.length} -> packages/web/public/ap-cdn/`)
if (failed.length) {
    console.log('\nUnavailable upstream (author these by hand at the same path):')
    for (const f of failed) console.log('  ! ' + f)
}
