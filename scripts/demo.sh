#!/usr/bin/env bash
#
# demo.sh — spin up the status-api docker-compose sandbox and watch the
# distributed service-status dashboard work live.
#
# This is a developer-tooling helper. It does NOT change application behaviour,
# the wire contract, or tests. It drives the existing docker-compose stack and
# talks to the API/dashboard exactly as the browser does.
#
# The port/topology facts below mirror docker-compose.yml (the single source of
# truth). Keep them in sync if the compose file changes:
#
#   postgres            internal only (no host port)
#   status-api-1/2/3    host 8081 / 8082 / 8083  -> container 8080  (dashboard + API)
#   mock-auth            host 9091 -> 8080   (/health + POST /__fault)
#   mock-payments        host 9092 -> 8080
#   mock-notifications   host 9093 -> 8080
#   mock-search          host 9094 -> 8080
#   mock-ai              host 9095 -> 8080
#
set -euo pipefail

# --- repo-root resolution (works from any cwd) ---------------------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

# --- topology constants (mirror docker-compose.yml) ----------------------------
API_HOST="localhost"
API_PORT="8081"                      # status-api-1 — dashboard + API
API_BASE="http://${API_HOST}:${API_PORT}"
ENV="dev"                            # demo env (seed keys are dev-only)
INSTANCE_PORTS=(8081 8082 8083)      # host ports for the three app instances
MOCKS="mock-auth mock-payments mock-notifications mock-search mock-ai"
DEFAULT_MOCK="mock-payments"
EXPECTED_MOCKS=5
CHECK_INTERVAL_SECS=5                # compose sets MONITORING_CHECK_INTERVAL=5s
DEMO_WAIT_SECS=10                    # 2 x interval, comfortable margin
STACK_SERVICES="postgres status-api-1 status-api-2 status-api-3 mock-auth mock-payments mock-notifications mock-search mock-ai"  # full stack (mirror docker-compose.yml)

# --- tiny output helpers -------------------------------------------------------
say()  { printf '%s\n' "$*"; }
ok()   { printf '[ok] %s\n' "$*"; }
warn() { printf '[!!] %s\n' "$*" >&2; }
die()  { printf '[!!] %s\n' "$*" >&2; exit 1; }
step() { printf '\n--- %s ---\n' "$*"; }

# --- compose detection (v2 `docker compose` first, then v1 `docker-compose`) ---
COMPOSE_CMD=""
detect_compose() {
  if command -v docker >/dev/null 2>&1 && docker compose version >/dev/null 2>&1; then
    COMPOSE_CMD="docker compose"
  elif command -v docker-compose >/dev/null 2>&1 && docker-compose version >/dev/null 2>&1; then
    COMPOSE_CMD="docker-compose"
  else
    die "neither 'docker compose' (v2) nor 'docker-compose' (v1) is available on PATH."
  fi
}

# Invoke the resolved compose command from the repo root (where docker-compose.yml lives).
compose() {
  ( cd "$REPO_ROOT" && $COMPOSE_CMD "$@" )
}

require_curl() {
  command -v curl >/dev/null 2>&1 || die "curl is required but not found on PATH."
}

# fetch URL -> prints body on 2xx (nothing on failure) and returns 0/1 accordingly
get() {
  curl -fsS --max-time 5 "$1" 2>/dev/null
}

# poll a URL until it answers 2xx (returns 0) or timeout (returns 1)
wait_http() {  # $1=url  $2=timeout_seconds
  local url="$1" timeout="${2:-120}" i=0
  while [ "$i" -lt "$timeout" ]; do
    if curl -fsS --max-time 3 "$url" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
    i=$((i + 1))
  done
  return 1
}

# --- mock/instance port lookup (explicit; no associative arrays for bash 3.2) --
mock_port() {
  case "$1" in
    mock-auth)          echo 9091 ;;
    mock-payments)      echo 9092 ;;
    mock-notifications) echo 9093 ;;
    mock-search)        echo 9094 ;;
    mock-ai)            echo 9095 ;;
    *)                  return 1 ;;
  esac
}

instance_port() {
  case "$1" in
    status-api-1) echo 8081 ;;
    status-api-2) echo 8082 ;;
    status-api-3) echo 8083 ;;
    *)            return 1 ;;
  esac
}

is_mode() {
  case "$1" in
    up|down|degraded|slow) return 0 ;;
    *) return 1 ;;
  esac
}

