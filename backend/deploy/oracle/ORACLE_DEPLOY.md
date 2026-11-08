# OpenLive on Oracle Cloud Always Free — step-by-step

Your OpenLive backend (FastAPI + SQLite) runs on a **free Oracle VPS
that stays on 24/7**. Oracle's Always Free tier costs $0/month — but
**you** have to create the account and the instance yourself (Oracle
needs your card and your SSH key; Muse can't do that part for you).

What you end up with:

- Backend at `https://<your-domain>/` (with a free domain, recommended)
  or `http://<your-public-IP>/` (without a domain — plain HTTP)
- The Android app points at that URL in **Settings** — no PC needed
  after this.

Time needed: about 30–60 minutes the first time, mostly waiting for
Oracle signup checks.

---

## Part 1 — Create the Oracle account (on your phone or PC)

1. Go to **https://cloud.oracle.com** → **Sign Up** (or "Start for free").
2. Create the account with your email. Oracle asks for a **credit/debit
   card for verification**. It may place a small temporary hold, but
   Always Free resources are **$0** — you are only charged if you
   deliberately pick a paid shape. **Never pick a paid shape.**
3. Pick a **Home Region** you can live with — you cannot change it
   later. Any region works; pick the closest one to Tunisia (e.g.
   a European region if offered).
4. Wait for the account to activate (email confirmation, usually
   minutes; sometimes a few hours).

> ⚠️ If signup fails ("could not verify"), try another card, or a
> different browser. Oracle is picky about virtual/prepaid cards.

## Part 2 — Create the Always Free instance

In the Oracle console (https://cloud.oracle.com):

1. Menu → **Compute → Instances → Create instance**.
2. Name: `openlive` (anything is fine).
3. **Placement:** pick an **Availability Domain** (AD-1 is fine).
4. **Image:** **Ubuntu 24.04** (or 22.04 if 24.04 is not offered).
   Use the normal Ubuntu image, not "Minimal" unless you know why.
5. **Shape:** click **Change shape** → **Ampere** → pick
   **`VM.Standard.A1.Flex`** (ARM).
   - **OCPU count: 2**, **Memory: 12 GB**. This is inside the free
     allowance (4 OCPU / 24 GB RAM total across all your free
     instances), and it is plenty for OpenLive.
   - ⚠️ If the shape list shows a price, you picked a paid shape.
     Go back and pick the A1.Flex with "Always Free" marked.
6. **Boot volume:** 50 GB (free allowance is 200 GB total).
7. **Networking:** use the default VCN (or create one). Make sure
   **"Assign a public IPv4 address"** is ON.
8. **Add SSH keys:** choose **"Generate a key pair for me"** and
   **download both keys** (`*.key` private + `*.key.pub`), OR paste
   your own public key if you already have one (Termux:
   `ssh-keygen -t ed25519`, then copy `~/.ssh/id_ed25519.pub`).
   **Keep the private key safe — it is the only way in.**
9. Click **Create** and wait until the instance shows **Running**.
   Note the **Public IP** on the instance page — you will need it.

### "Out of capacity" — very common for A1

Oracle's free ARM machines are popular and often full. If you get
`Out of capacity` / `Out of host capacity`:

- Try another **Availability Domain** in the same region.
- Try **1 OCPU / 6 GB** instead of 2 / 12.
- Try again later (early morning / off-peak is often better).
- Fallback free shape: **VM.Standard.E2.1.Micro** (AMD, 1 OCPU /
  1 GB RAM, also Always Free, 2 instances allowed). It is weaker
  but OpenLive still runs on it. The setup script works on both
  ARM and AMD — it does not care which chip you have.

Do **not** "upgrade" to a paid shape to get capacity. Just retry.

## Part 3 — Open the firewall (Oracle side)

The backend needs ports **22 (SSH), 80 (HTTP) and 443 (HTTPS)**
reachable from the internet.

1. Menu → **Networking → Virtual Cloud Networks** → open your VCN →
   **Security Lists** → **Default Security List**.
2. **Add Ingress Rules** (three rules, one per port):
   - Source CIDR: `0.0.0.0/0`, IP Protocol: **TCP**,
     Destination Port Range: `22` — then repeat for `80` and `443`.
   - Leave the existing SSH rule alone if it is already there.
3. The setup script also opens the same ports in the instance's own
   firewall (`ufw`), so both layers match.

## Part 4 — SSH in and run the setup script

On your PC (or in Termux on your phone):

```bash
# 1. Copy the private key to a safe place and fix its permissions:
chmod 600 openlive.key

# 2. SSH in (replace <PUBLIC-IP> with the instance's public IP):
ssh -i openlive.key ubuntu@<PUBLIC-IP>
```

Once inside, run:

```bash
git clone https://github.com/dhaouadiahmed1/openlive.git /opt/openlive
cd /opt/openlive
sudo bash backend/deploy/oracle/setup-oracle.sh
```

**With a domain** (recommended — automatic HTTPS):

```bash
sudo bash backend/deploy/oracle/setup-oracle.sh your-domain.example.com
```

The script is idempotent — you can run it again after a `git pull`
and it will update the code without wiping your database or your
admin token.

What the script does: installs Python + Caddy, creates a virtual
environment, installs the backend requirements, creates the systemd
service `openlive`, sets up Caddy as a reverse proxy to
`127.0.0.1:8000`, opens the firewall, and prints a health check at
the end. It contains **no secrets** — your admin token is generated
on the instance and stored only in `/etc/openlive/openlive.env`.

### Your admin token — save it

When the script runs the first time it prints a line like:

```
ADMIN TOKEN (save this — it is shown only once here):
  <a long random token>
```

Copy it somewhere safe. You need it to add channels/codes:

```bash
curl -X POST https://<your-domain>/api/admin/codes \
  -H 'Content-Type: application/json' \
  -H 'X-Admin-Token: <your-token>' \
  -d '{"code":"MY-CODE-123","label":"friend"}'
```

If you lose it, read it back from the server (you need SSH):

```bash
sudo cat /etc/openlive/openlive.env
```

**Never** put the token in GitHub, in the app, or in a chat message.

## Part 5 — HTTPS with a free domain (recommended)

Without a domain, the app uses plain HTTP and anyone on the network
can see the activation codes you send. A free domain fixes that —
Caddy then gets a real HTTPS certificate automatically.

Easiest free option — **DuckDNS** (https://www.duckdns.org):

1. Sign in with GitHub, create a subdomain, e.g.
   `openlive-ahmed.duckdns.org`.
2. Point it at your instance's **public IP** (DuckDNS page → enter
   the IP → update).
3. Wait a minute, then check it resolves:
   `nslookup openlive-ahmed.duckdns.org` (or `ping` it).
4. Re-run the setup script with the domain:
   `sudo bash backend/deploy/oracle/setup-oracle.sh openlive-ahmed.duckdns.org`
5. Caddy will fetch the certificate automatically. Test:
   `curl https://openlive-ahmed.duckdns.org/api/health`

### No domain?

The script still works — Caddy serves plain HTTP on port 80 and
your URL is `http://<PUBLIC-IP>/`.

⚠️ **Warning:** on plain HTTP, activation codes and admin traffic
travel unencrypted. It works for testing, but get a free domain
before you share the app with anyone else.

## Part 6 — Point the Android app at it

1. Open **OpenLive → Settings**.
2. Backend URL:
   - With a domain: `https://<your-domain>/`
   - Without a domain: `http://<PUBLIC-IP>/`
   (Trailing `/` matters — keep it.)
3. Activation code: `OPENLIVE-DEMO-2026` (or a code you created).
4. Tap **Save & activate**, then go to Home — channels should load.

---

## ⚠️ Idle-reclaim warning (Oracle Always Free)

Oracle may **reclaim an Always Free A1 instance that looks idle for
7 days** (very low CPU + network + memory use for a whole week).
This is Oracle's rule, not OpenLive's — **nothing here can
guarantee against it**.

What lowers the risk:

- **Real traffic helps.** Every time you (or anyone using the app)
  open the app, the backend gets traffic, so it does not look idle.
- The setup script installs a small **hourly keep-alive cron**
  (`curl http://127.0.0.1:8000/api/health`) and a **weekly channel
  refresh** (re-runs `scripts/import_iptv_org.py` to update
  `channels.json`). This keeps the machine doing *something*,
  but it is **not a guarantee** — Oracle measures more than a
  local health check, and a local curl generates almost no
  network traffic.
- If the instance is ever reclaimed, you recreate it from Part 2
  and re-run the setup script — your code and channel list are in
  GitHub, so you lose only the database (channels re-seed from
  `channels.json`; admin-created codes would need to be re-added).
  Keep a copy of your admin token and your codes list.

## Everyday commands (on the instance, over SSH)

```bash
# Is it running?
sudo systemctl status openlive
curl http://127.0.0.1:8000/api/health

# Logs (live):
sudo journalctl -u openlive -f

# Restart after a config change:
sudo systemctl restart openlive

# Update to the newest code from GitHub:
cd /opt/openlive && git pull
sudo bash backend/deploy/oracle/setup-oracle.sh [your-domain]
sudo systemctl restart openlive

# Check Caddy:
sudo systemctl status caddy
sudo caddy validate --config /etc/caddy/Caddyfile
```

## Troubleshooting

| Symptom | Check |
|---|---|
| `ssh: Connection refused` / timeout | Instance really Running? Public IP correct? Ingress rule for port 22 in the VCN security list? Key permissions `chmod 600`? |
| `curl` health check fails on the instance | `sudo systemctl status openlive`, then `sudo journalctl -u openlive -n 50` — usually a missing dependency or a bad `.env` file |
| App says "cannot connect" | Backend URL exactly right? Domain DNS pointing at the public IP? `curl https://<domain>/api/health` from your PC — if that works, the app URL is wrong |
| HTTPS certificate error | Domain DNS must resolve to the public IP *before* Caddy starts; re-run the setup script with the domain after DNS works |
| `Out of capacity` | See Part 2 — another AD, smaller shape, retry later, or E2.1.Micro |
| Channels empty / 0 | `curl http://127.0.0.1:8000/api/health` — if channels = 0, the DB seeded wrong; check `backend/channels.json` exists in `/opt/openlive/backend/` |

## Files in this folder

- `setup-oracle.sh` — the setup script (run it on the instance).
- `openlive.service` — systemd unit (the script installs it).
- `Caddyfile.example` — reverse-proxy template (the script writes
  the real one from your domain, or the HTTP-only variant).
