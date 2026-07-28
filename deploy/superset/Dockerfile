# syntax=docker/dockerfile:1.6
FROM apache/superset:6.0.0

USER root

# Install pinned psycopg2-binary from the index in the BuildKit-mounted pip.conf
# (enterprise Nexus or public PyPI). Credentials stay in the secret mount — they
# never enter Dockerfile ARG/ENV, image layers, or `docker history`.
RUN --mount=type=secret,id=pip_conf,dst=/etc/pip.conf,required=true \
    /app/.venv/bin/python -m ensurepip --upgrade && \
    /app/.venv/bin/python -m pip install --no-cache-dir \
    psycopg2-binary==2.9.10

COPY superset_config.py /app/pythonpath/superset_config.py
COPY superset_security_manager.py /app/pythonpath/superset_security_manager.py

# SUPERSET_SECRET_KEY / SUPERSET_DATABASE_URI are intentionally NOT baked in —
# inject at runtime (compose .env / k8s Secret). Fail closed if missing.
ENV SUPERSET_CONFIG_PATH=/app/pythonpath/superset_config.py
ENV SUPERSET_SECRET_PATH=/app/pythonpath/superset_config.py

USER superset

# Build (credentials via BuildKit secret, not build-args):
#   DOCKER_BUILDKIT=1 docker build \
#     --secret id=pip_conf,src=/path/to/pip.conf \
#     -t dev-superset .
# Prefer: deploy/environments/dev/build-and-deploy.ps1
#
# First-time empty Postgres (schema `superset` must exist; metadata stays out of public):
#   docker exec -it platform-superset-dev superset db upgrade
#   docker exec -it platform-superset-dev superset fab create-admin ...
#   docker exec -it platform-superset-dev superset init
