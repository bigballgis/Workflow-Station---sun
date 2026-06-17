#!/bin/sh
# Minimal wget wrapper: take last argument as URL
if [ $# -eq 0 ]; then
  exit 1
fi
last="$1"
for a in "$@"; do
  last="$a"
done
exec /usr/bin/curl -sS "$last"
