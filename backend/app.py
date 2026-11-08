"""
OpenLive backend v0.1 — your own live-TV API.

Clean-room design, modelled only on the *shape* of a typical IPTV app
(topics -> channels -> stream URL). No Drama Live code, endpoints, keys
or content are used here. Channels are free-to-air public streams listed
by iptv-org (https://iptv-org.github.io/api/) — the backend only stores
their public URLs, it hosts no video itself.

Run on a PC:
    cd backend
    python -m venv .venv && .venv/bin/pip install -r requirements.txt
    python -m uvicorn app:app --host 0.0.0.0 --port 8000

Env vars (see .env.example):
    OPENLIVE_DB          SQLite file (default: openlive.db next to app.py)
    OPENLIVE_ADMIN_TOKEN admin token for POST /api/admin/* (default: change-me)
    OPENLIVE_SEED_CODE   demo activation code seeded on first run
                         (default: OPENLIVE-DEMO-2026)
"""
import json
import os
import sqlite3
from datetime import datetime, timezone
from pathlib import Path

from fastapi import FastAPI, Header, HTTPException, Query
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

BASE_DIR = Path(__file__).resolve().parent
DB_PATH = Path(os.environ.get("OPENLIVE_DB", BASE_DIR / "openlive.db"))
ADMIN_TOKEN = os.environ.get("OPENLIVE_ADMIN_TOKEN", "change-me")
SEED_CODE = os.environ.get("OPENLIVE_SEED_CODE", "OPENLIVE-DEMO-2026")
APP_VERSION = "0.1.0"

app = FastAPI(title="OpenLive API", version=APP_VERSION)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],  # v0.1: single-user demo server; restrict in production
    allow_methods=["*"],
    allow_headers=["*"],
)


# ---------------------------------------------------------------- models
class Channel(BaseModel):
    id: str
    name: str
    topic: str
    country: str | None = None
    language: str | None = None
    logo: str | None = None
    stream_url: str
    quality: str | None = None
    source: str | None = None
    iptv_org_id: str | None = None


class ActivateRequest(BaseModel):
    code: str = Field(min_length=1)


class AdminChannel(Channel):
    pass


class AdminCode(BaseModel):
    code: str = Field(min_length=1)
    label: str = ""


# ---------------------------------------------------------------- database
def _db() -> sqlite3.Connection:
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    return conn


def init_db() -> dict:
    """Create tables and seed channels + demo code on first run."""
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    conn = _db()
    conn.executescript(
        """
        CREATE TABLE IF NOT EXISTS channels (
            id TEXT PRIMARY KEY, name TEXT NOT NULL, topic TEXT NOT NULL,
            country TEXT, language TEXT, logo TEXT,
            stream_url TEXT NOT NULL, quality TEXT,
            source TEXT, iptv_org_id TEXT
        );
        CREATE TABLE IF NOT EXISTS codes (
            code TEXT PRIMARY KEY, label TEXT, used INTEGER DEFAULT 0,
            created_at TEXT
        );
        """
    )
    seeded = 0
    if conn.execute("SELECT COUNT(*) FROM channels").fetchone()[0] == 0:
        seed_file = BASE_DIR / "channels.json"
        channels = json.loads(seed_file.read_text(encoding="utf-8"))
        for c in channels:
            conn.execute(
                "INSERT OR IGNORE INTO channels VALUES (?,?,?,?,?,?,?,?,?,?)",
                (c["id"], c["name"], c["topic"], c.get("country"),
                 c.get("language"), c.get("logo"), c["stream_url"],
                 c.get("quality"), c.get("source"), c.get("iptv_org_id")),
            )
        seeded = len(channels)
    if conn.execute("SELECT COUNT(*) FROM codes").fetchone()[0] == 0:
        conn.execute(
            "INSERT OR IGNORE INTO codes VALUES (?,?,0,?)",
            (SEED_CODE, "demo (reusable)", datetime.now(timezone.utc).isoformat()),
        )
    conn.commit()
    count = conn.execute("SELECT COUNT(*) FROM channels").fetchone()[0]
    conn.close()
    return {"channels": count, "seeded_now": seeded}


def _row_to_channel(row: sqlite3.Row) -> dict:
    return {k: row[k] for k in row.keys()}


@app.on_event("startup")
def _startup():
    init_db()


# ---------------------------------------------------------------- endpoints
@app.get("/")
def root():
    return {
        "app": "OpenLive",
        "version": APP_VERSION,
        "docs": "/docs",
        "health": "/api/health",
        "note": "Own backend. Free public streams only — no pirated channels.",
    }


@app.get("/api/health")
def health():
    info = init_db()
    return {"status": "ok", "version": APP_VERSION, "channels": info["channels"]}


