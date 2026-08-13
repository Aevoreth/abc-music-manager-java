# Settings — Set Playback relays

**File → Settings… → Set Playback**

Named Cloudflare relays for [Set Play](../set-play.md) named sessions. **Only the bandleader** needs a relay configured here (URL **and relay token**). Band assistants join via the share link (browser or paste into [Band Assistant](../band-assistant.md)) and do not need this settings page.

Relays are stored in the library SQLite database (schema v13). The selected relay id remains in `preferences.json`.

---

## Why relays?

Hosting a Set Play session uses a Cloudflare Worker (HTTP + WebSocket Durable Object), a **D1** session list, and **R2** for optional zips, plus a `/playback` Band Assistant page. Each bandleader can deploy their own worker (free tier is more than sufficient for typical band usage).

---

## Add a relay

1. Click **Add relay** (or use the deploy helper below)
2. Enter a **Name** (e.g. "Main band") and **URL** (`wss://your-worker.workers.dev` or `https://…`, **no trailing slash**)
3. Optional **Relay token** (hidden by default; Reveal / Copy / Paste). Copy/paste warns that two PCs hosting the same session are **last-write-wins**. A lost Cloudflare token cannot be recovered — redeploy.
4. **Zip retention (days after set date/time)** defaults to 14. Changing this does not rewrite existing objects.

Select the active relay from the combo box on the **Set Play → Sessions** tab.

---

## Deploy helper {#deploy-relay}

**Create your own relay (deploy helper)…** opens an in-app wizard that copies the worker template, creates D1 + R2, seeds a token hash on Cloudflare, and walks through Wrangler deploy. The plaintext token is shown **once**.

You need:

- A Cloudflare account (free)
- Node.js / npm for `wrangler deploy` (wizard provides instructions and in most cases can help install these)

Prefer `*.workers.dev`. On a custom domain, skip Bot Fight / WAF for the worker hostname or `/api/*` — the Bearer token does not bypass Bot Fight.

If you already deployed a relay before named sessions, use **Redeploy worker on Cloudflare…**. That **wipes** the worker, D1 session list, R2 zips, and issues a new token.

---

## End-to-end workflow

1. Deploy a worker and save its URL + token here (bandleader only)
2. On **Set Play → Sessions**: Load set → **Create session** → copy **Play Only** or **Download and Play**
3. Assistants: open the link in a browser, or paste it into **Band Assistant → Connect**

See [Set Play → Named sessions](../set-play.md#broadcast) and [Troubleshooting → Relay](../troubleshooting.md#relay-issues).

---

[← User Guide home](../index.md) · [Set Play](../set-play.md) · [Band Assistant](../band-assistant.md)
