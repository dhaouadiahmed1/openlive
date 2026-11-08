# OpenLive backend — run it

## On your PC (Windows / Linux / Mac)

```bash
cd backend
python -m venv .venv
# Windows: .venv\Scripts\activate
# Linux/Mac: source .venv/bin/activate
pip install -r requirements.txt
python -m uvicorn app:app --host 0.0.0.0 --port 8000
```

Open http://127.0.0.1:8000/docs for the interactive API docs.

### Test it (in a second terminal)

```bash
curl http://127.0.0.1:8000/api/health
# {"status":"ok","version":"0.1.0","channels":30}

curl http://127.0.0.1:8000/api/topics
curl "http://127.0.0.1:8000/api/live?topic=News&limit=3"
curl http://127.0.0.1:8000/api/live/france24
curl "http://127.0.0.1:8000/api/search?q=news"
curl -X POST http://127.0.0.1:8000/api/activate \
  -H 'Content-Type: application/json' \
  -d '{"code":"OPENLIVE-DEMO-2026"}'
# {"ok":true,"message":"Activated. Welcome to OpenLive."}
```

Tested 2026-11-08 on this machine: all of the above returned 200,
30 channels / 8 topics, demo code accepted, wrong code rejected,
single-use codes work (second use rejected), unknown channel → 404.

### Your phone on the same Wi-Fi

Find your PC's LAN IP (`ipconfig` on Windows, `ip a` on Linux) and use
`http://<PC-IP>:8000/` as the backend URL in the app's Settings.
The backend listens on `0.0.0.0`, so it accepts LAN connections.
(If Windows Firewall blocks it, allow Python / port 8000.)

## On a VPS (Docker)

```bash
cd backend
# Set a real admin token first:
export OPENLIVE_ADMIN_TOKEN='a-long-random-token-here'
docker compose up -d --build
curl http://127.0.0.1:8000/api/health
```

The SQLite database lives in the `openlive-data` Docker volume, so
channels and codes survive restarts.

## On Render / Railway (free tier)

- Create a new Web Service from this repo, root directory `backend`.
- Build command: `pip install -r requirements.txt`
- Start command: `python -m uvicorn app:app --host 0.0.0.0 --port $PORT`
- Env vars: `OPENLIVE_ADMIN_TOKEN` (set a long random value),
  `OPENLIVE_SEED_CODE` (optional, default `OPENLIVE-DEMO-2026`).
- Note: free tiers may wipe the SQLite file on redeploy — keep
  `channels.json` as the source of truth and re-seed by redeploying.

## Configuration

Copy `.env.example` to `.env` and edit it (never commit a real `.env`):

| Variable | Default | Meaning |
|---|---|---|
| `OPENLIVE_ADMIN_TOKEN` | `change-me` | Token for `POST /api/admin/*` (`X-Admin-Token` header) |
| `OPENLIVE_SEED_CODE` | `OPENLIVE-DEMO-2026` | Demo activation code, seeded on first run, reusable |
| `OPENLIVE_DB` | `openlive.db` | SQLite file |
| `PORT` | `8000` | Port |

## Endpoints

| Method | Path | Description |
|---|---|---|
| GET | `/` | App info |
| GET | `/api/health` | `{"status":"ok","channels":30}` |
| GET | `/api/settings` | App name, version, endpoint list |
| GET | `/api/topics` | Topics with channel counts |
| GET | `/api/live?topic=&q=&limit=&offset=` | Channel list |
| GET | `/api/live/{id}` | One channel (404 if unknown) |
| GET | `/api/search?q=` | Search by name/topic |
| GET | `/api/matches` | Empty in v0.1 (no match feed) |
| POST | `/api/activate` | `{"code":"…"}` → `{"ok":true/false}` |
| POST | `/api/admin/channels` | Add/replace a channel (admin token) |
| POST | `/api/admin/codes` | Add an activation code (admin token) |

## Refresh channels from iptv-org

```bash
cd backend
python scripts/import_iptv_org.py --max 250
# slower, keeps only streams that answer HTTP 200 + #EXTM3U:
python scripts/import_iptv_org.py --max 250 --check
# then: delete openlive.db and restart the backend to re-seed
```

Source (verified 2026-11-08): `https://iptv-org.github.io/api/channels.json`
(31,758 channels) and `.../api/streams.json` (18,637 streams), joined on
`streams.channel == channels.id`. Categories come from the channel,
URLs from the stream. Only `https://…*.m3u8` streams in the topics
News / Entertainment / Movies / Music / Sports / Documentary / Kids /
Education are imported, NSFW/closed channels are skipped.
