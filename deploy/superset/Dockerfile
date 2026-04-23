FROM apache/superset:latest

USER root

# 1. Install system dependencies for Postgres and MySQL
RUN /app/.venv/bin/python -m ensurepip --upgrade && \
    /app/.venv/bin/python -m pip install --no-cache-dir \
    psycopg2-binary

COPY superset_config.py /app/pythonpath/superset_config.py

# 4. Secret key + load custom superset_config.py (SUPERSET_SECRET_PATH does NOT load config)
ENV SUPERSET_SECRET_KEY=replace_this_with_a_real_secert_key

ENV SUPERSET_CONFIG_PATH=/app/pythonpath/superset_config.py

ENV SUPERSET_SECRET_PATH=/app/pythonpath/superset_config.py

USER superset

# docker build -t my-superset .
# docker run -d -p 8088:8088 --name superset-final my-superset
#
# First-time empty Postgres (schema `superset` must exist; metadata stays out of public):
# docker exec -it superset-final superset fab create-db
# docker exec -it superset-final superset db upgrade
# docker exec -it superset-final superset fab create-admin --username admin --firstname admin --lastname Admin --email admin@fab.org --password admin123
# docker exec -it superset-final superset init
# docker exec -it superset-final superset load_examples