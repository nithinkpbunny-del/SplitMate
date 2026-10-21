#!/usr/bin/env bash
# Wipes the SplitMate database and reseeds the demo data.
#
#   ./scripts/reseed-demo.sh              wipe, then start the backend (demo profile) in the foreground
#   ./scripts/reseed-demo.sh --seed-only  wipe, seed, then stop again (start the backend yourself afterwards)
#   ./scripts/reseed-demo.sh --yes        skip the confirmation question
#
# The backend must NOT be running: indexes and the default categories are created when it starts, so
# dropping the database under a running backend would leave it without them. The script refuses if it
# finds a backend on the configured port.
#
# Needs mongosh and Java 21. Reads MONGODB_URI and PORT from ./.env when present.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

SEED_ONLY=0
ASSUME_YES=0
ALLOW_REMOTE=0
for arg in "$@"; do
  case "$arg" in
    --seed-only) SEED_ONLY=1 ;;
    --yes|-y) ASSUME_YES=1 ;;
    --allow-remote) ALLOW_REMOTE=1 ;;
    -h|--help) sed -n '2,13p' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "Unknown option: $arg (see --help)" >&2; exit 2 ;;
  esac
done

# Read KEY=VALUE pairs from .env without executing it.
env_value() {
  [ -f .env ] || return 0
  grep -E "^$1=" .env | tail -1 | cut -d= -f2- | sed -e 's/^"//' -e 's/"$//' || true
}
MONGODB_URI="${MONGODB_URI:-$(env_value MONGODB_URI)}"
MONGODB_URI="${MONGODB_URI:-mongodb://localhost:27017/splitmate}"
PORT="${PORT:-$(env_value PORT)}"
PORT="${PORT:-8090}"

command -v mongosh >/dev/null || { echo "mongosh is not installed (https://www.mongodb.com/docs/mongodb-shell/install/)." >&2; exit 1; }

if curl -s -m 2 -o /dev/null "http://localhost:${PORT}/api/health"; then
  echo "A backend is still running on port ${PORT}. Stop it first (Ctrl+C in its terminal), then run this again." >&2
  exit 1
fi

if [[ "$MONGODB_URI" != *"localhost"* && "$MONGODB_URI" != *"127.0.0.1"* && $ALLOW_REMOTE -eq 0 ]]; then
  echo "MONGODB_URI does not point at localhost. Refusing to wipe a remote database (pass --allow-remote to override)." >&2
  exit 1
fi

DB_NAME="$(mongosh "$MONGODB_URI" --quiet --eval 'db.getName()')"
if [ $ASSUME_YES -eq 0 ]; then
  read -r -p "This DELETES the database '${DB_NAME}' (${MONGODB_URI}) and reseeds the demo data. Continue? [y/N] " answer
  [[ "$answer" =~ ^[Yy]$ ]] || { echo "Cancelled."; exit 1; }
fi

echo "Wiping '${DB_NAME}' ..."
mongosh "$MONGODB_URI" --quiet --eval 'db.dropDatabase()' >/dev/null

cd backend
if [ $SEED_ONLY -eq 0 ]; then
  echo "Starting the backend with the demo profile; it seeds the data on startup (Ctrl+C to stop) ..."
  exec ./mvnw -q spring-boot:run -Dspring-boot.run.profiles=demo
fi

echo "Seeding ..."
LOG="$(mktemp)"
set -m                                   # own process group, so the whole Maven + JVM tree can be stopped
./mvnw -q spring-boot:run -Dspring-boot.run.profiles=demo >"$LOG" 2>&1 &
PID=$!
cleanup() { kill -- -"$PID" 2>/dev/null || true; wait "$PID" 2>/dev/null || true; }
trap cleanup EXIT

for _ in $(seq 1 120); do
  if grep -q "Demo data ready" "$LOG"; then break; fi
  if ! kill -0 "$PID" 2>/dev/null; then echo "The backend stopped unexpectedly:" >&2; tail -30 "$LOG" >&2; exit 1; fi
  sleep 1
done
grep -q "Demo data ready" "$LOG" || { echo "Timed out waiting for the seeder:" >&2; tail -30 "$LOG" >&2; exit 1; }

cleanup
trap - EXIT
rm -f "$LOG"
echo "Done. Demo data is in '${DB_NAME}'. Start the backend with:"
echo "  cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=demo"
