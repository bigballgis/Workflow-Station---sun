#!/bin/sh
# Pre-installs the pieces from pieces.json into the AP worker's bun workspace
# (/usr/src/app/cache/v11/common) and writes the `ready` markers, replicating
# EXACTLY what packages/server/worker .../pieces/piece-installer.ts does at runtime.
# Because pieceCheckIfAlreadyInstalled() sees ready + node_modules, the runtime
# installer becomes a NO-OP for these pieces — bun never runs, no registry is hit.
#
# Runs INSIDE the AP image (docker build RUN step) on a machine WITH network.
# The `v11` segment is AP 0.84's LATEST_CACHE_VERSION (cache-paths.ts) — re-check
# it when upgrading the AP image.
set -eu
MANIFEST="${1:?usage: prewarm-pieces.sh /path/to/pieces.json}"
WORKSPACE="/usr/src/app/cache/v11/common"

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

# One folder per piece: pieces/<name>-<version>/package.json, same as
# createPiecePackageJson() (REGISTRY variant).
FILTERS=$(node -e '
const fs = require("fs"), path = require("path")
const pieces = require(process.argv[1])
const filters = []
for (const p of pieces) {
    const dir = path.join("pieces", `${p.name}-${p.version}`)
    fs.mkdirSync(dir, { recursive: true })
    fs.writeFileSync(path.join(dir, "package.json"), JSON.stringify({
        name: `${p.name}-${p.version}`,
        version: p.version,
        dependencies: { [p.name]: p.version },
    }, null, 2))
    filters.push("--filter", `./${dir}`)
}
process.stdout.write(filters.join(" "))
' "$MANIFEST")

# Same command bunRunner().install() spawns.
# shellcheck disable=SC2086
bun install --ignore-scripts $FILTERS

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
