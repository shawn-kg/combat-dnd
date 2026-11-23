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
  src/main/kotlin/app/combatdnd/encounter/app/      EncounterService (locks, cache, undo), event log, SSE hub
  src/main/kotlin/app/combatdnd/encounter/api/      REST controller and JSON shapes
  src/main/kotlin/app/combatdnd/dev/                Dev-only seed data
  src/main/resources/db/migration/V1__init.sql      Full schema
frontend/   Vite, React 19, TypeScript, React Router, TanStack Query
  src/styles/tokens.css     Design tokens (dark default, light theme)
  src/combat/               useEncounter (SSE + commands), Tracker rows
Dockerfile  frontend build -> jar with static files -> JRE image
fly.toml    One always-on machine (state and SSE live in memory)
```

## Run it locally

You need Java 21, Node 22 and Docker. On WSL the script opens Docker Desktop for you if it isn't running.
Then, from the repo folder:

```bash
./scripts/dev.sh
```

It checks your tools (and says how to fix anything missing), starts Postgres, installs frontend packages when
needed, and runs the backend and frontend together in one terminal, with each line labelled `[api]` or `[web]`.
When it prints **Ready**, open the two links it shows: one DM tab and one player tab, which update live.
**Ctrl+C** stops everything, including Postgres (your data is kept).

- `./scripts/dev.sh --keep-db` leaves Postgres running after Ctrl+C.
- `./scripts/dev.sh --no-docker` uses a Postgres you run yourself on port 5432 (database `combat_dnd`,
  user `combat`, password `combat`).

To try campaigns, open http://localhost:5173, start a campaign as the DM, and open its join link in a private
window (or another browser) to join as a player. Each browser keeps its own sign-in.

The backend runs with the **dev** profile, which also seeds a goblin fight mid-combat that you can open with
`?as=dm` or `?as=player:<id>` (the two links the script prints) until encounters arrive in milestone 4.

<details>
<summary>Running the pieces by hand</summary>

```bash
docker compose up -d
```

```bash
cd backend && ./gradlew bootRun
```

```bash
cd frontend && npm install && npm run dev
```

Vite (port 5173) forwards `/api` to Spring on port 8080.

</details>

## Test

```bash
cd backend && ./gradlew test
```

The API tests start the real server against Postgres, so they run only when asked (CI always runs them):

```bash
docker compose up -d && cd backend && RUN_DB_TESTS=true ./gradlew test
```

```bash
cd frontend && npm run lint && npm test && npm run build
```

## Roadmap

1. ~~Repo scaffold~~
2. ~~Tracker rules~~: log, edit and delete attacks with HP kept in step, last-turn summaries, next and
   previous turn, DM edits to any field, add, remove, reveal, defeat, reorder, per-viewer filtering.
3. ~~Tracker API~~: event log in Postgres, commands with version checks (409), undo, live updates over SSE,
   per-viewer filtering, the CSRF header check, dev seed data and `?as=` sign-in.
4. ~~Tracker screen~~: players log attacks and end their turn from one panel; the DM edits any field inline,
   logs for anyone, reveals, defeats, removes, adds enemies (as one undoable action), fixes entries in the
   turn log, and has Previous, Next and Undo (N and U keys).
5. Campaigns and people: ~~start a campaign, join link, DM approval, choosing a character once, the DM's
   characters, players setting their own HP, sign-in cookies~~ (3a); ~~monster templates and portrait uploads~~ (3b).
   Portraits are saved under `backend/data/portraits` in development.
6. Encounter prep.
7. Deploy to Fly.io and run a test session.
