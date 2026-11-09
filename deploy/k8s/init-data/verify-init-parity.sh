#!/usr/bin/env bash
# =============================================================================
# Proves that the k8s init builds the same database as the Docker init, on two throwaway postgres containers
# (nothing else is touched):
#   Docker  deploy/init-scripts/00-init-all.sh, mounted exactly as deploy/environments/dev mounts it
#   k8s     init-flowable/create -> init-platform-schema -> init-platform-seed -> init-flowable/upgrade/MIGRATE,
#           the order in deploy/k8s/init-data/README.md, each file with psql -v ON_ERROR_STOP=1, run by an account
#           that neither owns the database nor is a superuser (as in shared k8s databases)
# then compares
#   catalog-snapshot.sql  columns, constraints, indexes, functions, triggers, views, sequences, extensions, comments,
#                         Flowable versions, role / database settings. Only the physical tables of the Docker init's
#                         demo packages (DEMO_TABLES) may differ.
#   seed-snapshot.sql     every row of every platform table. Only Docker may have more, and only what k8s leaves out
#                         on purpose (SEED_DOCKER_ONLY); k8s may differ only in its initial System Administrator
#                         (SEED_K8S_ONLY).
# Anything else fails the run. Finally it runs the k8s schema file and the seed's 04 / 05 sections a second time and
# checks that the catalog does not change.
#
#   deploy/k8s/init-data/verify-init-parity.sh [--keep]     (Git Bash or Linux; needs docker and node)
#
# Regenerate the k8s files first (node deploy/k8s/init-data/build-all-in-one.mjs) when a script changed.
# =============================================================================
set -euo pipefail
export MSYS_NO_PATHCONV=1

HERE=$(cd "$(dirname "$0")" && (pwd -W 2>/dev/null || pwd))
REPO=$(cd "$(dirname "$0")/../../.." && (pwd -W 2>/dev/null || pwd))
IMAGE=postgres:16.5-alpine
DOCKER_PG=init-parity-docker
K8S_PG=init-parity-k8s
# The k8s init runs as this account; the database belongs to "parity".
K8S_APP=parity_app
# Physical tables of the demo function units (16-meeting-participant-collection/06-create-physical-tables.sql).
DEMO_TABLES="meeting participants"
# Seed rows only the Docker init writes (extended regexes on seed-snapshot.sql lines), each with the reason the k8s
# seed does not get them (K8S_SEED_EXCLUDED / TRANSFORMS in build-all-in-one.mjs).
SEED_DOCKER_ONLY=(
  # demo function units with their relation tables, and the dev-only Platform Showcase (90-post-seed/03)
  '^(dw|rt)_[a-z_]+\|'
  # the developer account: the developer block of 01-admin/05 and 90-post-seed/03 (PRD D-22, A-33)
  '"(id|user_id)": "user-dev"'
  # HASE organization demo data, 01-admin/06 and 11: the BU tree under ASP, the hase-hmdc BU, its HMDC roles
  '"path": "/e30b16fa-874e-435c-9f15-7ba31416678a'
  '"business_unit_id": "2ca743c1-2af5-4c44-866b-ae8e1ba60acb"'
  '^sys_roles\|.*"code": "HMDC_'
  # roles of the Digital Lending demo function unit and their E2E grants, 01-admin/13
  '"(id|role_id)": "role-(document-verifier|operations|credit-officer|risk-officer|risk-manager|senior-manager|finance-officer|finance-manager)"'
  # virtual groups and members of the Digital Lending demo package (08-digital-lending-v2-en)
  '"(id|group_id)": "vg-(credit-officers|doc-verifiers|finance-team|risk-officers)"'
)
# Seed rows only the k8s init writes: its initial System Administrator is admin (90-post-seed/04 k8s variant, D-22).
SEED_K8S_ONLY=(
  '^sys_virtual_group_members\|.*"user_id": "user-admin",.*"group_id": "vg-sys-admins",'
)
OUT=$(mktemp -d)
KEEP=${1:-}

