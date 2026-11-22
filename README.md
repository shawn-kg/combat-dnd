# Combat D&D

A live initiative tracker for a remote D&D group, like D&D Beyond's: the DM and each player on their own laptop,
sharing one list. Each row shows a portrait, initiative, AC, HP and what happened on that combatant's last turn.
The DM can edit everything; players log their own attacks and never see enemy AC or HP.

- Design doc: [docs/design.md](docs/design.md) (editable version: https://claude.ai/artifact/EvjGJzwvnNzExE1oMbCCAH)

## Layout

```
backend/    Kotlin, Spring Boot 4, Postgres (Flyway), Java 21
  src/main/kotlin/app/combatdnd/encounter/domain/   Tracker rules: pure functions, no Spring
    Model.kt     combatants, logged attacks, last turn
    Commands.kt  what an actor tries to do
    Events.kt    what happened (stored in encounter_event)
    Decide.kt    may this actor do this now? -> events or a rejection
    Evolve.kt    apply one event; state = events.fold(initial, ::evolve)
    View.kt      viewFor(): what one viewer may see (enemy AC and HP are never sent to players)
  src/main/resources/db/migration/V1__init.sql      Full schema
frontend/   Vite, React 19, TypeScript, React Router, TanStack Query
  src/styles/tokens.css     Design tokens (dark default, light theme)
  src/combat/               useEncounter (SSE + commands), Tracker rows
Dockerfile  frontend build -> jar with static files -> JRE image
fly.toml    One always-on machine (state and SSE live in memory)
```

## Run it locally

You need Java 21, Node 22 and Docker.

```bash
docker compose up -d
```

```bash
cd backend && ./gradlew bootRun
```

```bash
cd frontend && npm install && npm run dev
```

Open http://localhost:5173. Vite forwards `/api` to Spring on port 8080.

## Test

```bash
cd backend && ./gradlew test
```

```bash
cd frontend && npm run lint && npm run build
```

## Roadmap

1. ~~Repo scaffold~~
2. ~~Tracker rules~~: log, edit and delete attacks with HP kept in step, last-turn summaries, next and
   previous turn, DM edits to any field, add, remove, reveal, defeat, reorder, per-viewer filtering.
3. Tracker API: event persistence, version checks (409), undo, SSE broadcast, springdoc
   (then `scripts/gen-api.sh` generates the frontend types).
4. Tracker screen: rows, DM inline editing, Log attack dialog, turn log.
5. Campaign, join, lobby and choosing a character; characters and templates; portrait uploads.
6. Encounter prep.
7. Deploy to Fly.io and run a test session.
