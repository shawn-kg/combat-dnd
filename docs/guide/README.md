# Combat D&D, explained

A guided tour of the whole system, from the database up to the screen and out to the servers it runs on.
Read the chapters in order. Each one uses real excerpts from this repository, with a few small
**Try this** exercises to run on your own machine (`./scripts/dev.sh` running).

| # | Chapter | What you'll understand |
| --- | --- | --- |
| 0 | [The big picture](00-big-picture.md) | The pieces, where they run, the folder map, and one click followed end to end |
| 1 | Data: Postgres and migrations | Every table, how the schema changes safely, why secrets are hashed |
| 2 | The core: event sourcing | `decide`, `evolve`, `viewFor`, and why undo and testing are easy |
| 3 | The backend: Spring Boot layers | Controllers, services, repositories, transactions, locks, caches, 409s |
| 4 | Real-time: Server-Sent Events | How every screen updates, heartbeats, reconnects, why one server |
| 5 | Security | Cookie sign-in, hashed tokens, CSRF, rate limits, input checking |
| 6 | The frontend: React and TypeScript | Vite, routing, TanStack Query, `useEncounter`, components, CSS |
| 7 | Building and testing | Gradle, npm, the three kinds of tests, linting, the dev script |
| 8 | Infrastructure | Docker, Render, Neon, secrets, CI and deploys, backups, cold starts |
| 9 | Thinking like a systems engineer | The trade-offs, what breaks first at scale, and what's missing |

Chapters appear here as they're written.
