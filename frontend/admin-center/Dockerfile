# Build stage
FROM node:22-alpine AS builder

WORKDIR /app

# pnpm via corepack — the exact version comes from package.json's "packageManager".
ENV COREPACK_ENABLE_DOWNLOAD_PROMPT=0
RUN corepack enable

# Copy manifest + lockfile first for better caching
COPY package.json pnpm-lock.yaml ./

# Install dependencies with a shared pnpm store cache
RUN --mount=type=cache,target=/root/.local/share/pnpm/store \
    pnpm install --frozen-lockfile

# Copy source code
COPY . .

# Build the application (skip type check for Docker)
RUN pnpm run build

# Production stage
FROM nginx:alpine

# Install gettext for envsubst
RUN apk add --no-cache gettext

# Copy built assets
COPY --from=builder /app/dist /usr/share/nginx/html

# Copy nginx configuration as template
COPY nginx.conf /etc/nginx/conf.d/default.conf.template

# Copy entrypoint script
COPY docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# Set default backend URL
ENV ADMIN_CENTER_URL=http://admin-center:8080

EXPOSE 80

ENTRYPOINT ["/docker-entrypoint.sh"]