# --- API helpers ---------------------------------------------------------------
# number of services registered in an env (uses python3; grep fallback)
registered_count() {  # $1=env
  local env="$1" body n
  body="$(get "$API_BASE/api/v1/services?env=$env" || true)"
  [ -n "$body" ] || { echo 0; return 0; }
  if command -v python3 >/dev/null 2>&1; then
    n="$(printf '%s' "$body" | python3 -c 'import json,sys;print(json.load(sys.stdin)["summary"]["total"])' 2>/dev/null)"
  else
    n="$(printf '%s' "$body" | grep -o '"total"[[:space:]]*:[[:space:]]*[0-9][0-9]*' | grep -o '[0-9][0-9]*$' | head -n 1)"
  fi
  echo "${n:-0}"
}

wait_mocks_registered() {  # $1=timeout_seconds
  local timeout="$1" elapsed=0 total
  while [ "$elapsed" -lt "$timeout" ]; do
    total="$(registered_count "$ENV" || echo 0)"
    if [ "$total" -ge "$EXPECTED_MOCKS" ]; then
      return 0
    fi
    sleep 2
    elapsed=$((elapsed + 2))
  done
  return 1
}

is_stack_up() {
  curl -fsS --max-time 3 "$API_BASE/actuator/health/readiness" >/dev/null 2>&1
}

# is a compose service currently running? (empty compose ps -q output = not running)
service_running() {  # $1=service
  [ -n "$(compose ps -q "$1" 2>/dev/null || true)" ]
}

# preflight for every stack-dependent sub-command: fail fast (non-zero) if the
# expected compose services are not all running. Uses `compose ps` (v2 or v1) so
# it works even when the API itself isn't answering.
require_stack_up() {
  local svc missing=""
  for svc in $STACK_SERVICES; do
    service_running "$svc" || missing="${missing}${missing:+, }${svc}"
  done
  if [ -n "$missing" ]; then
    die "stack is not running (not up: ${missing}) — run './scripts/demo.sh up' first."
  fi
}

# actionable hint for a failed `compose up --build`. The raw compose error stays
# visible above; this names the likely cause and the documented workaround.
print_up_failure_hint() {
  cat >&2 <<'EOF'
[!!] `docker compose up --build` failed (see the error above).

A common cause on macOS is the Docker credential helper:

    error getting credentials - err: exit status 1, out: `User canceled the operation. (-128)`

It happens when ~/.docker/config.json sets "credsStore": "osxkeychain", which
prompts for credentials in a non-interactive shell. All base images are public,
so no credentials are actually needed. Workarounds (pick one):

  1. Run from an interactive terminal and allow the Keychain prompt, or
  2. Isolate the Docker config so no credential helper is consulted:

       export DOCKER_CONFIG="$(mktemp -d)"
       echo '{}' > "$DOCKER_CONFIG/config.json"

     …then re-run './scripts/demo.sh up'.

(Your ~/.docker/config.json was NOT modified.)
EOF
}

# human-readable matrix + counters for one env, from an optional base URL
show_matrix() {  # $1=env  $2=base_url(optional)
  local env="${1:-$ENV}" base="${2:-$API_BASE}" body
  body="$(get "$base/api/v1/services?env=$env" || true)"
  if [ -z "$body" ]; then
    warn "  no response from $base — is the stack up? run './demo.sh up'"
    return 0
  fi
  if command -v python3 >/dev/null 2>&1; then
    printf '%s' "$body" | python3 -c '
import json, sys
d = json.load(sys.stdin)
s = d.get("summary", {})
print("  total={total}  up={up}  degraded={degraded}  down={down}  unknown={unknown}".format(
    total=s.get("total"), up=s.get("up"), degraded=s.get("degraded"),
    down=s.get("down"), unknown=s.get("unknown")))
for it in d.get("items", []):
    lat = it.get("latencyMs")
    lat_s = "-" if lat is None else str(lat) + "ms"
    print("  {status:9}  {key:18}  {name:18}  latency={lat:>8}  failures={f}".format(
        status=it.get("status", "?"), key=it.get("key", "?"),
        name=it.get("name", "?"), lat=lat_s, f=it.get("consecutiveFailures")))
'
  else
    printf '%s\n' "$body"
  fi
}

# --- subcommands ---------------------------------------------------------------
print_urls() {
  say ""
  say "Dashboard:     ${API_BASE}/"
  say "Matrix (dev):  ${API_BASE}/api/v1/services?env=${ENV}"
  say "SSE stream:    ${API_BASE}/api/v1/events?env=${ENV}"
  say "Swagger UI:    ${API_BASE}/swagger-ui/index.html"
  say "Prometheus:    ${API_BASE}/actuator/prometheus"
  say ""
  say "Instances:  status-api-1 -> :8081 | status-api-2 -> :8082 | status-api-3 -> :8083"
  say "Mocks:      mock-auth :9091 | mock-payments :9092 | mock-notifications :9093 | mock-search :9094 | mock-ai :9095"
  say ""
  say "Next:"
  say "  ./demo.sh status      # matrix + counters"
  say "  ./demo.sh open        # open the dashboard in your browser"
  say "  ./demo.sh demo        # guided end-to-end story"
  say "  ./demo.sh fault down  # flip mock-payments down"
  say "  ./demo.sh logs        # follow app logs"
  say "  ./demo.sh down        # tear down"
}

