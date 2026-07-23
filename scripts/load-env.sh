#!/usr/bin/env sh
set -eu

ENV_FILE="${1:-$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)/.env}"

if [ ! -f "$ENV_FILE" ]; then
  echo "Environment file not found: $ENV_FILE" >&2
  exit 1
fi

set -a
# The file is local and must contain only KEY=VALUE entries; it is never committed.
. "$ENV_FILE"
set +a

echo "Loaded local environment from $ENV_FILE into the current process."