cleanup() {
  if [ "$KEEP" != "--keep" ]; then docker rm -fv "$DOCKER_PG" "$K8S_PG" >/dev/null 2>&1 || true; fi
}
trap cleanup EXIT

node "$HERE/build-all-in-one.mjs" --check

docker rm -fv "$DOCKER_PG" "$K8S_PG" >/dev/null 2>&1 || true
docker run -d --name "$DOCKER_PG" -e POSTGRES_DB=parity -e POSTGRES_USER=parity -e POSTGRES_PASSWORD=parity \
  -v "$REPO/deploy/init-scripts:/docker-entrypoint-initdb.d:ro" \
  -v "$HERE/init-flowable:/flowable-sql:ro" "$IMAGE" >/dev/null
docker run -d --name "$K8S_PG" -e POSTGRES_DB=parity -e POSTGRES_USER=parity -e POSTGRES_PASSWORD=parity \
  -v "$HERE:/k8s:ro" "$IMAGE" >/dev/null

psql_docker() { docker exec "$DOCKER_PG" psql -U parity -d parity -v ON_ERROR_STOP=1 -q "$@"; }
psql_k8s() { docker exec "$K8S_PG" psql -U "$K8S_APP" -d parity -v ON_ERROR_STOP=1 -q "$@"; }

# The image's entrypoint runs initdb and the mounted scripts on a temporary server (socket only, so pg_isready
# already answers), stops it, logs "PostgreSQL init process complete" and then starts the real server. Ready means
# both: that line is logged and the server answers.
wait_for_init() { # container, label
  for _ in $(seq 1 360); do
    if [ "$(docker inspect -f '{{.State.Running}}' "$1")" != "true" ]; then
      docker logs --tail 40 "$1"; echo "FAIL: $2 stopped"; exit 1
    fi
    if docker logs "$1" 2>&1 | grep -q "PostgreSQL init process complete" \
        && docker exec "$1" pg_isready -U parity -d parity -q; then return 0; fi
    sleep 1
  done
  docker logs --tail 40 "$1"; echo "FAIL: $2 not ready after 6 minutes"; exit 1
}

echo "Waiting for the Docker init (00-init-all.sh)..."
wait_for_init "$DOCKER_PG" "the Docker init"
docker logs "$DOCKER_PG" > "$OUT/docker-init.log" 2>&1
grep -q "Database Initialization Complete!" "$OUT/docker-init.log" || { echo "FAIL: Docker init incomplete"; exit 1; }
if grep -q "ERROR" "$OUT/docker-init.log"; then grep "ERROR" "$OUT/docker-init.log"; echo "FAIL: errors in the Docker init"; exit 1; fi

echo "Running the k8s init as $K8S_APP (not the database owner, not a superuser)..."
wait_for_init "$K8S_PG" "the k8s postgres container"
docker exec "$K8S_PG" psql -U parity -d parity -v ON_ERROR_STOP=1 -q -c "CREATE ROLE $K8S_APP LOGIN" \
  -c "GRANT CONNECT, CREATE ON DATABASE parity TO $K8S_APP" -c "GRANT ALL ON SCHEMA public TO $K8S_APP"
for f in init-flowable/create/flowable.postgres.all.create.sql init-platform-schema/all-in-one-for-gui.sql \
         init-platform-seed/all-in-one-for-gui.sql init-flowable/upgrade/MIGRATE-7.0.0-to-7.2.0-for-gui.sql; do
  echo "  $f"
  psql_k8s -f "/k8s/$f" >/dev/null
done

# --- objects ---
docker cp "$HERE/catalog-snapshot.sql" "$DOCKER_PG:/tmp/catalog-snapshot.sql"
docker cp "$HERE/seed-snapshot.sql" "$DOCKER_PG:/tmp/seed-snapshot.sql"
psql_docker -f /tmp/catalog-snapshot.sql > "$OUT/docker.txt"
psql_k8s -f /k8s/catalog-snapshot.sql > "$OUT/k8s.txt"
grep -q '^dbsetting|<init account> in this database|TimeZone=Asia/Hong_Kong$' "$OUT/k8s.txt" \
  || { echo "FAIL: the k8s init did not set the init account's time zone in this database (00-schema/101)"; exit 1; }
