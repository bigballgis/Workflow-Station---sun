#!/bin/sh
# Pre-installs the pieces from pieces.json into the AP worker's pnpm workspace
# (/usr/src/app/cache/v11/common) and writes the `ready` markers, replicating
# EXACTLY what packages/server/worker .../pieces/piece-installer.ts does at runtime.
# Because pieceCheckIfAlreadyInstalled() sees ready + node_modules, the runtime
# installer becomes a NO-OP for these pieces — pnpm never runs, no registry is hit.
#
# Runs INSIDE the AP image (last RUN step of ../Dockerfile) on a machine WITH network.
# The `v11` segment is AP 0.84's LATEST_CACHE_VERSION (see
# packages/server/worker/src/lib/cache/cache-paths.ts) — re-check it, and the
# installer layout below, whenever the vendored tree moves to a new upstream tag.
#
# pieces.json is the single piece allowlist: this script bakes the runtime half (npm
# packages, from the registry or from ./tarballs/ for in-house pieces) and
# deploy/pieces/generate-metadata-seed.js reads the same file to build the designer half
# (piece_metadata rows). The two must stay in lockstep.
set -eu
MANIFEST="${1:?usage: prewarm-pieces.sh /path/to/pieces.json}"
# Image default; overridable so the same script can prewarm a test cache outside the image
# (execute-flow-e2e pins older piece versions — see hermes/pieces.e2e-fixtures.json).
WORKSPACE="${AP_PREWARM_WORKSPACE:-/usr/src/app/cache/v11/common}"

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

# One folder per piece: pieces/<name>-<version>/package.json, same as
# createPiecePackageJson(). A manifest entry with "tarball" is an IN-HOUSE piece: it is
# not on any public registry, so the dependency points at the archived .tgz next to the
# manifest — structurally the same as createPiecePackageJson()'s ARCHIVE branch, which
# also depends on a .tgz sitting inside the piece folder. Everything else resolves from
# the registry by version (REGISTRY branch).
FILTERS=$(node -e '
const fs = require("fs"), path = require("path")
const manifestPath = path.resolve(process.argv[1])
const pieces = require(manifestPath)
const tarballDir = path.join(path.dirname(manifestPath), "tarballs")
const filters = []
for (const p of pieces) {
    const dir = path.join("pieces", `${p.name}-${p.version}`)
    fs.mkdirSync(dir, { recursive: true })
    let dependency = p.version
    if (p.tarball) {
        const src = path.join(tarballDir, p.tarball)
        if (!fs.existsSync(src)) {
            console.error(`FATAL: ${p.name}@${p.version} declares tarball ${p.tarball}, not found in ${tarballDir}`)
            process.exit(1)
        }
        // Copy in, so the dependency path stays inside the piece folder like ARCHIVE does.
        const dest = path.join(dir, p.tarball)
        fs.copyFileSync(src, dest)
        dependency = path.resolve(dest)
    }
    fs.writeFileSync(path.join(dir, "package.json"), JSON.stringify({
        name: `${p.name}-${p.version}`,
        version: p.version,
        dependencies: { [p.name]: dependency },
    }, null, 2))
    filters.push("--filter", `./${dir}`)
}
process.stdout.write(filters.join(" "))
' "$MANIFEST")

# Same command pkgRunner().install() spawns (X-4: no bun anywhere).
# shellcheck disable=SC2086
pnpm install --ignore-scripts --config.node-linker=isolated --config.confirmModulesPurge=false $FILTERS

# Verify layout matches what the runtime skip-check needs, then mark ready.
node -e '
const fs = require("fs"), path = require("path")
const pieces = require(process.argv[1])
for (const p of pieces) {
    const dir = path.join("pieces", `${p.name}-${p.version}`)
    if (!fs.existsSync(path.join(dir, "node_modules"))) {
        console.error(`FATAL: ${dir}/node_modules missing — runtime would re-install`)
        process.exit(1)
    }
    fs.writeFileSync(path.join(dir, "ready"), "true")
    console.log(`prewarmed ${p.name}@${p.version}`)
}
' "$MANIFEST"
