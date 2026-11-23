#!/usr/bin/env bash
# Runs everything for local development in one terminal: Postgres (Docker), the backend and the frontend.
# Ctrl+C stops all of it.
#
#   ./scripts/dev.sh              start everything
#   ./scripts/dev.sh --keep-db    leave Postgres running after Ctrl+C
#   ./scripts/dev.sh --no-docker  use a Postgres you run yourself on localhost:5432
#                                 (database combat_dnd, user combat, password combat)
set -euo pipefail
cd "$(dirname "$0")/.."

KEEP_DB=false
USE_DOCKER=true
for arg in "$@"; do
  case "$arg" in
    --keep-db) KEEP_DB=true ;;
    --no-docker) USE_DOCKER=false ;;
    -h | --help) sed -n '2,8p' "$0"; exit 0 ;;
    *) echo "Unknown option: $arg (try --help)" >&2; exit 2 ;;
  esac
done

say() { printf '\033[1;33m==>\033[0m %s\n' "$*"; }
fail() { printf '\033[1;31mError:\033[0m %s\n' "$*" >&2; exit 1; }

# ---------- check the tools, with the fix for each ----------
command -v java >/dev/null || fail "Java isn't installed. Run: sudo apt install -y openjdk-21-jdk"
java_major=$(java -version 2>&1 | awk -F'"' '/version/ {split($2, v, "."); print v[1]; exit}')
[[ "${java_major:-0}" -ge 21 ]] || fail "Java 21 or newer is needed (found ${java_major:-none}). Run: sudo apt install -y openjdk-21-jdk"
command -v node >/dev/null || fail "Node isn't installed. Install nvm, then: nvm install 22 (see README)"
node_major=$(node -p 'process.versions.node.split(".")[0]')
[[ "$node_major" -ge 22 ]] || fail "Node 22 or newer is needed (found $(node -v)). Run: nvm install 22 && nvm alias default 22"
command -v curl >/dev/null || fail "curl isn't installed. Run: sudo apt install -y curl"
if $USE_DOCKER; then
  command -v docker >/dev/null || fail "Docker isn't installed. See the README, or use --no-docker with your own Postgres."
  docker info >/dev/null 2>&1 || fail "Docker isn't running. Open Docker Desktop, wait for the whale icon, and try again."
fi
if curl -s -o /dev/null localhost:8080; then
  fail "Something is already using port 8080. Is another copy of the backend still running?"
fi

# ---------- database ----------
if $USE_DOCKER; then
  say "Starting Postgres"
  docker compose up -d --wait db
fi

# ---------- frontend packages, only when missing or out of date ----------
if [[ ! -d frontend/node_modules || frontend/package-lock.json -nt frontend/node_modules/.package-lock.json ]]; then
  say "Installing frontend packages"
  (cd frontend && npm install --no-audit --no-fund)
fi

# ---------- run both, each in its own process group so Ctrl+C can stop the whole tree ----------
pids=()
cleanup() {
  trap - INT TERM EXIT
  echo
  say "Stopping"
  for pid in "${pids[@]}"; do kill -TERM -- "-$pid" 2>/dev/null || true; done
  wait 2>/dev/null || true
  if $USE_DOCKER && ! $KEEP_DB; then
    docker compose stop db >/dev/null 2>&1 || true
    say "Postgres stopped (your data is kept). Use --keep-db to leave it running."
  fi
}
trap cleanup INT TERM EXIT

start() { # start <tag> <ansi color> <dir> <command...>
  local tag=$1 color=$2 dir=$3; shift 3
  setsid bash -c 'tag=$1 color=$2; cd "$3" || exit 1; shift 3; "$@" 2>&1 | sed -u "s/^/\x1b[${color}m[${tag}]\x1b[0m /"' \
    _ "$tag" "$color" "$dir" "$@" &
  pids+=($!)
}

say "Starting the backend (the first run downloads dependencies, which takes a minute or two)"
start api 36 backend ./gradlew bootRun --console=plain

until curl -sf localhost:8080/actuator/health >/dev/null; do
  kill -0 "${pids[0]}" 2>/dev/null || fail "The backend stopped. Look at the [api] lines above for the reason."
  sleep 2
done

start web 35 frontend npm run dev -- --strictPort
until curl -sf localhost:5173 >/dev/null; do
  kill -0 "${pids[1]}" 2>/dev/null || fail "The frontend stopped. Look at the [web] lines above for the reason."
  sleep 1
done

ENCOUNTER=00000000-0000-4000-8000-0000000000a1
LYRA=00000000-0000-4000-8000-00000000017a
echo
say "Ready. Open these in two browser tabs:"
echo "      DM:     http://localhost:5173/combat/$ENCOUNTER?as=dm"
echo "      Player: http://localhost:5173/combat/$ENCOUNTER?as=player:$LYRA"
say "Press Ctrl+C to stop everything."
echo
wait
