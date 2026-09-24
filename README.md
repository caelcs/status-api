# status-api

Service-status dashboard backend. A self-service, distributed service-health
monitor: monitored services register themselves once, and this service
continuously probes their `/health` endpoints, maps each probe to
`up` / `degraded` / `down` / `unknown`, persists a snapshot + transition history,
and pushes live changes to browsers over Server-Sent Events.

This README is a **step-by-step onboarding guide** — start at the top and run
each command in order. It assumes no prior context.

---

## 1. What this is

| Fact | Value |
|------|-------|
| Language / runtime | **Java 25 LTS** (JDK `25.0.1-graalce`) |
| Framework | **Spring Boot 4** / Spring Framework 7 |
| Build | **Gradle wrapper only** (`./gradlew`) — no Maven, no global Gradle |
| Shape | Hexagonal-ish (ports / adapters), virtual-threaded |
| Topology | 3 stateless `status-api` instances ("pods") + **Postgres 16** + a static **SSE dashboard** |
| Coordination | No leader — `claim-and-advance` over `services.next_check_at` (`FOR UPDATE SKIP LOCKED`) |
| Live updates | Postgres `LISTEN/NOTIFY` → each instance's `GET /api/v1/events` SSE stream |
| Dashboard | Static page (`index.html` + `app.js`, native `EventSource`), served by any instance |

**Further reading**

- [`docs/architecture.md`](docs/architecture.md) — the component map, runtime
  flows, read order, and test map. Start here when you need to change code.
- `docs/features/001-service-status-dashboard/prd.md` and
  `docs/features/001-service-status-dashboard/api-contract.md` — the feature's
  requirements and the frozen REST/SSE wire contract. **These live in the hub
  repo (`samtex-interview`), not this repo** — see the hub's
  `docs/features/001-service-status-dashboard/` directory.

---

## 2. Prerequisites

| Requirement | Why | How to check |
|-------------|-----|--------------|
| **Docker engine** (or Podman) | Runs the sandbox containers | `docker version` (or `podman version`) |
| **Compose** — either the v2 plugin (`docker compose`) **or** the standalone `docker-compose` | Drives the multi-container stack | `docker compose version` **or** `docker-compose version` — one of them is enough; `demo.sh` auto-detects |
| **JDK 25** (pinned) | Builds/tests run under Java 25, **never** the machine default | `"$HOME/.sdkman/candidates/java/25.0.1-graalce/bin/java" -version` |
| **Gradle wrapper** | Only build tool (committed; nothing to install) | `./gradlew --version` |
| **`python3`** | `demo.sh`'s matrix/counter formatting (degrades gracefully to a plain JSON dump) | `python3 --version` |
| **`curl`** | The script's health/API checks | `curl --version` |

