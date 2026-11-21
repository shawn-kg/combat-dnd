# Combat D&D

Live D&D 5e combat tracking for a remote group: the DM and each player on their own laptop, sharing one
initiative list. The DM sees everything; players see their own options and what their characters would know.

- Design doc: [docs/design.md](docs/design.md) (editable version: https://claude.ai/artifact/EvjGJzwvnNzExE1oMbCCAH)
- Clickable mockup: https://claude.ai/artifact/2YbcoLEwtTzEnpHRycCKpn

**Principle:** track, don't enforce. The app records what happened and shows hints. The DM rules.

## Layout

```
backend/    Kotlin, Spring Boot 4, Postgres (Flyway), Java 21
  src/main/kotlin/app/combatdnd/encounter/domain/   Combat rules: pure functions, no Spring
    Model.kt     combatants, resources, the pending attack
    Commands.kt  what an actor tries to do
    Events.kt    what happened (stored in encounter_event)
    Decide.kt    may this actor do this now? -> events or a rejection
    Evolve.kt    apply one event; state = events.fold(initial, ::evolve)
    View.kt      viewFor(): what one viewer may see (enemy numbers are never sent to players)
    Hints.kt     condition reminders before a roll
  src/main/resources/db/migration/V1__init.sql      Full schema
frontend/   Vite, React 19, TypeScript, React Router, TanStack Query
  src/styles/tokens.css     Design tokens (dark default, light theme)
  src/combat/               useEncounter (SSE + commands), InitiativeFeed
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

1. ~~Repo scaffold~~ (this commit)
2. ~~Combat rules core~~: attack flow, reactions (manual, automatic, never), send back, enemy reactions,
   standard actions, turn order, per-viewer filtering, hints. Still to add: undo, initiative phase,
   conditions and HP edits, add/reveal combatants, previous turn.
3. Encounter service and API: persistence of events, version checks (409), SSE broadcast, springdoc
   (then `scripts/gen-api.sh` generates the frontend types).
4. Combat screen: turn panel, composer, target list, interaction cards, DM controls.
5. Campaign, join and lobby, characters, monster templates, encounter prep, initiative phase.
6. Turn planning and reaction modes in the UI.
7. Deploy to Fly.io and run a test session.
