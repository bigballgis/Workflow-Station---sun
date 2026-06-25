FROM apache/superset:6.0.0

USER root

# 1. Install system dependencies for Postgres and MySQL
RUN /app/.venv/bin/python -m ensurepip --upgrade && \
    /app/.venv/bin/python -m pip install --no-cache-dir \
    psycopg2-binary

COPY superset_config.py /app/pythonpath/superset_config.py
COPY superset_security_manager.py /app/pythonpath/superset_security_manager.py

# 4. Load custom superset_config.py (SUPERSET_SECRET_PATH does NOT load config).
#    NOTE: SUPERSET_SECRET_KEY is intentionally NOT baked into the image — it must be
#    injected at runtime (compose .env / k8s Secret). Baking a default weak secret here
#    would let anyone forge guest tokens.
ENV SUPERSET_CONFIG_PATH=/app/pythonpath/superset_config.py

ENV SUPERSET_SECRET_PATH=/app/pythonpath/superset_config.py

USER superset

# docker pull apache/superset:6.0.0
# docker build -t my-superset .
# docker run -d -p 8088:8088 -e SUPERSET_SECRET_KEY=your-real-secret --name superset-final my-superset
#
# First-time empty Postgres (schema `superset` must exist; metadata stays out of public):
# docker exec -it superset-final superset fab create-db
# docker exec -it superset-final superset db upgrade
# docker exec -it superset-final superset fab create-admin --username admin --firstname admin --lastname Admin --email admin@fab.org --password admin123
# docker exec -it superset-final superset init
# docker exec -it superset-final superset load_examples