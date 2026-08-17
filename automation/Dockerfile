# HERMES: overridable so a build machine that cannot reach Docker Hub can point at the
# internal mirror, e.g. --build-arg NODE_IMAGE=nexus3.hk.hsbc:18080/.../node:24.14.0-bullseye-slim
ARG NODE_IMAGE=node:24.14.0-bullseye-slim
FROM ${NODE_IMAGE} AS base

# C.UTF-8 ships with Debian, so no locale generation is needed.
# REDISMS_VERSION pins the Redis that redis-memory-server (AP_REDIS_TYPE=MEMORY)
# compiles at image build and looks up at runtime — the default "stable" drifted
# to Redis 8, whose in-tree modules need cmake/pkg-config and break the build;
# Redis 7.x compiles with gcc/make alone. Keep this pin in the base stage so the
# runtime binary-cache key matches the one baked at build.
ENV LANG=C.UTF-8 \
    LC_ALL=C.UTF-8 \
    REDISMS_VERSION=7.4.2

# Install all system dependencies in a single layer. No apt cache mounts: docker-clean in the
# node base image wipes /var/cache/apt anyway, and a persisted /var/lib/apt/lists goes stale
# against rotated bullseye-security packages, failing the build with hash/size fetch errors.
# libcap2 is isolate's runtime lib (the isolate binaries ship prebuilt in api assets).
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        openssh-client \
        python3 \
        g++ \
        build-essential \
        git \
        poppler-utils \
        poppler-data \
        procps \
        unzip \
        curl \
        ca-certificates \
        iptables \
        libcap2 && \
    rm -rf /var/lib/apt/lists/*

# HERMES: bun removed (FR-A02/FR-A08, D-13) — upstream downloaded the bun binary here.
# pnpm replaces it for every install the image performs: monorepo build/prod install below,
# the offline-store bake and piece prewarm at the end of the run stage, and the runtime
# piece/code-step installs spawned by packages/server/sandbox pkg-runner.ts.
RUN --mount=type=cache,target=/root/.npm \
    npm install -g --no-fund --no-audit \
    node-gyp \
    npm@11.11.0 \
    pnpm@9.15.9 \
    pm2@6.0.10 \
    esbuild@0.25.0

RUN pnpm --version

# Install isolated-vm standalone (needed for sandboxes); resolved at runtime via NODE_PATH
RUN --mount=type=cache,target=/root/.npm \
    cd /usr/src && npm install --no-fund --no-audit isolated-vm@6.0.2

### STAGE 1: Build ###
FROM base AS build

WORKDIR /usr/src/app

# Copy dependency files and workspace package.json files for resolution
COPY .npmrc package.json pnpm-lock.yaml pnpm-workspace.yaml ./
COPY packages/ ./packages/

# Install all dependencies with frozen lockfile
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile

# Copy remaining source code (turbo config, etc.)
COPY . .

# Build frontend, engine, server API, and worker
RUN pnpm exec turbo run build --filter=web --filter=@activepieces/engine --filter=api --filter=worker

# The web build emits hidden source maps (vite build.sourcemap='hidden') used to
# symbolicate production stack traces. Always remove the .map files so source is
# never served from the shipped image.
RUN find dist/packages/web -name '*.map' -delete

# Generate migration manifest (ordered list of migration names) for image-tag-based rollback
RUN node -e "\
  const {getMigrations} = require('./packages/server/api/dist/src/app/database/postgres-connection');\
  const names = getMigrations().map(M => new M().name);\
  process.stdout.write(JSON.stringify(names));\
" > packages/server/api/dist/src/migration-manifest.json

# HERMES: upstream's build-time "pieces trim" step (rm -rf pieces/core|custom|web|cli|... +
# lockfile regeneration) is gone: the source tree is physically trimmed already (D-13), the
# committed pnpm-lock.yaml matches it, and no build stage may re-resolve versions from the
# network — see the --frozen-lockfile note in the run stage.

### STAGE 2: Run ###
FROM base AS run

WORKDIR /usr/src/app

# Copy static configuration files first (better layer caching)
COPY --from=build /usr/src/app/packages/server/api/src/assets/default.cf /usr/local/etc/isolate
COPY docker-entrypoint.sh .

# Copy root config files needed for dependency resolution
COPY --from=build /usr/src/app/package.json ./
COPY --from=build /usr/src/app/.npmrc ./
COPY --from=build /usr/src/app/pnpm-lock.yaml ./
COPY --from=build /usr/src/app/pnpm-workspace.yaml ./
COPY --from=build /usr/src/app/LICENSE .

# Copy workspace package.json files (needed for pnpm workspace resolution)
COPY --from=build /usr/src/app/packages ./packages

# Copy built engine
COPY --from=build /usr/src/app/dist/packages/engine/ ./dist/packages/engine/

# Install production dependencies from the committed lockfile. Both stages use
# --frozen-lockfile: the workspace is ours and its lockfile is a committed asset, so no build
# stage may resolve versions from the network (D-13). Upstream instead regenerated the
# lockfile here after a build-time pieces trim; with the tree trimmed at source there is
# nothing to regenerate — and a 0.84-era `rm lockfile && pnpm install` variant killed two
# builds in one day (ECONNRESET, full Docker disk) before being retired.
#
# HERMES: --filter restricts the install to the three processes this image actually runs
# (api, worker, engine) plus their transitive workspace deps; `<pkg>...` is pnpm's
# "package and its dependencies" selector. WITHOUT it, the COPY of ./packages above hands
# pnpm the whole workspace and --prod dutifully installs packages/web's browser stack
# (lucide-react, posthog-js, shiki, core-js, date-fns…) into an image that never runs vite —
# the web app ships here as the prebuilt static dist/packages/web copied below, which needs
# no node_modules at all. That accounted for ~half of a 983MB node_modules.
# Upstream got the same effect by DELETING the build-only workspace members (rm -rf
# packages/web packages/cli …) and regenerating the lockfile; this fork keeps the tree and
# the lockfile intact and filters at install time instead, so --frozen-lockfile still
# validates against the committed lockfile. Do NOT try to achieve this by trimming
# pnpm-workspace.yaml — that changes the member set the lockfile was built from and
# --frozen-lockfile fails.
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --prod --frozen-lockfile \
      --filter=api... \
      --filter=worker... \
      --filter=@activepieces/engine...

# Copy frontend files
COPY --from=build /usr/src/app/dist/packages/web ./dist/packages/web/

# HERMES: the two hermes layers below both read hermes/pieces.json, so the copy sits
# right before them — editing the whitelist invalidates only these two layers, never the
# monorepo build above.
COPY hermes/ ./hermes/

# HERMES-PATCH-005 (piece-admin P3): bake a pnpm offline store holding the exact dependency
# closure the in-house piece tarballs pin (see hermes/seed-offline-store.mjs for why the
# tarballs — not the vendored source tree — are the anchor). With
# AP_PIECES_OFFLINE_INSTALL=true the sandbox's pkg-runner resolves ARCHIVE piece installs
# from this store only — runtime stays fully offline (X-3, FR-A09), registry access
# happens here at build time only.
RUN node /usr/src/app/hermes/seed-offline-store.mjs \
      /usr/src/app/hermes \
      /usr/src/app/pnpm-offline-store
# The bake above also writes pnpm's offline METADATA cache under /root/.cache/pnpm/
# (keyed by registry hostname) — pkg-runner pins the same --registry at runtime so
# --offline resolution hits it. Both must stay registry.npmjs.org, in lockstep.

# HERMES: pre-install the allowlisted pieces (hermes/pieces.json) into the sandbox's
# piece cache (cache/v13/common), replicating piece-installer.ts byte-for-byte — incl.
# each piece's bundle.tgz, so the runtime install AND the bundle-endpoint fetch are
# no-ops and no registry is reached in an air-gapped cluster (X-3, FR-A10). Registry
# access happens HERE, at build time, on a networked machine; in-house pieces install
# from hermes/tarballs/ and need no registry at all.
RUN sh /usr/src/app/hermes/prewarm-pieces.sh /usr/src/app/hermes/pieces.json

ENV NODE_ENV=production

LABEL service=activepieces

# WORKER containers have no HTTP server; treat them as healthy (probe only the app).
# (D-12 runs a single WORKER_AND_APP container today, but AP_CONTAINER_TYPE must keep
# working — FR-G04 — so the guard stays.)
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=5 \
    CMD [ "$AP_CONTAINER_TYPE" = "WORKER" ] && exit 0 || curl -fsS "http://localhost:${AP_PORT:-80}/api/v1/health" || exit 1

ENTRYPOINT ["./docker-entrypoint.sh"]
EXPOSE 80
