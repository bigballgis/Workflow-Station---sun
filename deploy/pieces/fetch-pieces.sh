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

echo "Done. Now run: node generate-metadata-seed.js"
