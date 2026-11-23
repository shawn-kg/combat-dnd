# Deploying Combat D&D (free)

The app runs as one free **Render** web service, with a free **Neon** Postgres database. Portraits are stored
in the database, so there's nothing else to set up. Nightly backups run in GitHub Actions.

You need: this GitHub repository, and free accounts on [Neon](https://neon.com) and [Render](https://render.com)
(signing up with GitHub is easiest). It takes about 15 minutes.

## 1. Create the database (Neon)

1. Sign in to Neon and create a project, e.g. `combat-dnd`.
   - **Postgres version:** whatever Neon suggests (17 or 18 both work).
   - **Region:** AWS US East 2 (Ohio). It should match the Render region in `render.yaml` (`ohio`), so the app and
     database are close together. If your group is elsewhere, pick a Neon region near you and change `region:` in
     `render.yaml` to the nearest Render region.
2. On the project dashboard, press **Connect**. Copy the connection string. It looks like
   `postgresql://neondb_owner:...@ep-something.us-east-2.aws.neon.tech/neondb?sslmode=require`.
   - Use the normal (not "pooled") connection string.
   - Keep it secret: it contains the password.

The app creates its tables on first start, so there's nothing to run in Neon.

## 2. Create the web service (Render)

1. Sign in to Render and connect your GitHub account, giving it access to the `combat-dnd` repository.
2. Choose **New → Blueprint**, pick the `combat-dnd` repository, and Render reads `render.yaml`.
3. It asks for **DATABASE_URL**: paste the Neon connection string exactly as you copied it.
4. Press **Deploy Blueprint**. The first build takes 5–10 minutes (it builds the frontend and backend).
5. When it says **Live**, open the URL Render shows (something like `https://combat-dnd.onrender.com`).

From now on, every merge to `main` deploys by itself once CI passes. Don't merge during a session: a deploy
restarts the server (everyone reconnects within a minute or two, and nothing is lost, but it's a pause).

## 3. Turn on backups (GitHub)

1. In the GitHub repository: **Settings → Secrets and variables → Actions → New repository secret**.
2. Name: `DATABASE_URL`. Value: the same Neon connection string.
3. Go to **Actions → Backup → Run workflow** to try it. The run should end with a `combat-dnd-…` artifact.

It then runs every night and keeps 30 days of backups. Neon can also restore the database to any point in
the last 6 hours (Neon dashboard → **Restore**), which is the quickest fix for a mistake made during a session.

### Restoring a backup

Download the artifact from the Backup run you want, unzip it, then:

```bash
docker run --rm -v "$PWD":/d postgres:18 pg_restore --clean --if-exists --no-owner -d "<Neon connection string>" /d/combat-dnd.dump
```

## 4. Start playing

1. Open the site and **start a campaign**: that browser becomes the DM.
2. Press **Make my DM sign-in link** and save the link somewhere private (a password manager or a private
   note). Open it on your other devices to be the DM there too. Without it, clearing this browser's cookies
   would lock you out of your own campaign.
3. Send the join link to your players.

## Things to know about the free plan

- **It sleeps.** After 15 minutes with nobody using it, the server sleeps, and the next visit takes about a
  minute to load. Open the site a minute or two before the session starts. While anyone has a fight open on
  screen, it stays awake.
- **Nothing is lost when it sleeps or restarts.** Fights are rebuilt from the database.
- **Limits are generous for one group:** Neon's free database is 1 GB (this app uses a few MB) with 100
  compute-hours a month, and it sleeps when unused.
- **If the sleeping gets annoying,** Render's paid Starter plan keeps it always on. Change `plan: free` to
  `plan: starter` in `render.yaml`.

## Troubleshooting

- **Deploy fails at startup with a database error:** check `DATABASE_URL` in Render (**Environment** tab)
  matches Neon's connection string, including `?sslmode=require`.
- **"Bad Gateway" or a slow first load:** it's waking up; wait a minute and refresh.
- **Logs:** Render dashboard → the service → **Logs**.
