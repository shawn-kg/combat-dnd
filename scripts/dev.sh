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
node_major() { command -v node >/dev/null && node -p 'process.versions.node.split(".")[0]' || echo 0; }
# An old system Node (Ubuntu's apt package is often v12) can win over nvm's in a new terminal.
# If nvm is installed, load it and switch to Node 22 for this run.
if [[ "$(node_major)" -lt 22 && -s "${NVM_DIR:-$HOME/.nvm}/nvm.sh" ]]; then
  set +u; . "${NVM_DIR:-$HOME/.nvm}/nvm.sh"
  if nvm use "$(cat .nvmrc)" >/dev/null 2>&1; then say "Using Node $(node -v) from nvm"; fi
  set -u
fi
command -v node >/dev/null || fail "Node isn't installed. Install nvm, then: nvm install 22 (see README)"
node_major=$(node_major)
[[ "$node_major" -ge 22 ]] || fail "Node 22 or newer is needed (found $(node -v)). Run: nvm install 22 && nvm alias default 22"
command -v curl >/dev/null || fail "curl isn't installed. Run: sudo apt install -y curl"

# Docker: on WSL, open Docker Desktop if it isn't running yet and wait for it.
docker_ready() { command -v docker >/dev/null && docker info >/dev/null 2>&1; }
is_wsl() { grep -qi microsoft /proc/version 2>/dev/null; }
DOCKER_DESKTOP_WIN='C:\Program Files\Docker\Docker\Docker Desktop.exe'
DOCKER_DESKTOP_WSL='/mnt/c/Program Files/Docker/Docker/Docker Desktop.exe'
if $USE_DOCKER && ! docker_ready; then
  if is_wsl && [[ -f "$DOCKER_DESKTOP_WSL" ]]; then
    say "Docker isn't running, so opening Docker Desktop (this can take a minute)"
    # Started through Windows so it outlives this script; run from /mnt/c to avoid cmd.exe's UNC-path warning.
    (cd /mnt/c && cmd.exe /c start "" "$DOCKER_DESKTOP_WIN" >/dev/null 2>&1) || true
    for _ in $(seq 1 60); do docker_ready && break; sleep 3; done
    docker_ready || fail "Docker Desktop didn't become ready within 3 minutes. Open it yourself, check that
       Settings > Resources > WSL integration is on for this Ubuntu, and run the script again."
    say "Docker is ready"
  elif command -v docker >/dev/null; then
    fail "Docker isn't running. Open Docker Desktop (or on plain Linux: sudo systemctl start docker) and try again."
  else
    fail "Docker isn't installed. See the README, or use --no-docker with your own Postgres."
  fi
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
