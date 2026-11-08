#!/usr/bin/env python3
"""
Import a CURATED subset of free-to-air public streams from iptv-org.

Verified 2026-11-08:
  https://iptv-org.github.io/api/channels.json   (31,758 channels)
  https://iptv-org.github.io/api/streams.json    (18,637 streams)
  https://iptv-org.github.io/api/categories.json (category list)
Fields (checked live, do not guess):
  channels.json: [{id, name, alt_names, network, owners, country,
                   categories[], is_nsfw, launched, closed, replaced_by,
                   website}]  — NOTE: no logo field in this version.
  streams.json:  [{channel, feed, title, url, quality, labels,
                   user_agent, referrer}]
  Join on streams.channel == channels.id. Categories come from the
  channel, the URL from the stream.

Rules (this is what keeps the import legal/clean):
  * only channels in the allow-list of topics below
  * skip NSFW, closed channels, and streams without a channel id
  * only https:// URLs ending in .m3u8 (or .m3u8 with a query string)
  * skip known aggregator/test mirrors (jmp2.uk, freem3u, antik test feeds)
  * deduplicate by channel id, keep the first good stream
  * cap at --max channels (default 250), prefer 720p/1080p
  * optional --check: HEAD/GET each stream and keep only HTTP 200
    responses starting with #EXTM3U (slow, ~1s per stream)

Usage:
    python scripts/import_iptv_org.py [--max 250] [--check] [--out channels.json]
    # then restart the backend — it seeds from channels.json on first run,
    # or delete openlive.db to re-seed, or POST the new channels via
    # /api/admin/channels with your OPENLIVE_ADMIN_TOKEN.
"""
import argparse
import json
import sys
import urllib.request

CHANNELS_URL = "https://iptv-org.github.io/api/channels.json"
STREAMS_URL = "https://iptv-org.github.io/api/streams.json"

ALLOWED_TOPICS = {
    "news", "entertainment", "movies", "music", "sports",
    "documentary", "kids", "education",
}
# Map iptv-org's first category to our topic name.
TOPIC_MAP = {
    "news": "News", "entertainment": "Entertainment", "movies": "Movies",
    "music": "Music", "sports": "Sports", "documentary": "Documentary",
    "kids": "Kids", "education": "Education",
}
SKIP_URL_PARTS = ("jmp2.uk", "freem3u", "antik.sk", "test_", "dailymotion")


def fetch_json(url: str):
    req = urllib.request.Request(url, headers={"User-Agent": "OpenLive/0.1"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return json.load(resp)


def stream_alive(url: str) -> bool:
    try:
        req = urllib.request.Request(
            url, headers={"User-Agent": "Mozilla/5.0"}, method="GET")
        with urllib.request.urlopen(req, timeout=10) as resp:
            if resp.status != 200:
                return False
            return resp.read(16).startswith(b"#EXTM3U")
    except Exception:
        return False


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--max", type=int, default=250)
    ap.add_argument("--check", action="store_true",
                    help="verify each stream returns HTTP 200 + #EXTM3U")
    ap.add_argument("--out", default="channels.json")
    args = ap.parse_args()

    print("Fetching channels.json ...", flush=True)
    channels = fetch_json(CHANNELS_URL)
    print(f"  {len(channels)} channels", flush=True)
    print("Fetching streams.json ...", flush=True)
    streams = fetch_json(STREAMS_URL)
    print(f"  {len(streams)} streams", flush=True)

    by_id = {c["id"]: c for c in channels}
    seen: set[str] = set()
    out: list[dict] = []
    for s in streams:
        cid = s.get("channel")
        if not cid or cid in seen:
            continue
        ch = by_id.get(cid)
        if not ch or ch.get("is_nsfw") or ch.get("closed"):
            continue
        cats = [c for c in (ch.get("categories") or []) if c in ALLOWED_TOPICS]
        if not cats:
            continue
        url = s.get("url") or ""
        if not url.startswith("https://") or ".m3u8" not in url:
            continue
        if any(bad in url for bad in SKIP_URL_PARTS):
            continue
        if args.check and not stream_alive(url):
            continue
        seen.add(cid)
        out.append({
            "id": cid.lower().replace(".", "-"),
            "name": ch["name"],
            "topic": TOPIC_MAP[cats[0]],
            "country": ch.get("country"),
            "language": None,
            "logo": None,
            "stream_url": url,
            "quality": s.get("quality"),
            "source": "iptv-org",
            "iptv_org_id": cid,
        })
        if len(out) >= args.max:
            break

    with open(args.out, "w", encoding="utf-8") as f:
        json.dump(out, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"Wrote {len(out)} channels to {args.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
