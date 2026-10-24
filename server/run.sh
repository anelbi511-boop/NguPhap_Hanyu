#!/usr/bin/env bash
# Launch the Chinese Grammar AI server (Linux/macOS dev use).
# On startup the console prints a PAIRING CODE once - enter it in the
# Android app to pair this phone.
set -euo pipefail
cd "$(dirname "$0")"
if [ -d .venv ]; then
  # shellcheck disable=SC1091
  source .venv/bin/activate
fi
export HOST="${HOST:-127.0.0.1}"
export PORT="${PORT:-8730}"
exec python -m app.main
