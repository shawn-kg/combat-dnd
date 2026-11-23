# Combat D&D Design Doc

> Snapshot of the living design doc as of 2026-11-22 (prep, initiative and per-attack last turn added). The editable version, with comments, is at
> https://claude.ai/artifact/EvjGJzwvnNzExE1oMbCCAH. Update this file when the design changes.

## Overview

Combat D&D is a live initiative tracker for our remote group: one DM and about 8 players, each on their own laptop. It works like D&D Beyond's encounter tracker. Each combatant is one row with a portrait, initiative, AC, HP out of max, and what happened on their last turn (hit or miss, and damage).

- **The DM runs it** and can edit everything: combatants, portraits, initiative, AC, HP, and any logged turn.
- **Players see the tracker** and log their own turns: target, hit or miss, damage. The DM can correct any entry.
- **Players see less than the DM.** Enemy HP and AC stay hidden, and so do enemies the DM hasn't revealed.
- **Characters carry over** between sessions, and the DM can prepare encounters ahead of time.

Players roll physical dice and type in totals. The app never rolls for anyone.

This replaces an earlier, much larger design with a full attack flow, reactions, conditions and turn planning. That design is in the doc's version history and in this file's git history.

## Principles

1. **The DM can edit everything.** Any number, any portrait, any logged turn. Nothing a player enters is final until the DM leaves it alone.
2. **Players see only what they should, enforced on the server.** Players never receive enemy HP, AC or hidden enemies. The server builds a filtered view for each viewer, so nothing is hidden only in the UI.
3. **Every change is an event.** Changes go into an event log. Undo and the last-turn summaries come from it.
4. **Keep it a tracker.** The app records what happened. It doesn't model action economy, conditions or reactions; the table handles those.

## Scope

| Area | Version 1 | Later |
| --- | --- | --- |
| Campaign | Join link, DM approves players, each player chooses their character once | Real accounts |
| Characters | Created by the DM: name, portrait, AC, max HP, current HP; HP carries over | Players editing their own sheet |
| Monsters | Templates with name, portrait, AC, max HP | Import from the free 5e SRD |
| Encounters | Prep ahead, add enemies from templates, hidden enemies, reveal | |
| Combat | Initiative order, current turn, next and previous turn, round counter | |
| Turns | Log attacks (target, hit or miss, damage); damage comes off the target's HP; last-turn summary per row | Healing entries, multi-target spells |
| DM | Edit any field or log entry, undo, reorder, add, remove, reveal | |

Removed from the earlier design: the DM-confirmed attack flow, reactions, send back, advantage and natural 20 toggles, action and bonus tracking, conditions, hints and turn planning.

## User flows

### Joining and choosing a character

1. The DM creates the campaign and its characters, then shares the join link (e.g. `/join/K7QX2`).
2. A player opens the link, types their name and waits in a lobby.
3. The DM approves them.
4. The player picks their character from the ones nobody has claimed yet. This happens once: from then on that character is theirs, and their row shows a "You" tag.
5. On a new laptop, the player opens the link, picks "I'm returning as…", and the DM approves again. Their character comes with them.

The DM can unassign a character (if someone picked the wrong one), remove a player, or regenerate the join code.

### Portraits

The DM uploads a picture for each character and monster template. It's cropped to a square and shown as a circle. Without a picture, the row shows the name's initials on a party or enemy colored background.

### Prep before the session

Everything the DM sets up is saved, so starting a fight takes seconds.

