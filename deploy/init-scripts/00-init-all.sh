#!/bin/bash
set -e

echo "Starting database initialization..."

# Execute schema creation scripts
echo "Creating schemas..."
for file in /docker-entrypoint-initdb.d/00-schema/*.sql; do
    if [ -f "$file" ]; then
        echo "Executing $file..."
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$file"
    fi
done

# Execute admin user scripts
echo "Creating admin users..."
for file in /docker-entrypoint-initdb.d/01-admin/*.sql; do
    if [ -f "$file" ]; then
        echo "Executing $file..."
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$file"
    fi
done

# Execute test data scripts
echo "Loading test data..."
for file in /docker-entrypoint-initdb.d/02-test-data/*.sql; do
    if [ -f "$file" ]; then
        echo "Executing $file..."
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$file"
    fi
done

# Execute purchase workflow scripts
echo "Loading purchase workflow..."
for file in /docker-entrypoint-initdb.d/04-purchase-workflow/*.sql; do
    if [ -f "$file" ]; then
        echo "Executing $file..."
        psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -f "$file"
    fi
done

echo "Database initialization completed!"
