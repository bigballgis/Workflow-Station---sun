FROM node:24.14.0-bullseye-slim AS base

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

# Generate migration manifest (ordered list of migration names) for image-tag-based rollback
RUN node -e "\
  const {getMigrations} = require('./packages/server/api/dist/src/app/database/postgres-connection');\
  const names = getMigrations().map(M => new M().name);\
  process.stdout.write(JSON.stringify(names));\
" > packages/server/api/dist/src/migration-manifest.json

# Remove piece directories not needed at runtime (keeps only the 4 pieces api imports)
# Then regenerate pnpm-lock.yaml so it matches the trimmed workspace
RUN rm -rf packages/pieces/core packages/pieces/custom && \
    find packages/pieces/community -mindepth 1 -maxdepth 1 -type d \
      ! -name slack \
      ! -name square \
      ! -name facebook-leads \
      ! -name intercom \
      -exec rm -rf {} + && \
    rm -f pnpm-lock.yaml && pnpm install

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
COPY --from=build /usr/src/app/LICENSE .

# Copy workspace package.json files (needed for pnpm workspace resolution)
COPY --from=build /usr/src/app/packages ./packages

# Copy built engine
COPY --from=build /usr/src/app/dist/packages/engine/ ./dist/packages/engine/

# Install production dependencies (pieces were trimmed; lockfile regenerated in build stage)
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --prod

# HERMES-PATCH(piece-admin P3): bake a pnpm offline store holding the exact
# dependency closure that build-piece pins into every self-developed piece tarball
# (@activepieces/pieces-framework / pieces-common / shared + tslib, versions read
# from the vendored tree so they can never drift). With AP_PIECES_OFFLINE_INSTALL=true
# the worker's pkg-runner resolves ARCHIVE piece installs from this store only —
# runtime stays fully offline (X-3), registry access happens here at build time only.
RUN set -e && \
    FW=$(node -p "require('/usr/src/app/packages/pieces/framework/package.json').version") && \
    CM=$(node -p "require('/usr/src/app/packages/pieces/common/package.json').version") && \
    SH=$(node -p "require('/usr/src/app/packages/shared/package.json').version") && \
    mkdir -p /tmp/ap-store-seed && cd /tmp/ap-store-seed && \
    printf '{"name":"ap-offline-store-seed","version":"1.0.0","dependencies":{"@activepieces/pieces-framework":"%s","@activepieces/pieces-common":"%s","@activepieces/shared":"%s","tslib":"2.6.2"}}' "$FW" "$CM" "$SH" > package.json && \
    pnpm install --ignore-scripts --ignore-workspace \
      --registry=https://registry.npmjs.org/ \
      --config.node-linker=isolated \
      --config.store-dir=/usr/src/app/pnpm-offline-store && \
    cd / && rm -rf /tmp/ap-store-seed
# The bake above also writes pnpm's offline METADATA cache under /root/.cache/pnpm/
# (keyed by registry hostname) — pkg-runner pins the same --registry at runtime so
# --offline resolution hits it. Both must stay registry.npmjs.org, in lockstep.

# Copy frontend files
COPY --from=build /usr/src/app/dist/packages/web ./dist/packages/web/

LABEL service=activepieces

ENTRYPOINT ["./docker-entrypoint.sh"]
EXPOSE 80
