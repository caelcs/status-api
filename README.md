# status-api

Service-status dashboard backend.

## Architecture

New to the codebase? Start with [`docs/architecture.md`](docs/architecture.md) — the component map, runtime flows, read order, and test map for the distributed claim-and-advance monitor and SSE dashboard.

## Demo — watch the whole system live

`scripts/demo.sh` spins up the full docker-compose sandbox (Postgres + 3 `status-api`
instances + the 5 self-registering mock services) and lets you watch the dashboard
work in real time. One-liner to start:

```bash
./scripts/demo.sh        # build if needed, start detached, wait until usable, print the URLs
./scripts/demo.sh open   # open the dashboard in your browser
```

Sub-commands: `up` (default), `status` (matrix + counters), `fault [SERVICE] MODE`
(default service `mock-payments`, so `fault down` just works), `kill [INSTANCE]`
(rebalance-on-death demo), `demo` (guided end-to-end story), `logs [SERVICE]`,
`down [-v|--volumes]`, `open [URL]`. Run `./scripts/demo.sh --help` for details.

The dashboard is served by each app instance at `http://localhost:8081/`
(also `:8082`, `:8083`). The mock services are fault-injectable from the host at
`localhost:9091`–`9095` (`mock-auth` … `mock-ai`).

> Bootstrap placeholder. The Gradle skeleton, application code, and test suite are scaffolded in a later pipeline stage (the `status-api` task pack, prefix `S`). This repository is currently empty by design.

## Remote

`git@github.com:caelcs/status-api.git`