cmd_up() {
  require_curl
  step "build + start the stack (detached)"
  if ! compose up -d --build; then
    print_up_failure_hint
    exit 1
  fi

  step "waiting for the three status-api instances to become ready"
  local port
  for port in "${INSTANCE_PORTS[@]}"; do
    ok "  waiting for readiness on :${port}…"
    if ! wait_http "http://${API_HOST}:${port}/actuator/health/readiness" 240; then
      die "status-api on :${port} did not become ready in time — run './demo.sh logs status-api-$((port - 8080))' to inspect."
    fi
  done
  ok "all three instances are ready."

  step "waiting for the ${EXPECTED_MOCKS} mock services to self-register"
  if ! wait_mocks_registered 120; then
    die "not all ${EXPECTED_MOCKS} mock services registered in time — run './demo.sh logs' to inspect."
  fi
  ok "all ${EXPECTED_MOCKS} mock services are registered (env=${ENV})."

  print_urls
}

cmd_status() {
  require_curl
  require_stack_up
  step "container overview"
  compose ps

  local env
  for env in dev prod; do
    step "matrix + counters (env=${env})"
    show_matrix "$env"
  done
}

cmd_fault() {
  require_curl
  local service="${1:-}" mode="${2:-}"
  if [ -z "$service" ]; then
    die "usage: fault [SERVICE] <up|down|degraded|slow>   (default service: ${DEFAULT_MOCK})"
  fi
  # single-arg form: `fault down` -> default service + that mode
  if [ -z "$mode" ]; then
    if is_mode "$service"; then
      mode="$service"
      service="$DEFAULT_MOCK"
    else
      die "unknown mode/service '${service}'. usage: fault [SERVICE] <up|down|degraded|slow>"
    fi
  fi
  is_mode "$mode" || die "invalid mode '${mode}' (expected: up|down|degraded|slow)"
  local port
  port="$(mock_port "$service")" || die "unknown mock service '${service}' (expected: ${MOCKS})"

  require_stack_up

  ok "POST /__fault {\"mode\":\"${mode}\"} -> ${service} (localhost:${port})"
  curl -fsS --max-time 5 -X POST "http://${API_HOST}:${port}/__fault" \
      -H 'Content-Type: application/json' \
      -d "{\"mode\":\"${mode}\"}" \
    || die "could not reach ${service}/__fault — is the stack up? run './demo.sh up'"
  ok "${service} is now '${mode}' — watch the dashboard card update within one check interval."
}

cmd_kill() {
  require_curl
  require_stack_up
  local instance="${1:-status-api-1}" port survivor=""
  case "$instance" in
    status-api-1|status-api-2|status-api-3) ;;
    *) die "unknown instance '${instance}' (expected: status-api-1|status-api-2|status-api-3)" ;;
  esac
  port="$(instance_port "$instance")"

  # only claim anything after confirming the target container exists and is running
  if ! service_running "$instance"; then
    die "${instance} is not running — run './scripts/demo.sh up' first."
  fi

  step "kill ${instance} (SIGKILL) to demonstrate rebalance-on-death"
  compose kill "$instance" >/dev/null

  # wait for the container to actually leave the 'running' state before claiming success
  local tries=0
  while service_running "$instance" && [ "$tries" -lt 10 ]; do
    sleep 1
    tries=$((tries + 1))
  done
  if service_running "$instance"; then
    die "failed to kill ${instance} — it is still running."
  fi
  ok "${instance} killed (its dashboard on :${port} is now down)."

  local p
  for p in "${INSTANCE_PORTS[@]}"; do
    if [ "$p" != "$port" ]; then survivor="$p"; break; fi
  done

  if [ -n "$survivor" ]; then
    sleep 3
    if curl -fsS --max-time 5 "http://${API_HOST}:${survivor}/" >/dev/null 2>&1; then
      ok "survivor on :${survivor} is still serving — the cluster rebalanced after ${instance}'s death."
      step "matrix via survivor on :${survivor}"
      show_matrix "$ENV" "http://${API_HOST}:${survivor}"
    else
      warn "could not verify the survivor dashboard at http://${API_HOST}:${survivor}/ — rebalancing may still be in progress (unverified)."
    fi
  fi
}

