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

# Remove piece source directories: nothing is imported at runtime anymore.
# HERMES-PATCH-012 摘掉了 api 对 slack / square / facebook-leads / intercom 的最后四个 import
# （连同 /v1/app-events 未鉴权端点），HERMES-PATCH-013 把 community 收敛到 5 个件——运行期的
# piece 一律由 hermes/prewarm-pieces.sh 从白名单以 npm 包形态烘进 cache，与源码树无关。
# 删完重生成 pnpm-lock.yaml，让 run 阶段的 `pnpm install --prod` 对得上裁剪后的工作区。
RUN rm -rf packages/pieces/core packages/pieces/custom packages/pieces/community && \
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

# HERMES: pre-install the allowlisted pieces (hermes/pieces.json) into the worker's
# piece cache, replicating piece-installer.ts byte-for-byte so the runtime install is a
# no-op and no registry is reached in an air-gapped cluster (X-3, FR-F03A). Registry
# access happens HERE, at build time, on a networked machine; in-house pieces install
# from hermes/tarballs/ and need no registry at all.
# Kept LAST so a whitelist edit only invalidates this layer, not the monorepo build.
COPY hermes/ ./hermes/
RUN sh /usr/src/app/hermes/prewarm-pieces.sh /usr/src/app/hermes/pieces.json

LABEL service=activepieces

ENTRYPOINT ["./docker-entrypoint.sh"]
EXPOSE 80
