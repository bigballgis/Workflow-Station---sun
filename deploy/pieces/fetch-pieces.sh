#!/bin/sh
# Downloads the pieces listed in pieces.json onto an INTERNET-CONNECTED machine
# (dev laptop). Produces:
#   tarballs/<pkg>-<version>.tgz   — npm package (audit trail / Nexus publish source)
#   metadata/<short-name>.json     — full designer metadata from the AP cloud API,
#                                    pinned to the version in pieces.json
# Run from deploy/pieces/:  sh fetch-pieces.sh
# Then regenerate the DB seed:  node generate-metadata-seed.js
set -eu
cd "$(dirname "$0")"
mkdir -p tarballs metadata

node -e '
const pieces = require("./pieces.json");
for (const p of pieces) console.log(p.name + " " + p.version + " " + p.name.split("/")[1]);
' | while read -r name version short; do
    echo "==> $name@$version"
    (cd tarballs && npm pack "$name@$version" --silent)
    curl -sf "https://cloud.activepieces.com/api/v1/pieces/$name?version=$version" -o "metadata/$short.json"
done

# Piece icons must be served from our own origin — production is air-gapped and
# cdn.activepieces.com is unreachable there (every step icon would render broken).
# In-house pieces are skipped: their upstream URLs 404 even online, so their icons
# are authored by hand and live in the same directory.
ICON_DIR=../../activepieces/packages/web/public/piece-icons
mkdir -p "$ICON_DIR"
node -e '
const fs = require("fs"), path = require("path");
const pieces = require("./pieces.json");
for (const p of pieces) {
    const short = p.name.split("/")[1];
    const file = path.join("metadata", short + ".json");
    if (!fs.existsSync(file)) continue;
    const logoUrl = JSON.parse(fs.readFileSync(file, "utf8")).logoUrl;
    if (logoUrl && logoUrl.startsWith("http")) console.log(logoUrl);
}
' | sort -u | while read -r url; do
    icon=$(basename "$url")
    if curl -sf "$url" -o "$ICON_DIR/$icon"; then
        echo "==> icon $icon"
    else
        rm -f "$ICON_DIR/$icon"
        echo "!!! icon $icon unavailable ($url) — author it by hand in $ICON_DIR and map it in generate-metadata-seed.js"
    fi
done

echo "Done. Now run: node generate-metadata-seed.js"
