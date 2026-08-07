# HERMES: overridable so a build machine that cannot reach Docker Hub can point at the
# internal mirror, e.g. --build-arg NODE_IMAGE=nexus3.hk.hsbc:18080/.../node:24.14.0-bullseye-slim
ARG NODE_IMAGE=node:24.14.0-bullseye-slim
FROM ${NODE_IMAGE} AS base

# Set environment variables early for better layer caching
ENV LANG=en_US.UTF-8 \
    LANGUAGE=en_US:en \
    LC_ALL=en_US.UTF-8

# Install all system dependencies in a single layer with cache mounts
RUN --mount=type=cache,target=/var/cache/apt,sharing=locked \
    --mount=type=cache,target=/var/lib/apt,sharing=locked \
    apt-get update && \
    apt-get install -y --no-install-recommends \
        openssh-client \
        python3 \
        g++ \
        build-essential \
        git \
        poppler-utils \
        poppler-data \
        procps \
        locales \
        unzip \
        curl \
        ca-certificates \
        iptables \
        libcap-dev && \
    yarn config set python /usr/bin/python3 && \
    sed -i '/en_US.UTF-8/s/^# //g' /etc/locale.gen && \
    locale-gen en_US.UTF-8

# HERMES: bun removed (X-4). Global npm packages incl. pnpm (replaces bun) in a single layer.
RUN --mount=type=cache,target=/root/.npm \
    npm install -g --no-fund --no-audit \
    node-gyp \
    npm@11.11.0 \
    pnpm@9.15.9 \
    pm2@6.0.10 \
    typescript@4.9.4 \
    esbuild@0.25.0

RUN pnpm --version

# Install isolated-vm standalone (needed for sandboxes); resolved at runtime via NODE_PATH
RUN --mount=type=cache,target=/root/.npm \
    cd /usr/src && npm install --no-fund --no-audit isolated-vm@npm:@luminati-io/isolated-vm@6.0.2-lum.2

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

# Generate migration manifest (ordered list of migration names) for image-tag-based rollback
RUN node -e "\
  const {getMigrations} = require('./packages/server/api/dist/src/app/database/postgres-connection');\
  const names = getMigrations().map(M => new M().name);\
  process.stdout.write(JSON.stringify(names));\
" > packages/server/api/dist/src/migration-manifest.json

### STAGE 2: Run ###
FROM base AS run

WORKDIR /usr/src/app

# Copy static configuration files first (better layer caching)
COPY --from=build /usr/src/app/packages/server/api/src/assets/default.cf /usr/local/etc/isolate
COPY docker-entrypoint.sh .

# Create all necessary directories in one layer
RUN mkdir -p \
    /usr/src/app/dist/packages/engine && \
    chmod +x docker-entrypoint.sh

# Copy root config files needed for dependency resolution
COPY --from=build /usr/src/app/package.json ./
COPY --from=build /usr/src/app/.npmrc ./
COPY --from=build /usr/src/app/pnpm-lock.yaml ./
COPY --from=build /usr/src/app/pnpm-workspace.yaml ./
# LICENSE 随 fd96c997c 从 vendor 树删除（AP 已 vendor-and-embed，不再作为独立发行物），
# 故这里不再拷贝——留着会让构建在 "LICENSE not found" 上直接失败。

# Copy workspace package.json files (needed for pnpm workspace resolution)
COPY --from=build /usr/src/app/packages ./packages

# Copy built engine
COPY --from=build /usr/src/app/dist/packages/engine/ ./dist/packages/engine/

# Install production dependencies from the committed lockfile. Both stages use
# --frozen-lockfile: the workspace is ours and its lockfile is a committed asset, so no build
# stage may resolve versions from the network (D13). This replaced a
# `rm -f pnpm-lock.yaml && pnpm install` that re-resolved the whole tree on every build —
# two builds on 2026-08-07 died in it, one on ECONNRESET and one on a full Docker disk.
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --prod --frozen-lockfile

# Copy frontend files
COPY --from=build /usr/src/app/dist/packages/web ./dist/packages/web/

# HERMES: the two hermes layers below both read hermes/pieces.json, so the copy sits
# right before them — editing the whitelist invalidates only these two layers, never the
# monorepo build above.
COPY hermes/ ./hermes/

# HERMES-PATCH(piece-admin P3): bake a pnpm offline store holding the exact dependency
# closure the in-house piece tarballs pin (see hermes/seed-offline-store.mjs for why the
# tarballs — not the vendored source tree — are the anchor). With
# AP_PIECES_OFFLINE_INSTALL=true the worker's pkg-runner resolves ARCHIVE piece installs
# from this store only — runtime stays fully offline (X-3), registry access happens here
# at build time only.
RUN node /usr/src/app/hermes/seed-offline-store.mjs \
      /usr/src/app/hermes \
      /usr/src/app/pnpm-offline-store
# The bake above also writes pnpm's offline METADATA cache under /root/.cache/pnpm/
# (keyed by registry hostname) — pkg-runner pins the same --registry at runtime so
# --offline resolution hits it. Both must stay registry.npmjs.org, in lockstep.

# HERMES: pre-install the allowlisted pieces (hermes/pieces.json) into the worker's
# piece cache, replicating piece-installer.ts byte-for-byte so the runtime install is a
# no-op and no registry is reached in an air-gapped cluster (X-3, FR-F03A). Registry
# access happens HERE, at build time, on a networked machine; in-house pieces install
# from hermes/tarballs/ and need no registry at all.
RUN sh /usr/src/app/hermes/prewarm-pieces.sh /usr/src/app/hermes/pieces.json

LABEL service=activepieces

ENTRYPOINT ["./docker-entrypoint.sh"]
EXPOSE 80