> **Two things that trip people up:**
>
> 1. **The machine default JDK is often not Java 25** (this host's default is
>    Java 27). Always invoke Gradle with the pin — see
>    [Step 6](#9-step-6--run-the-tests) and the [Wrong JDK](#11-troubleshooting) row.
> 2. **`docker compose` (v2) and `docker-compose` (v1) are different.** On this
>    host only the standalone `docker-compose` is present
>    (`docker compose version` → `docker: unknown command: docker compose`), and
>    the script falls back automatically. Either is fine.

The exact verification commands, with real output from this host:

```bash
$ docker version
Docker version 29.8.1, build 4a63305d74

$ docker compose version        # v2 — may not exist on your machine
$ docker-compose version        # v1/standalone — this host uses this one
Docker Compose version 5.5.1

$ "$HOME/.sdkman/candidates/java/25.0.1-graalce/bin/java" -version
openjdk version "25.0.1" 2025-10-21
OpenJDK Runtime Environment GraalVM CE 25.0.1+8.1 (build 25.0.1+8-jvmci-b01)

$ python3 --version
Python 3.14.6
```

If you don't have JDK 25, install it with SDKMAN (`sdk install java 25.0.1-graalce`).

---

## 3. Step 0 — Get the code

The `status-api` repo's **`main` branch is currently empty** — all the code lives
on the `feature/001-service-status-dashboard` branch. A plain `git clone` would
land you on an empty checkout, so clone the branch that has the code directly:

```bash
git clone -b feature/001-service-status-dashboard git@github.com:caelcs/status-api.git
cd status-api
```

Once `feature/001-service-status-dashboard` is merged it becomes the default
branch, at which point the `-b` flag can be dropped.

**What you should see** afterwards (`ls` at the repo root):

```
build.gradle   docker-compose.yml   Dockerfile   gradle/   gradle.properties
gradlew        gradlew.bat          mock-services/   scripts/   settings.gradle
src/
```

`scripts/demo.sh` (used in every step below) is present and executable.

> **Already have the hub repo (`samtex-interview`)?** The PRD, API contract, and
> ADR for this feature live there — see `docs/features/001-service-status-dashboard/`
> (cross-linked under [What this is](#1-what-this-is)).

---

## 4. Step 1 — Start the environment

From the repo root:

```bash
cd status-api
./scripts/demo.sh        # `up` is the default sub-command
```

(`./scripts/demo.sh up` is equivalent.)

**What it does**, in order:

1. Detects the Compose binary (`docker compose` v2 first, then `docker-compose` v1).
2. `compose up -d --build` — builds the image (first run) and starts the stack detached.
3. Polls all three `status-api` instances' `/actuator/health/readiness` until they answer 2xx.
4. Waits until all 5 mock services have self-registered (env `dev`).
5. Prints the URLs and next commands.

**How long:** a minute or two on first run (image build); a few tens of seconds
once the image is cached. The script's timeouts allow up to ~4 minutes for
instance readiness and ~2 minutes for mock registration.

**Expected output** — you should see the `[ok]` markers for the three instances
and the "all 5 mock services are registered" line, followed by the URLs:

```
--- build + start the stack (detached) ---
[ok]   waiting for readiness on :8081…
[ok]   waiting for readiness on :8082…
[ok]   waiting for readiness on :8083…
[ok] all three instances are ready.
[ok] all 5 mock services are registered (env=dev).

Dashboard:     http://localhost:8081/
Matrix (dev):  http://localhost:8081/api/v1/services?env=dev
SSE stream:    http://localhost:8081/api/v1/events?env=dev
Swagger UI:    http://localhost:8081/swagger-ui/index.html
Prometheus:    http://localhost:8081/actuator/prometheus

Instances:  status-api-1 -> :8081 | status-api-2 -> :8082 | status-api-3 -> :8083
Mocks:      mock-auth :9091 | mock-payments :9092 | mock-notifications :9093 | mock-search :9094 | mock-ai :9095
```

9 containers total: `postgres` + 3× `status-api` + 5 mocks.

> **`error getting credentials …`?** Stop — see the
> [first Troubleshooting row](#11-troubleshooting).

---

## 5. Step 2 — Verify it's up

Run the script's status command plus a few direct `curl` checks.

```bash
./scripts/demo.sh status
```

**Expected output** (trimmed) — the container overview plus a matrix and
counters per environment:

```
--- matrix + counters (env=dev) ---
  total=5  up=5  degraded=0  down=0  unknown=0
  up         mock-ai             mock-ai             latency=     2ms  failures=0
  up         mock-auth           mock-auth           latency=     2ms  failures=0
  up         mock-notifications  mock-notifications  latency=     2ms  failures=0
  up         mock-payments       mock-payments       latency=     2ms  failures=0
  up         mock-search         mock-search         latency=     2ms  failures=0

--- matrix + counters (env=prod) ---
  total=0  up=0  degraded=0  down=0  unknown=0
```

Now the raw API — the same shape the dashboard consumes:

```bash
curl -s http://localhost:8081/actuator/health
# {"groups":["liveness","readiness"],"status":"UP"}

curl -s http://localhost:8081/actuator/health/readiness
# {"status":"UP"}

curl -s "http://localhost:8081/api/v1/services?env=dev"
# {"items":[{"key":"mock-ai","env":"dev","status":"up", ...}, ... 5 items ...],
#  "summary":{"total":5,"up":5,"degraded":0,"down":0,"unknown":0},"limit":50,"offset":0}

curl -s "http://localhost:8081/api/v1/services?env=prod"
# {"items":[],"summary":{"total":0,"up":0,"degraded":0,"down":0,"unknown":0},"limit":50,"offset":0}
```

**What to check:** `env=dev` returns 5 services, all `up`; `env=prod` is empty
(no prod keys/registrations yet). If `dev` shows fewer than 5, wait one interval
(~5s) and retry — see the [dashboard-empty row](#11-troubleshooting).

---

## 6. Step 3 — Open the dashboard

Open any of the three URLs in a browser (they're interchangeable):

```
http://localhost:8081/
http://localhost:8082/
http://localhost:8083/
```

Or:

```bash
./scripts/demo.sh open        # opens http://localhost:8081/ in your browser
```

**What you should see:** a green status matrix (5 rows) plus aggregate counters
("5 up / 0 down / …"), live-updating via SSE.

**Why any pod works:** there is no leader. A status transition collected by
*one* instance is published to Postgres `LISTEN/NOTIFY`, and every instance
forwards it to its own SSE clients — so the dashboard on `:8082` updates even
when the probe that caused the change ran on `:8081`.

---

## 7. Step 4 — Register your own service

The mocks self-register; this is the path for *your* service.

**Where API keys come from.** Keys are seeded on startup by `ApiKeySeeder`
(`src/main/java/dev/status/config/ApiKeySeeder.java`), which reads the
`app.seed-api-keys` property from the environment — there is **no default in
`application.yml`** (it defaults to empty). The demo stack supplies it via the
`APP_SEED_API_KEYS` env var in `docker-compose.yml` (Spring Boot relaxed binding
maps `APP_SEED_API_KEYS` → `app.seed-api-keys`), formatted as `key:env:name`,
comma-separated:

```
mock-auth-key:dev:mock-auth,mock-payments-key:dev:mock-payments,mock-notifications-key:dev:mock-notifications,mock-search-key:dev:mock-search,mock-ai-key:dev:mock-ai
```

Each key is bound to a single environment (all five seeds are `dev`).

**Register with `X-API-Key`:**

```bash
curl -s -X POST http://localhost:8081/api/v1/services \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: mock-payments-key' \
  -d '{
    "key": "payments-worker",
    "name": "Payments Worker",
    "env": "dev",
    "healthUrl": "http://mock-payments:8080/health",
    "tags": ["example"]
  }'
```

This returns a `201` with the created `ServiceStatus`. Within one interval
(~5s) the new card appears on the dashboard, and
`curl -s "http://localhost:8081/api/v1/services?env=dev"` lists it.

**The `/health` contract your service must expose** (see the API contract §5 —
do not restate it here):

```
GET {healthUrl} → 200
{ "status": "ok", "version": "1.2.3" }   // "status" must be "ok" | "degraded"
```

- `2xx` + `"ok"` → `up` · `2xx` + `"degraded"` → `degraded` · non-`2xx`/timeout → `down`.

**The `env` binding rule.** The request body's `env` **must match the key's
bound env**, or you get `403`. All five seed keys are `dev`, so registering with
`"env": "prod"` is rejected:

```bash
curl -s -X POST http://localhost:8081/api/v1/services \
  -H 'Content-Type: application/json' \
  -H 'X-API-Key: mock-payments-key' \
  -d '{"key":"x","name":"X","env":"prod","healthUrl":"http://mock-payments:8080/health"}'
# {"type":"about:blank","title":"Forbidden","status":403,"detail":"API key is not authorized for environment prod"}
```

**To register a real service with its own key** (rather than reusing a mock's),
add a `key:env:name` entry to `APP_SEED_API_KEYS` in `docker-compose.yml` and
re-run `./scripts/demo.sh up` (seeding is idempotent), then register with that
key and matching `env`.

Full registration/contract details:
**`docs/features/001-service-status-dashboard/api-contract.md`** — note it lives
in the **hub repo** (`samtex-interview`), not this one.

---

## 8. Step 5 — See it fail (the point of the tool)

Fault-inject a mock and watch the dashboard react.

```bash
./scripts/demo.sh fault down    # mock-payments -> down (default mock)
```

Watch the `mock-payments` card turn red and the counters move **within one check
interval** (~5s in the demo stack). Then restore it:

```bash
./scripts/demo.sh fault up      # mock-payments -> up (green again)
```

Kill an entire `status-api` instance and confirm the survivors carry on with
**no duplicate probing** (the persisted schedule is re-claimed atomically):

```bash
./scripts/demo.sh kill          # SIGKILL status-api-1 (default instance)
```

Expected: the script reports `status-api-1` killed, then verifies a survivor
(`:8082` or `:8083`) is still serving and prints its matrix.

Prefer a narrated walk-through?

```bash
./scripts/demo.sh demo          # guided: up → matrix → fault down → restore → kill → teardown hints
```

`fault` also accepts `degraded` and `slow`, and a named service
(`./scripts/demo.sh fault mock-auth degraded`).

---

## 9. Step 6 — Run the tests

```bash
JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.1-graalce" ./gradlew cleanTest test --no-build-cache
```

- **Always under the Java 25 pin** — never the machine default (see the
  [Wrong JDK](#11-troubleshooting) row).
- **Testcontainers needs a running Docker daemon** (the same engine that runs
  the sandbox) — it spins up throwaway Postgres containers for the integration
  tests.
- Current expected tally: **65 tests / 22 classes, all green**.
- `--no-build-cache` (or `cleanTest`) matters: a cached `:test` result is
  **not** evidence that the suite passed — force a fresh run.

The registry's `phase_verify` / `final_verification` for this repo is
`./gradlew test` (single module; one command runs feature + full suite).

---

## 10. Step 7 — Stop it

```bash
./scripts/demo.sh down          # stop + remove the stack, KEEP the Postgres volume
```

To also drop the database volume (destructive, opt-in — next `up` reseeds from scratch):

```bash
./scripts/demo.sh down -v       # or: ./scripts/demo.sh down --volumes
```

---

## 11. Troubleshooting

| Symptom | Cause | Fix |
|---------|-------|-----|
| **`error getting credentials … User canceled the operation`** (on `up`) | macOS `~/.docker/config.json` sets `"credsStore": "osxkeychain"`, which prompts for Keychain in a non-interactive shell and gets cancelled (all base images are public, so no credentials are needed) | **Non-destructive clean-`HOME` workaround** (your real `~/.docker/config.json` is untouched): `TMPHOME=$(mktemp -d); mkdir -p "$TMPHOME/.docker"; echo '{}' > "$TMPHOME/.docker/config.json"; env -u DOCKER_CONFIG HOME="$TMPHOME" ./scripts/demo.sh up` — or run from an interactive terminal and allow the prompt. Never edit your real `~/.docker/config.json` destructively. |
| **`docker: unknown command: docker compose`** | No Compose v2 plugin — only the standalone `docker-compose` binary is present | Nothing to change. `demo.sh` auto-detects and uses `docker-compose`. Verify with `docker-compose version`. |
| **Port already in use** (8081–8083 / 9091–9095 / Postgres) | Another process (or a previous stack) holds the port | Find it: `lsof -nP -iTCP:8081 -sTCP:LISTEN` (repeat for 8082/8083/9091–9095). Stop it, or edit the `ports:` mapping in `docker-compose.yml` and re-run `up`. Postgres maps no host port (internal only), so `5432` conflicts only if another project maps it. |
| **Podman instead of Docker** | `Podman Engine` emulates the Docker CLI/socket; compose binaries differ (`podman-compose` vs `docker-compose`) | Run Podman's machine so a `docker`/`docker-compose` shim resolves to a working engine; `demo.sh` auto-detects only `docker compose`/`docker-compose`, so make sure one of those points at Podman (e.g. Podman's docker-compose compatibility mode). |
| **Dashboard shows nothing / counters all zero** | Probes haven't run yet (interval up to ~5s), or the mocks didn't self-register | Wait one interval, then `./scripts/demo.sh status` (expect `total=5 up=5` in `dev`); confirm the mocks registered. If not, check `./scripts/demo.sh logs`. |
| **Wrong JDK / build fails** | Gradle ran under a different JDK (this host's default is Java 27) | Pin Java 25 for every Gradle invocation: `JAVA_HOME="$HOME/.sdkman/candidates/java/25.0.1-graalce" ./gradlew …`. |

---

## 12. Reference

### `demo.sh` sub-commands

| Command | What it does |
|---------|--------------|
| `./demo.sh` (or `up`) | Build if needed + start detached, wait until usable, print URLs |
| `./demo.sh status` | Container overview + matrix/counters per env |
| `./demo.sh fault [SERVICE] MODE` | POST a mock's `/__fault` — `MODE`: `up\|down\|degraded\|slow`; default mock `mock-payments` |
| `./demo.sh kill [INSTANCE]` | SIGKILL an instance (default `status-api-1`); show survivors |
| `./demo.sh demo` | Guided step-by-step story |
| `./demo.sh logs [SERVICE]` | Follow logs (default: the three app instances) |
| `./demo.sh down [-v\|--volumes]` | Stop + remove (add `-v` to also drop volumes) |
| `./demo.sh open [URL]` | Open the dashboard in the browser |
| `./demo.sh --help` | Usage + examples |

### `monitoring.*` tunables

Read from `application.yml`, overridable via `MONITORING_*` env vars (relaxed
binding). Four are bound to `MonitoringProperties`
(`@ConfigurationProperties(prefix = "monitoring")`); `claim-tick-ms` is **not** a
`MonitoringProperties` field — it is read directly by `ClaimLoop`'s
`@Scheduled(fixedDelayString = "${monitoring.claim-tick-ms:5000}")`.

| Property | Default | Meaning | Compose override |
|----------|---------|---------|------------------|
| `monitoring.check-interval` | `15s` | Schedule interval between a service's probes | `5s` |
| `monitoring.timeout` | `2s` | Per-probe HTTP timeout | — |
| `monitoring.max-in-flight` | `10` | Bounded concurrent probes per instance | — |
| `monitoring.batch-size` | `50` | Rows claimed per tick | — |
| `monitoring.claim-tick-ms` | `5000` | Claim-loop tick frequency (bound via `@Scheduled`, not `MonitoringProperties`) | `2000` |

### Where the docs live

- This repo: [`docs/architecture.md`](docs/architecture.md) (code map), the
  API docs at `/swagger-ui/index.html` and `/v3/api-docs`, and `/actuator/prometheus`.
- Hub repo (`samtex-interview`): `docs/features/001-service-status-dashboard/`
  — `prd.md`, `api-contract.md`, `adr.md`, `plan.md`.

---

## Remote

`git@github.com:caelcs/status-api.git`