if grep -q '^dbsetting|.* in all databases|' "$OUT/k8s.txt"; then
  echo "FAIL: a setting for all databases of the instance (00-schema/101 sets this database only)"; exit 1
fi

demo_pattern=$(for t in $DEMO_TABLES; do printf '%s' "^[<>] [a-z]*\\|($t\\.|${t}_id_seq\\|)|"; done)
demo_pattern=${demo_pattern%|}
diff "$OUT/docker.txt" "$OUT/k8s.txt" | grep '^[<>]' > "$OUT/diff.txt" || true
unexpected=$(grep -Ev "$demo_pattern" "$OUT/diff.txt" || true)
echo "Objects: Docker $(wc -l < "$OUT/docker.txt"), k8s $(wc -l < "$OUT/k8s.txt");" \
  "$(grep -cE "$demo_pattern" "$OUT/diff.txt" || true) only in Docker from demo packages ($DEMO_TABLES)"
if [ -n "$unexpected" ]; then
  echo "$unexpected"; echo "FAIL: the k8s init differs from the Docker init"; exit 1
fi

# --- seed data ---
psql_docker -f /tmp/seed-snapshot.sql > "$OUT/docker-seed.txt"
psql_k8s -f /k8s/seed-snapshot.sql > "$OUT/k8s-seed.txt"
diff "$OUT/docker-seed.txt" "$OUT/k8s-seed.txt" | grep '^[<>]' > "$OUT/seed-diff.txt" || true
docker_only_pattern=$(IFS='|'; echo "${SEED_DOCKER_ONLY[*]}")
k8s_only_pattern=$(IFS='|'; echo "${SEED_K8S_ONLY[*]}")
sed -n 's/^< //p' "$OUT/seed-diff.txt" > "$OUT/seed-docker-only.txt"
sed -n 's/^> //p' "$OUT/seed-diff.txt" > "$OUT/seed-k8s-only.txt"
unexpected_seed=$( { grep -Ev "$docker_only_pattern" "$OUT/seed-docker-only.txt" | sed 's/^/only in Docker: /'; \
  grep -Ev "$k8s_only_pattern" "$OUT/seed-k8s-only.txt" | sed 's/^/only in k8s: /'; } || true)
echo "Seed rows: Docker $(wc -l < "$OUT/docker-seed.txt"), k8s $(wc -l < "$OUT/k8s-seed.txt");" \
  "$(wc -l < "$OUT/seed-docker-only.txt") only in Docker, $(wc -l < "$OUT/seed-k8s-only.txt") only in k8s"
if [ -n "$unexpected_seed" ]; then
  echo "$unexpected_seed"; echo "FAIL: the k8s seed differs from the Docker seed"; exit 1
fi
grep -Eq "$k8s_only_pattern" "$OUT/seed-k8s-only.txt" \
  || { echo "FAIL: the k8s seed does not make admin its initial System Administrator"; exit 1; }

echo "Running the k8s schema file and the seed's 04 / 05 sections again..."
psql_k8s -f /k8s/init-platform-schema/all-in-one-for-gui.sql >/dev/null
# 05 must follow: 00-schema/66 widens sys_roles.chk_role_type again, 05 narrows it (no BU_UNBOUNDED).
docker exec "$K8S_PG" sh -c "sed -n '/^-- 90-post-seed\/04-platform-access-model-switch.sql\$/,\$p' \
  /k8s/init-platform-seed/all-in-one-for-gui.sql > /tmp/switch.sql"
psql_k8s -f /tmp/switch.sql >/dev/null
psql_k8s -f /k8s/catalog-snapshot.sql > "$OUT/k8s-again.txt"
if ! diff "$OUT/k8s.txt" "$OUT/k8s-again.txt"; then echo "FAIL: running again changed the catalog"; exit 1; fi

echo "PASS: the k8s init builds the Docker init's objects and seed (outputs in $OUT)"
