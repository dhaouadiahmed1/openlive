# OpenLive v0.1

Your own live-TV system — **backend + Android app** — built clean from scratch.

## What it is

- **Backend** (`backend/`, Python FastAPI + SQLite): serves your channel list
  — topics, channels, search, activation codes — over a simple JSON API.
- **Android app** (`android/`, Kotlin + Jetpack Compose, package `app.openlive`):
  Home (topic chips + channel list), Search, Favourites (stored on-device),
  Player (Media3 ExoPlayer, HLS, picture-in-picture), Settings (backend URL
  + activation code).

## What it is NOT

- **Not Drama Live.** No Drama Live code, package name, server, API key,
  ad SDK, or content is used here.
- **No pirated channels.** The only channels shipped are free-to-air public
  streams listed by [iptv-org](https://iptv-org.github.io/api/) — the
  backend stores only their public URLs and hosts no video itself.
  Public streams can go offline or be geo-blocked without notice; that is
  a property of the source, not a bug in OpenLive.

## Architecture (text diagram)

```
Android app (app.openlive, ExoPlayer)
        │  HTTPS/HTTP JSON
        ▼
OpenLive backend (FastAPI, :8000) ── SQLite openlive.db
        │  seeded from channels.json (30 verified public channels)
        │  or scripts/import_iptv_org.py (iptv-org, curated, capped)
        ▼
Public stream hosts (e.g. france24.com, dw.com, euronews, PBS…)
        — the phone plays the stream URL directly; video never
          passes through your backend.
```

## Quick start — backend on your PC

See [backend/README_BACKEND.md](backend/README_BACKEND.md) for the full steps.
Short version:

```bash
cd backend
python -m venv .venv
.venv/bin/pip install -r requirements.txt
python -m uvicorn app:app --host 0.0.0.0 --port 8000
# then: curl http://127.0.0.1:8000/api/health
# → {"status":"ok","version":"0.1.0","channels":30}
```

VPS / Docker:

```bash
cd backend
docker compose up -d --build
```

## Point the app at your backend

1. Build the APK (below) and install it.
2. Open **Settings** in the app.
3. Backend URL:
   - Emulator: `http://10.0.2.2:8000/`
   - Real phone, same Wi-Fi as your PC: `http://<your-PC-IP>:8000/`
   - VPS: `https://<your-domain>/`
4. Activation code: demo code **`OPENLIVE-DEMO-2026`** (reusable, for
   testing). Tap **Save & activate**.

## Add a channel manually

Option A — edit `backend/channels.json`, delete `backend/openlive.db`,
restart the backend (it re-seeds on first run).

Option B — admin API (needs your `OPENLIVE_ADMIN_TOKEN`):

```bash
curl -X POST http://127.0.0.1:8000/api/admin/channels \
  -H 'Content-Type: application/json' \
  -H 'X-Admin-Token: change-me' \
  -d '{"id":"my-channel","name":"My Channel","topic":"News",
       "country":"TN","language":"ar","logo":null,
       "stream_url":"https://example.com/live.m3u8","quality":"720p",
       "source":"manual","iptv_org_id":null}'
```

Add an activation code the same way:

```bash
curl -X POST http://127.0.0.1:8000/api/admin/codes \
  -H 'Content-Type: application/json' \
  -H 'X-Admin-Token: change-me' \
  -d '{"code":"MY-CODE-123","label":"friend"}'
```

Refresh the whole catalogue from iptv-org (curated, capped at 250):

```bash
cd backend
python scripts/import_iptv_org.py --max 250
# with liveness check (slower): python scripts/import_iptv_org.py --max 250 --check
```

## Build the APK (GitHub Actions — AHMED's usual flow)

1. Create the repo `dhaouadiahmed1/openlive` on GitHub (or push this folder).
2. Make sure `.github/workflows/android.yml` is in the repo.
   **Note:** Muse's GitHub App cannot create `.github/workflows/*.yml`
   files via the API (GitHub returns 403). If the push did not include
   it, add it yourself on GitHub web: repo → Add file → Create new file →
   path `.github/workflows/android.yml` → paste the file from this zip.
3. Push to `main` — the workflow builds the debug APK.
4. Actions → latest run → Artifacts → `openlive-debug-apk` → download,
   unzip, install `app-debug.apk` on your phone.

## Seeded channels (v0.1)

30 channels, 8 topics — all verified live on 2026-11-08
(HTTP 200 + `#EXTM3U`): News 18, Sports 3, Documentary 2, Music 2,
Shop 2, Entertainment 1, Education 1, Kids 1. Examples: France 24, DW,
Euronews (EN/FR/DE/ES/IT/PL), Sky News Arabia, ABC News Live, CBS News,
NBC News NOW, TRT World, WION, CNA, Rai News 24, Al Jazeera Mubasher,
NDTV 24x7, CGTN Documentary, Bloomberg Originals, Red Bull TV,
NBC Sports NOW, Trace Urban / Trace Africa, NHK World Premium, PBS,
PBS Kids, QVC US/UK.

Excluded after testing (403/404/timeout on 2026-11-08, so not seeded):
Al Jazeera (main feed), Al Jazeera Documentary, TV5Monde, arte,
Bloomberg TV, Documentary+, Fashion TV.

## Limitations (v0.1)

- Public streams can go offline, change URL, or be geo-blocked — re-run
  the import script to refresh.
- No downloads, no Chromecast, no EPG, no live match feed
  (`GET /api/matches` returns an empty list; sports channels are in
  the Sports topic).
- No ads. No analytics. No Drama Live servers.
- Backend CORS is open (`*`) in v0.1 — restrict it before exposing the
  server to the internet, and set a real `OPENLIVE_ADMIN_TOKEN`
  (see `backend/.env.example`).

## Files

```
openlive/
├── README.md                  this file
├── backend/                   FastAPI backend
│   ├── app.py                 API + SQLite + activation codes
│   ├── channels.json          30 seeded public channels
│   ├── requirements.txt
│   ├── .env.example
│   ├── Dockerfile / docker-compose.yml
│   ├── README_BACKEND.md      exact PC / VPS steps
│   └── scripts/import_iptv_org.py
├── android/                   Kotlin + Compose app (app.openlive)
└── .github/workflows/android.yml
```