cmd_demo() {
  require_curl
  if ! is_stack_up; then
    step "stack is not up — starting it first"
    cmd_up || die "failed to start the stack"
  fi

  step "STEP 1 — the current matrix + counters"
  cmd_status

  step "STEP 2 — flip ${DEFAULT_MOCK} DOWN"
  cmd_fault "$DEFAULT_MOCK" down
  say "watching ${DEMO_WAIT_SECS}s for the next probe cycle…"
  sleep "$DEMO_WAIT_SECS"
  say "matrix now (watch ${DEFAULT_MOCK} turn red, counters move):"
  show_matrix "$ENV"

  step "STEP 3 — restore ${DEFAULT_MOCK}"
  cmd_fault "$DEFAULT_MOCK" up
  say "watching ${DEMO_WAIT_SECS}s…"
  sleep "$DEMO_WAIT_SECS"
  say "matrix now (${DEFAULT_MOCK} back to green):"
  show_matrix "$ENV"

  step "STEP 4 — kill status-api-1 (rebalance-on-death)"
  cmd_kill status-api-1

  step "STEP 5 — teardown (only when you are done)"
  say "  ./demo.sh down            # stop + remove, keep the DB volume"
  say "  ./demo.sh down --volumes  # also drop the DB volume (destructive)"
}

cmd_logs() {
  require_stack_up
  local svc="${1:-}"
  if [ -z "$svc" ]; then
    ok "following logs for the three app instances (Ctrl-C to stop)…"
    compose logs -f --tail=100 status-api-1 status-api-2 status-api-3
  else
    ok "following logs for '${svc}' (Ctrl-C to stop)…"
    compose logs -f --tail=100 "$svc"
  fi
}

cmd_down() {
  local drop=0
  if [ "$#" -gt 0 ]; then
    case "$1" in
      -v|--volumes) drop=1 ;;
      *) die "unknown flag '$1'. usage: down [-v|--volumes]" ;;
    esac
  fi
  if [ "$drop" -eq 1 ]; then
    warn "stopping + removing the stack AND dropping volumes (destructive, opt-in)."
    compose down -v
  else
    ok "stopping + removing the stack (DB volume kept)."
    compose down
  fi
}

cmd_open() {
  require_stack_up
  local url="${1:-${API_BASE}/}"
  case "$(uname -s)" in
    Darwin)
      open "$url" ;;
    Linux)
      if command -v xdg-open >/dev/null 2>&1; then xdg-open "$url"; else say "Dashboard: $url"; fi ;;
    *)
      say "Dashboard: $url" ;;
  esac
}

usage() {
  cat <<EOF
Usage: ./demo.sh [COMMAND] [ARGS…]

Spin up and demo the status-api service-status dashboard (docker-compose sandbox).
Ports/topology mirror docker-compose.yml (dashboard is the app's static root).

Commands:
  up                     Build (if needed) + start the stack detached, then wait
                         until it is actually usable (all 3 instances ready +
                         the 5 mock services self-registered). Prints URLs. (default)
  status                 Container overview + human-readable matrix & counters per env.
  fault [SERVICE] MODE   POST a mock's /__fault control endpoint.
                         MODE:    up | down | degraded | slow
                         SERVICE: mock-auth | mock-payments | mock-notifications
                                  | mock-search | mock-ai   (default: ${DEFAULT_MOCK})
                         The single-arg form is the mode, so 'fault down' just works.
  kill [INSTANCE]        Kill a status-api instance (default: status-api-1, SIGKILL)
                         to demonstrate rebalance-on-death. Survivors keep monitoring.
  demo                   Guided end-to-end story: up → matrix → fault down → restore
                         → kill an instance → show monitoring continues.
  logs [SERVICE]         Follow logs (default: the three app instances).
  down [-v|--volumes]    Stop + remove the stack. -v also drops volumes (destructive).
  open [URL]             Open the dashboard in the browser (macOS 'open', Linux 'xdg-open').

Examples:
  ./demo.sh                             # start everything + print URLs
  ./demo.sh fault down                  # mock-payments -> down
  ./demo.sh fault mock-auth degraded    # mock-auth -> degraded
  ./demo.sh demo                        # watch the whole system, step by step
EOF
}

# --- entrypoint ----------------------------------------------------------------
main() {
  detect_compose
  local cmd="${1:-up}"
  shift || true
  case "$cmd" in
    up)       cmd_up "$@" ;;
    status)   cmd_status "$@" ;;
    fault)    cmd_fault "$@" ;;
    kill)     cmd_kill "$@" ;;
    demo)     cmd_demo "$@" ;;
    logs)     cmd_logs "$@" ;;
    down)     cmd_down "$@" ;;
    open)     cmd_open "$@" ;;
    -h|--help|help) usage ;;
    *)        warn "unknown command '${cmd}'"; usage; exit 2 ;;
  esac
}

main "$@"