@app.get("/api/settings")
def settings():
    return {
        "app_name": "OpenLive",
        "version": APP_VERSION,
        "activation_required": True,
        "topics_endpoint": "/api/topics",
        "live_endpoint": "/api/live",
        "search_endpoint": "/api/search",
        "activate_endpoint": "/api/activate",
        "matches_endpoint": "/api/matches",
        "note": "Public streams can go offline without notice; "
                "re-import with scripts/import_iptv_org.py to refresh.",
    }


@app.get("/api/topics")
def topics():
    conn = _db()
    rows = conn.execute(
        "SELECT topic, COUNT(*) AS count FROM channels GROUP BY topic ORDER BY topic"
    ).fetchall()
    conn.close()
    return {"topics": [{"name": r["topic"], "count": r["count"]} for r in rows]}


@app.get("/api/live")
def live_list(
    topic: str | None = Query(default=None),
    q: str | None = Query(default=None),
    limit: int = Query(default=100, ge=1, le=500),
    offset: int = Query(default=0, ge=0),
):
    conn = _db()
    sql = "SELECT * FROM channels WHERE 1=1"
    params: list = []
    if topic:
        sql += " AND topic = ?"
        params.append(topic)
    if q:
        sql += " AND name LIKE ?"
        params.append(f"%{q}%")
    sql += " ORDER BY topic, name LIMIT ? OFFSET ?"
    params += [limit, offset]
    rows = conn.execute(sql, params).fetchall()
    total = conn.execute("SELECT COUNT(*) FROM channels").fetchone()[0]
    conn.close()
    return {"total": total, "returned": len(rows),
            "channels": [_row_to_channel(r) for r in rows]}


@app.get("/api/live/{channel_id}")
def live_one(channel_id: str):
    conn = _db()
    row = conn.execute("SELECT * FROM channels WHERE id = ?", (channel_id,)).fetchone()
    conn.close()
    if row is None:
        raise HTTPException(status_code=404, detail="channel not found")
    return _row_to_channel(row)


@app.get("/api/search")
def search(q: str = Query(default="", min_length=1)):
    conn = _db()
    rows = conn.execute(
        "SELECT * FROM channels WHERE name LIKE ? OR topic LIKE ? ORDER BY name",
        (f"%{q}%", f"%{q}%"),
    ).fetchall()
    conn.close()
    return {"q": q, "returned": len(rows),
            "channels": [_row_to_channel(r) for r in rows]}


@app.get("/api/matches")
def matches():
    # v0.1: no live match feed. Sports channels are in the Sports topic.
    return {"matches": [],
            "note": "No match feed in v0.1 — watch the Sports topic instead."}


@app.post("/api/activate")
def activate(body: ActivateRequest):
    code = body.code.strip()
    conn = _db()
    row = conn.execute("SELECT * FROM codes WHERE code = ?", (code,)).fetchone()
    if row is None:
        conn.close()
        return {"ok": False, "message": "Invalid code."}
    # The demo code is reusable; real codes are single-use.
    if row["label"] != "demo (reusable)" and row["used"]:
        conn.close()
        return {"ok": False, "message": "This code has already been used."}
    if row["label"] != "demo (reusable)":
        conn.execute("UPDATE codes SET used = 1 WHERE code = ?", (code,))
        conn.commit()
    conn.close()
    return {"ok": True, "message": "Activated. Welcome to OpenLive."}


# ---------------------------------------------------------------- admin
def _require_admin(x_admin_token: str | None):
    if not x_admin_token or x_admin_token != ADMIN_TOKEN:
        raise HTTPException(status_code=401, detail="bad admin token")


@app.post("/api/admin/channels")
def admin_add_channel(channel: AdminChannel, x_admin_token: str | None = Header(default=None)):
    _require_admin(x_admin_token)
    conn = _db()
    conn.execute(
        "INSERT OR REPLACE INTO channels VALUES (?,?,?,?,?,?,?,?,?,?)",
        (channel.id, channel.name, channel.topic, channel.country,
         channel.language, channel.logo, channel.stream_url,
         channel.quality, channel.source, channel.iptv_org_id),
    )
    conn.commit()
    conn.close()
    return {"ok": True, "id": channel.id}


@app.post("/api/admin/codes")
def admin_add_code(body: AdminCode, x_admin_token: str | None = Header(default=None)):
    _require_admin(x_admin_token)
    conn = _db()
    conn.execute(
        "INSERT OR REPLACE INTO codes VALUES (?,?,0,?)",
        (body.code, body.label, datetime.now(timezone.utc).isoformat()),
    )
    conn.commit()
    conn.close()
    return {"ok": True, "code": body.code}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=int(os.environ.get("PORT", "8000")))
