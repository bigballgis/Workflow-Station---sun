# Activepieces image with the approved pieces PRE-INSTALLED (offline / IKP policy).
# The pnpm install happens HERE, at build time on a networked machine; at runtime the
# worker's piece-installer sees the ready markers and never touches any registry.
#
# Build (from deploy/pieces/):
#   docker build -t <registry>/workflow-station2/activepieces:0.84.0-pieces-<n> .
# For the cluster, override BASE_IMAGE with the nexus3 mirror.
# HERMES vendored, EE-removed, de-bunned image (see activepieces/Dockerfile) — the
# prewarm layout must match that image's runtime piece installer (pnpm, isolated linker).
ARG BASE_IMAGE=activepieces:0.84.0-ee-removed
FROM ${BASE_IMAGE}

COPY pieces.json /tmp/pieces.json
COPY prewarm-pieces.sh /tmp/prewarm-pieces.sh
RUN sh /tmp/prewarm-pieces.sh /tmp/pieces.json && rm /tmp/prewarm-pieces.sh

# The designer's "Approvals" tab is hardwired to six SaaS pieces (Slack/Teams/Gmail/...)
# that are outside the allowlist — it would spin forever and spam 404s. Patch it out.
COPY patch-web-approvals.js /tmp/patch-web-approvals.js
RUN node /tmp/patch-web-approvals.js && rm /tmp/patch-web-approvals.js

# run_agent + DeepSeek reasoning models: set an explicit maxOutputTokens (the provider
# default is small enough that chain-of-thought exhausts it before the real answer) and
# keep reasoning deltas out of the markdown output (they polluted the chat reply).
COPY patch-piece-ai-run-agent.js /tmp/patch-piece-ai-run-agent.js
RUN node /tmp/patch-piece-ai-run-agent.js && rm /tmp/patch-piece-ai-run-agent.js