- **Party:** the characters persist between sessions. The DM adds characters (each shows "Not claimed yet" until a player picks it) and sets their portrait, AC and max HP. HP carries over from the last fight, and each player can set their own current HP at any time, between fights after a rest or during one, so the DM doesn't have to.
- **Saved encounters:** the DM can keep several ready, for example "Goblin Ambush" and "Wolves at the Bridge". Each one lists which party members are in it (untick anyone who isn't playing tonight) and its enemies from templates, with a count and a hidden option ("Goblin × 2", "Goblin Archer × 1, hidden").
- Changes save automatically. Nothing needs to be re-entered at the table.

### Starting combat

1. The DM opens a saved encounter and presses **Start combat**. The roster becomes a list of combatants; repeated enemies are numbered (Goblin 1, Goblin 2).
2. **Initiative:** the DM types everyone's roll, presses **Sort by initiative**, and uses the up and down arrows to break ties. Players see "Rolling initiative" until then.
3. **Begin round 1** puts the list in that order and highlights the first combatant who isn't hidden.

### Running combat

1. The current turn's row is highlighted for everyone. **Next turn** and **Previous turn** move it; the round counts up on wrap-around.
2. During the fight the DM can add enemies, reveal hidden ones, remove or mark defeated, and edit any field inline.
3. **End combat** writes the party's HP back to their characters and returns to prep.

### Logging a turn

On their turn, a player presses **Log attack** on their row:

1. Pick the target from a list (enemies first).
2. Choose **Hit** or **Miss**.
3. On a hit, type the damage. It comes off the target's HP right away.

They can log several attacks in one turn and remove one they got wrong. **End turn** sits in the same panel as **Log**; if an attack is filled in but not logged yet, ending the turn logs it first.

The row's last-turn line lists every attack from that turn, plus a total when there's more than one, for example "Hit Thorin · 6", "Hit Thorin · 7", "Total 13". The DM logs enemy turns the same way: on every turn, Log attack and End turn sit on the current row. The DM can edit or delete any entry, and the target's HP is corrected to match.

## Data model

Postgres holds everything except image files, which go to object storage. A fight lives entirely in its event log: there is no combatant table, so the tracker can never drift from the log. The server caches each active encounter in memory and rebuilds it from its events after a restart or an undo. One command writes one `batch` of events, and undo marks the latest batch undone. The schema is in `backend/src/main/resources/db/migration/V1__init.sql`.

| Table | Key columns | Notes |
| --- | --- | --- |
| `campaign` | name, join_code, dm_token_hash | One per group |
| `player` | campaign_id, display_name, token_hash, status (PENDING, APPROVED, REMOVED) | No passwords |
| `character` | campaign_id, player_id (null until claimed), name, portrait_key, ac, max_hp, current_hp | Created by the DM; a player claims one; HP carries over |
| `monster_template` | campaign_id, name, portrait_key, ac, max_hp | Reused across encounters |
| `encounter` | name, status (PREPARING, ROLLING_INITIATIVE, ACTIVE, ENDED), round, current_combatant_id, version | `version` guards against conflicting edits |
| `encounter_event` | encounter_id, seq, batch, actor, type, payload, undone, created_at | Append-only; the fight itself: combatants added, attacks, edits, turn changes |

An encounter's saved roster lives in two more tables: `encounter_party` (encounter_id, character_id, included) and `encounter_enemy` (encounter_id, monster_template_id, count, hidden). **Start combat** turns the roster into `CombatantAdded` events.

`portrait_key` names a file in object storage. A player's character is set once, when `character.player_id` is filled; only the DM can change it after that.

A combatant's **last turn** is not stored as a column. It is every `AttackLogged` event that combatant made during its most recent turn, minus any deleted ones. That way editing or undoing an attack updates the summary and the HP together.

### What each viewer receives

| Data | DM | Player |
| --- | --- | --- |
| Portrait, name, initiative, whose turn, round | Yes | Yes |
| Party AC and HP | Yes | Yes |
| Enemy AC and HP | Yes | Never sent |
| Last-turn summary (hit or miss, damage) | Yes | Yes |
| Hidden enemies | Yes | Not until revealed |
| Defeated enemies | Yes | Shown greyed out |
| Edit controls | Everything | Log attacks on their own turn; set their own HP any time |

## Backend

The backend is Kotlin on Spring Boot 4 (Spring MVC, Java 21 virtual threads), with Spring JDBC and Flyway on Postgres. Commands come in over REST, and updates go out over Server-Sent Events (SSE).

### Tracker rules

```kotlin
fun decide(state: EncounterState, cmd: Command, actor: Actor): Decision
fun evolve(state: EncounterState, event: Event): EncounterState
fun viewFor(state: EncounterState, viewer: Actor): EncounterView
```

- `decide` checks permissions only: the DM may do anything; a player may log attacks for their own character during its turn, and end their own turn. A player may also set their own character's current HP at any time, but not their AC, max HP or anyone else's numbers.
- Current state is `events.fold(initial, ::evolve)`. Undo marks the last event undone and replays.
- Commands: `LogAttack` (target, hit, damage), `EditAttack`, `DeleteAttack`, `NextTurn`, `PreviousTurn`, `AddCombatant`, `AddCombatants` (several as one undoable action), `RemoveCombatant`, `RevealCombatant`, `SetDefeated`, `EditCombatant` (name, portrait, initiative, AC, HP, max HP), `Reorder`, `Undo`.
- `LogAttack` with a hit takes the damage off the target's HP, never below 0. `EditAttack` and `DeleteAttack` apply the difference, so HP always matches the log.
- Party HP is written back to `character.current_hp`, so it carries over to the next fight.

Starting and ending a fight: `StartCombat` (builds combatants from the roster, status becomes ROLLING_INITIATIVE), `SetInitiative`, `BeginRound1` (ACTIVE), and `EndCombat` (writes party HP back, status becomes ENDED). A player sets their own HP with `PATCH /api/characters/{cid}/hp`, between fights; during a fight they edit the HP on their own row. Roster edits during prep are plain CRUD: `PUT /api/encounters/{id}/roster`.

### API

| Method and path | Purpose |
| --- | --- |
| `POST /api/campaigns` | Create a campaign; sets the DM cookie |
| `GET` and `POST /api/join/{code}` | The join page: campaign name and returning players, then join or rejoin (rate limited) |
| `GET /api/campaigns/{id}`, `GET /api/me/campaigns` | The campaign page for the DM or a player, refreshed every 3 seconds so the lobby updates by itself; the browser's campaigns |
| `POST /api/campaigns/{id}/players/{pid}/approve`, `/deny`, `/remove` | DM manages players |
| `POST /api/campaigns/{id}/characters/{cid}/claim` | A player chooses their character (once) |
| `GET, POST, PUT, DELETE /api/campaigns/{id}/characters` and `/monster-templates` | DM CRUD |
| `POST /api/campaigns/{id}/images` | DM uploads a portrait; returns its key |
| `POST /api/campaigns/{id}/encounters` | Prep |
| `POST /api/encounters/{id}/commands` | Every tracker command, with `expectedVersion` |
| `GET /api/encounters/{id}/stream` | SSE: this viewer's filtered tracker after every change |

### Request lifecycle

1. Resolve the actor from the cookie.
2. Lock the encounter (one in-memory lock per encounter).
3. Reject with 409 if `expectedVersion` is stale.
4. Run `decide`; reject with 403 and a reason.
5. In one transaction, insert the events, bump the version and write party HP.
6. Fold the events into the cached state, unlock, then send each viewer their filtered snapshot.

Image uploads are limited to 5 MB, JPEG, PNG or WebP, and resized to 256 × 256 on the server before storing. Portraits are served through short-lived signed URLs.

## Frontend

One React app (Vite, TypeScript, React Router, TanStack Query), served by Spring as static files.

### Routes

- `/join/:code`: join or rejoin, the lobby, then choosing a character.
- `/c/:campaignId`: campaign home. The DM manages players, characters, templates, portraits and encounters.
- `/c/:campaignId/encounters/:id`: encounter prep (DM).
- `/combat/:id`: the tracker, rendered by role.

### Tracker components

- `TrackerHeader`: encounter name, round, whose turn; DM gets Previous, Next, Undo, Add enemy.
- `TrackerRow`: portrait, initiative, name, AC, HP bar with numbers, last-turn line. The DM's row has inline editing on every field and a menu with Reveal, Mark defeated and Remove.
- `LogAttackDialog`: target list, Hit or Miss, damage. Opens from your own row on your turn, and from any row for the DM.
- `TurnLog` (DM): the current and previous turns' entries, each with Edit and Delete.

The screen renders from the latest snapshot the server pushed; there are no optimistic updates.

## Visual design

The tracker is one list of rows in initiative order, like D&D Beyond. Dark is the default, with a light theme. The fantasy shows only in the type (Cinzel for names and headings) and a gold highlight on the current turn.

```
┌────────────────────────────────────────────────────────────────────┐
│ 21  (◯) Goblin Boss        AC 15   22/30 ▓▓▓▓▓░░   Hit Thorin · 6 │  enemy: red edge
┃ 15  (◯) Lyra  YOU  ▸TURN  AC 15   22/27 ▓▓▓▓▓▓░   2 hits · 17    ┃  current: gold
│ 12  (◯) Goblin 1           —        —               Miss            │  player view of an enemy
└────────────────────────────────────────────────────────────────────┘
```

- **Portrait**: a 48 px circle, with a party (teal) or enemy (red) ring. Initials when there's no picture.
- **Side**: a colored left edge plus the ring color, never color alone; party and enemies keep a shield or skull icon.
- **HP**: numbers plus a bar that turns amber below 50% and red below 25%. Players see a dash for enemy AC and HP.
- **Last turn**: one line per attack ("Hit Goblin 2 · 9", "Miss Goblin 1"), with a total when there are several. Hits in green, misses muted, damage in red.
- **Current turn**: gold border and glow; the list scrolls to it on each turn change.
- **Defeated**: greyed out with a strikethrough. **Hidden** (DM only): dashed border and an eye-off icon.
- The color tokens from the earlier design stay the same (`frontend/src/styles/tokens.css`).

## Infrastructure

One Docker image on one Fly.io machine, Neon Postgres, and Cloudflare R2 for portraits, for an estimated $5–7 a month. Prices are approximate; check current pricing before committing.

| Piece | Choice | Approximate cost |
| --- | --- | --- |
| App | Fly.io, 1 shared-CPU machine with 1 GB RAM | $5–7/month |
| Database | Neon serverless Postgres | Free tier |
| Portraits | Cloudflare R2 (S3-compatible), 256 × 256 images of a few KB each | Free tier |
| Domain | `combat-dnd.fly.dev` with TLS included | $0 |
| Backups | Nightly `pg_dump` to R2, kept 30 days | Free tier |

- **Build:** a three-stage Dockerfile (Node builds the frontend, Gradle builds the jar with it inside, a Java 21 JRE runs it). Flyway migrates on startup.
- **CI:** GitHub Actions runs backend tests and frontend lint and build on every push and pull request. Deploy on merge to main is ready in `ci.yml`, commented out until the Fly app exists.
- **One machine, always running,** because the encounter cache and SSE connections live in memory. Don't deploy mid-session.
- **Locally:** `docker compose up` starts Postgres, portraits are saved to a local folder instead of R2, then `./gradlew bootRun` and `npm run dev`.
- **Security:** tokens are 32 random bytes, stored hashed, in `HttpOnly; Secure; SameSite=Lax` cookies; POSTs require an `X-Requested-With` header; the join endpoint is rate limited; R2 credentials live in `fly secrets`.

## Roadmap and open questions

1. **Rework the repo for the tracker.** Replace the attack-flow rules with the tracker rules and tests, and update the schema. In progress.
2. Tracker API: persistence, version checks, SSE.
3. Tracker screen: rows, DM inline editing, Log attack, turn log.
4. Campaign, join, lobby and choosing a character; characters and templates; portrait uploads.
5. Encounter prep.
6. Deploy to Fly.io and run a test session with the group.

Open questions:

- [ ] Should players see a health band for enemies (Healthy, Bloodied, Down) instead of nothing?
- [ ] Should healing be logged like attacks, so the last-turn line can say "Healed Bram · 7"?
- [ ] Should players type their own initiative at the start of combat, or is DM entry enough?
