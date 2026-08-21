#!/bin/sh
# Pre-installs the pieces from pieces.json into the AP sandbox's pnpm workspace
# (/usr/src/app/cache/v13/common) and writes the `ready` markers, replicating
# EXACTLY what packages/server/sandbox .../cache/pieces/piece-installer.ts does at
# runtime. Because pieceCheckIfAlreadyInstalled() sees ready + node_modules, the
# runtime installer becomes a NO-OP for these pieces — pnpm never runs, no registry
# and no bundle endpoint is hit (X-3, FR-A09/FR-A10).
#
# Runs INSIDE the AP image (last RUN step of ../Dockerfile) on a machine WITH network.
# The `v13` segment is AP 0.88's LATEST_CACHE_VERSION (see
# packages/server/sandbox/src/lib/cache/cache-paths.ts) — re-check it, and the
# installer layout below, whenever the installer code changes.
#
# 0.88 LAYOUT (differs from the 0.84 version of this script): the runtime installer no
# longer depends on registry version numbers. It downloads every piece — REGISTRY and
# ARCHIVE alike — as a tarball from the API's /v1/engine/pieces/bundle endpoint into
# `pieces/<name>-<ver>/bundle.tgz`, then writes a package.json whose dependency points
# at that tarball's ABSOLUTE path (see piece-installer.ts#createPiecePackageJson and
# #saveBundlesToDiskIfNotCached). We replicate that here: at build time the tarball for
# a registry piece comes straight from registry.npmjs.org (the same bytes the endpoint
# 307-redirects to), and for an in-house piece from ./tarballs/. Baking bundle.tgz —
# not just node_modules — matters: even if `ready` is ever lost and the runtime
# re-installs, saveBundlesToDiskIfNotCached() sees the tarball on disk and skips the
# network fetch entirely.
#
# pieces.json is the single piece allowlist: this script bakes the runtime half (npm
# packages, from the registry or from ./tarballs/ for in-house pieces) and
# deploy/pieces/generate-metadata-seed.js reads the same file to build the designer half
# (piece_metadata rows). The two must stay in lockstep.
set -eu
MANIFEST="${1:?usage: prewarm-pieces.sh /path/to/pieces.json}"
# Image default; overridable so the same script can prewarm a test cache outside the image.
WORKSPACE="${AP_PREWARM_WORKSPACE:-/usr/src/app/cache/v13/common}"

mkdir -p "$WORKSPACE/pieces"
cd "$WORKSPACE"

# Root workspace package.json — byte-for-byte what createRootPackageJson() writes.
cat > package.json <<'EOF'
{
  "name": "fast-workspace",
  "version": "1.0.0",
  "workspaces": [
    "pieces/**"
  ]
}
EOF

# pnpm does not read package.json `workspaces`, and the engine's piece loader resolves
# pieces/<name>-<ver>/node_modules/<name> — i.e. the isolated (per-member) layout.
# Byte-for-byte what createRootPackageJson() writes at runtime; a mismatch here makes
# the runtime re-install (or fail with PieceNotFound).
cat > pnpm-workspace.yaml <<'EOF'
packages:
  - "pieces/**"
EOF

cat > .npmrc <<'EOF'
node-linker=isolated
ignore-workspace-root-check=true
EOF

# One folder per piece: pieces/<name>-<version>/{bundle.tgz,package.json}, same as
# saveBundlesToDiskIfNotCached() + createPiecePackageJson(). A manifest entry with
# "tarball" is an IN-HOUSE piece: it is not on any public registry, so its bundle.tgz
# is copied from ./tarballs/ next to the manifest. Everything else is fetched from
# registry.npmjs.org — `https://registry.npmjs.org/<name>/-/<basename>-<ver>.tgz`,
# where <basename> is the package name without its scope. Either way the dependency
# value is the absolute bundle.tgz path, exactly as the runtime writes it.
FILTERS=$(node -e '
const fs = require("fs"), path = require("path")
const manifestPath = path.resolve(process.argv[1])
const pieces = require(manifestPath)
const tarballDir = path.join(path.dirname(manifestPath), "tarballs")

async function fetchTarball(url, dest) {
    let lastError
    for (let attempt = 1; attempt <= 5; attempt++) {
        try {
            const response = await fetch(url)
            if (!response.ok) {
                throw new Error(`${response.status} ${response.statusText}`)
            }
            fs.writeFileSync(dest, Buffer.from(await response.arrayBuffer()))
            return
        }
        catch (error) {
            lastError = error
            await new Promise(resolve => setTimeout(resolve, attempt * 2000))
        }
    }
    throw new Error(`FATAL: failed to download ${url}: ${lastError}`)
}

async function main() {
    const filters = []
    for (const p of pieces) {
        const dir = path.join("pieces", `${p.name}-${p.version}`)
        fs.mkdirSync(dir, { recursive: true })
        const bundlePath = path.resolve(dir, "bundle.tgz")
        if (p.tarball) {
            const src = path.join(tarballDir, p.tarball)
            if (!fs.existsSync(src)) {
                console.error(`FATAL: ${p.name}@${p.version} declares tarball ${p.tarball}, not found in ${tarballDir}`)
                process.exit(1)
            }
            fs.copyFileSync(src, bundlePath)
        }
        else {
            const basename = p.name.startsWith("@") ? p.name.split("/")[1] : p.name
            const url = `https://registry.npmjs.org/${p.name}/-/${basename}-${p.version}.tgz`
            await fetchTarball(url, bundlePath)
        }
        // Byte-for-byte what createPiecePackageJson() writes (dependency = absolute
        // bundle.tgz path, no file: prefix — pnpm resolves a bare .tgz path as a
        // local tarball, proven by the 0.84 ARCHIVE branch in production).
        fs.writeFileSync(path.join(dir, "package.json"), JSON.stringify({
            name: `${p.name}-${p.version}`,
            version: p.version,
            dependencies: { [p.name]: bundlePath },
        }, null, 2))
        filters.push("--filter", `./${dir}`)
    }
    process.stdout.write(filters.join(" "))
}

main().catch((error) => { console.error(error); process.exit(1) })
' "$MANIFEST")

# Same command pkgRunner().install() spawns (FR-A02: no bun anywhere). No offline args:
# THIS is the build-time step that is allowed to reach the registry.
# shellcheck disable=SC2086
pnpm install --ignore-scripts --config.node-linker=isolated --config.confirmModulesPurge=false $FILTERS

# Verify layout matches what the runtime skip-check needs, then mark ready.
node -e '
const fs = require("fs"), path = require("path")
const pieces = require(process.argv[1])
for (const p of pieces) {
    const dir = path.join("pieces", `${p.name}-${p.version}`)
    if (!fs.existsSync(path.join(dir, "node_modules", p.name))) {
        console.error(`FATAL: ${dir}/node_modules/${p.name} missing — runtime would re-install (or PieceNotFound)`)
        process.exit(1)
    }
    fs.writeFileSync(path.join(dir, "ready"), "true")
    console.log(`prewarmed ${p.name}@${p.version}`)
}
' "$MANIFEST"
