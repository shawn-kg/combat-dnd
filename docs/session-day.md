# Session day

A checklist for running a game with Combat D&D. The first time, give it ten extra minutes.

## Before the session (DM)

- [ ] **Make your DM sign-in link** if you haven't (campaign page → Your devices) and keep it somewhere private.
      It's how you get back in from another device or after clearing cookies.
- [ ] **Characters:** one per player, with AC, max HP and current HP. Add portraits if you like.
- [ ] **Monsters:** the ones you'll use, with AC and HP.
- [ ] **Encounters:** set up tonight's fights (who's in, which monsters, how many, hidden or not).
- [ ] **New players:** send the join link, let them in, and check they've each chosen their character.

## Five minutes before

- [ ] **Open the site** (free hosting sleeps when unused; the first load takes about a minute).
- [ ] Ask players to open the campaign page. Anyone who sees "Join the campaign first" opens the join link again.
- [ ] Want it beside D&D Beyond? Open the fight, then press **⧉ Pop out** for a small window, or just make
      the browser window narrow: the layout tightens up below about 520 px.

## Running a fight

1. **Start combat** on the encounter. Players get a **Join the fight** button.
2. **Initiative:** players send their own rolls; you type the rest, **Sort**, fix ties with ↑/↓,
   then **Begin round 1**.
3. On each turn, whoever's turn it is logs attacks (target, hit or miss, damage) and presses **End turn**.
   You can log for anyone, and fix anything: click a number to edit it, or use the Turn log.
4. Keyboard: **N** next turn, **U** undo.
5. Late arrival? **+ Add → Party member**. Surprise monsters? **+ Add → Enemies** (tick Hidden, reveal later).
6. **End combat…** when it's over. Everyone's HP is saved to their character for next time.

## Don't

- **Don't merge pull requests during a session.** A deploy restarts the server; everyone reconnects within a
  minute or two and nothing is lost, but it's a pause.

## If something goes wrong

- **"Reconnecting…"** at the top: wait a moment, or refresh. Nothing is lost.
- **A mistake during the fight:** the DM's **Undo** (U), or edit the number or Turn log entry.
- **A bigger mistake:** Neon can restore the whole database to any point in the last 6 hours, and there are
  nightly backups for 30 days (see docs/deploy.md).

## After the first session

Jot down what felt slow or confusing, and what you wished it did. That's the list for what to build next.
