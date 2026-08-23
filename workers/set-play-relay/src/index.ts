/**
 * Set Play relay: D1 session registry, R2 zips, one Durable Object per room.
 * Owner APIs require Authorization: Bearer <relayToken>.
 * Assistants connect to WS without a token; zip GET requires X-Zip-Passphrase.
 */

import { DurableObject } from "cloudflare:workers";

export interface Env {
  SET_PLAY_ROOM: DurableObjectNamespace<SetPlayRoom>;
  ASSETS: Fetcher;
  REGISTRY: D1Database;
  ZIPS: R2Bucket;
}

const STATE_TYPE = "set_play_state_v2";
const MAX_ZIP_BYTES = 2 * 1024 * 1024;
const PIN_FAIL_LIMIT = 10;
const PIN_WINDOW_MS = 5 * 60 * 1000;
const CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "Authorization, Content-Type, X-Zip-Passphrase",
  "Access-Control-Allow-Methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
};

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "Content-Type": "application/json", ...CORS_HEADERS },
  });
}

function text(message: string, status: number): Response {
  return new Response(message, { status, headers: CORS_HEADERS });
}

function randomCode(): string {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
  let s = "";
  for (let i = 0; i < 7; i++) {
    s += alphabet[Math.floor(Math.random() * alphabet.length)];
  }
  return s;
}

function randomPin(): string {
  const n = crypto.getRandomValues(new Uint32Array(1))[0] % 1_000_000;
  return n.toString().padStart(6, "0");
}

/** R2 key stays zips/CODE.zip; the saved filename uses the set name. */
function zipDownloadFileName(setName: string | null | undefined, code: string): string {
  const fromSet = sanitizeZipBaseName(setName);
  const fromCode = sanitizeZipBaseName(code);
  const base = fromSet || fromCode || "set";
  return `${base}.zip`;
}

function sanitizeZipBaseName(raw: string | null | undefined): string {
  if (!raw) return "";
  let s = String(raw)
    .trim()
    .replace(/[\\/:*?"<>|\x00-\x1F]/g, " ")
    .replace(/\s+/g, " ")
    .trim()
    .replace(/^[.]+/, "")
    .replace(/[.]+$/, "");
  if (s.length > 120) s = s.slice(0, 120).trim();
  return s;
}

function zipContentDisposition(setName: string | null | undefined, code: string): string {
  const file = zipDownloadFileName(setName, code);
  const ascii = file.replace(/[^\x20-\x7E]/g, "_").replace(/"/g, "");
  return `attachment; filename="${ascii}"; filename*=UTF-8''${encodeURIComponent(file)}`;
}

function placeholderLayoutCards(raw: unknown): unknown[] {
  if (!Array.isArray(raw)) {
    return [];
  }
  return raw.map((card) => {
    if (!card || typeof card !== "object") {
      return card;
    }
    const c = card as Record<string, unknown>;
    return {
      ...c,
      part_number: "---",
      part_name: "(Part Name)",
      instrument_name: "(Made for Instrument)",
      instrument_warning: false,
      part_duplicate: false,
      neighbor_prev_part_label: "",
      neighbor_next_part_label: "",
      instrument_changed_from_prior_in_set: false,
    };
  });
}

function randomToken(): string {
  const bytes = new Uint8Array(32);
  crypto.getRandomValues(bytes);
  return [...bytes].map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function sha256Hex(value: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(value));
  return [...new Uint8Array(buf)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

function bearerToken(request: Request): string | null {
  const header = request.headers.get("Authorization") || "";
  const m = header.match(/^Bearer\s+(.+)$/i);
  return m ? m[1].trim() : null;
}

function clientIp(request: Request): string {
  return request.headers.get("CF-Connecting-IP") || request.headers.get("X-Forwarded-For") || "unknown";
}

function nowIso(): string {
  return new Date().toISOString();
}

type SessionRow = {
  code: string;
  name: string;
  passphrase_hash: string;
  setlist_id: number | null;
  set_name: string | null;
  notes: string | null;
  set_date: string | null;
  set_time: string | null;
  r2_key: string | null;
  expires_at: string | null;
  created_at: string;
  updated_at: string;
};

export class SetPlayRoom extends DurableObject {
  private _initSchema(): void {
    this.ctx.storage.sql.exec(
      `CREATE TABLE IF NOT EXISTS kv (key TEXT PRIMARY KEY, value TEXT);`,
    );
  }

  private _getKv(key: string): string | null {
    let result: string | null = null;
    const cur = this.ctx.storage.sql.exec("SELECT value FROM kv WHERE key = ?", key);
    for (const row of cur) {
      result = String((row as { value?: string }).value ?? "");
    }
    return result;
  }

  private _setKv(key: string, value: string): void {
    this.ctx.storage.sql.exec(
      `INSERT INTO kv (key, value) VALUES (?, ?)
       ON CONFLICT(key) DO UPDATE SET value = excluded.value`,
      key,
      value,
    );
  }

  private _broadcast(raw: string, except?: WebSocket): void {
    for (const peer of this.ctx.getWebSockets()) {
      if (except && peer === except) {
        continue;
      }
      try {
        peer.send(raw);
      } catch {
        /* ignore */
      }
    }
  }

  private _closeAll(reason: string): void {
    for (const peer of this.ctx.getWebSockets()) {
      try {
        peer.close(1000, reason);
      } catch {
        /* ignore */
      }
    }
  }

  private _resetFlagsInState(raw: string | null): string {
    let parsed: Record<string, unknown> = { type: STATE_TYPE, revision: 0 };
    if (raw) {
      try {
        parsed = JSON.parse(raw) as Record<string, unknown>;
      } catch {
        parsed = { type: STATE_TYPE, revision: 0 };
      }
    }
    parsed.type = STATE_TYPE;
    parsed.played_item_ids = [];
    parsed.skipped_item_ids = [];
    parsed.current_item_id = null;
    parsed.next_item_id = null;
    parsed.next_layout_cards = placeholderLayoutCards(parsed.next_layout_cards);
    const rev = Number(parsed.revision ?? 0) + 1;
    parsed.revision = rev;
    return JSON.stringify(parsed);
  }

  async fetch(request: Request): Promise<Response> {
    this._initSchema();
    const url = new URL(request.url);

    if (url.pathname === "/internal/init" && request.method === "POST") {
      this._setKv("state_json", "{}");
      this._setKv("revision", "0");
      return json({ ok: true });
    }

    if (url.pathname === "/internal/clear" && request.method === "POST") {
      const next = this._resetFlagsInState(this._getKv("state_json"));
      this._setKv("state_json", next);
      this._broadcast(next);
      return json({ ok: true });
    }

    if (url.pathname === "/internal/replace-state" && request.method === "POST") {
      let body: { state?: unknown };
      try {
        body = (await request.json()) as { state?: unknown };
      } catch {
        return text("bad json", 400);
      }
      const raw =
        typeof body.state === "string" ? body.state : JSON.stringify(body.state ?? {});
      let parsed: Record<string, unknown>;
      try {
        parsed = JSON.parse(raw) as Record<string, unknown>;
      } catch {
        return text("bad state", 400);
      }
      parsed.type = STATE_TYPE;
      parsed.played_item_ids = [];
      parsed.skipped_item_ids = [];
      parsed.current_item_id = null;
      parsed.next_item_id = null;
      parsed.revision = Number(parsed.revision ?? 0) + 1;
      const out = JSON.stringify(parsed);
      this._setKv("state_json", out);
      this._setKv("revision", String(parsed.revision));
      this._broadcast(out);
      return json({ ok: true, revision: parsed.revision });
    }

    if (url.pathname === "/internal/zip-flag" && request.method === "POST") {
      let body: { zip_available?: boolean };
      try {
        body = (await request.json()) as { zip_available?: boolean };
      } catch {
        return text("bad json", 400);
      }
      const snap = this._getKv("state_json");
      if (snap && snap.length > 2) {
        try {
          const parsed = JSON.parse(snap) as Record<string, unknown>;
          parsed.zip_available = Boolean(body.zip_available);
          const out = JSON.stringify(parsed);
          this._setKv("state_json", out);
          this._broadcast(out);
        } catch {
          /* ignore */
        }
      }
      return json({ ok: true });
    }

    if (url.pathname === "/internal/destroy" && request.method === "POST") {
      this._closeAll("session ended");
      await this.ctx.storage.deleteAll();
      return json({ ok: true });
    }

    if (request.headers.get("Upgrade") !== "websocket") {
      return text("expected websocket", 426);
    }

    const role = request.headers.get("X-Internal-Role") === "leader" ? "leader" : "assistant";
    const webSocketPair = new WebSocketPair();
    const [client, server] = Object.values(webSocketPair);
    this.ctx.acceptWebSocket(server);
    server.serializeAttachment(JSON.stringify({ role }));

    const snap = this._getKv("state_json");
    if (snap && snap !== "{}" && snap.length > 2) {
      try {
        server.send(snap);
      } catch {
        /* ignore */
      }
    }

    return new Response(null, { status: 101, webSocket: client });
  }

  async webSocketMessage(ws: WebSocket, message: string | ArrayBuffer): Promise<void> {
    const raw =
      typeof message === "string" ? message : new TextDecoder().decode(message);
    let att: { role: string };
    try {
      att = JSON.parse(ws.deserializeAttachment() as string) as { role: string };
    } catch {
      att = { role: "assistant" };
    }
    if (att.role !== "leader") {
      return;
    }
    let parsed: Record<string, unknown>;
    try {
      parsed = JSON.parse(raw) as Record<string, unknown>;
    } catch {
      return;
    }
    if (parsed.type !== STATE_TYPE) {
      return;
    }
    const rev = Number(parsed.revision ?? 0);
    this._setKv("state_json", raw);
    this._setKv("revision", String(rev));
    this._broadcast(raw);
  }

  async webSocketClose(): Promise<void> {
    /* hibernation */
  }
}

async function requireOwner(request: Request, env: Env): Promise<Response | null> {
  const token = bearerToken(request);
  if (!token) {
    return text("unauthorized", 401);
  }
  const hash = await sha256Hex(token);
  const row = await env.REGISTRY.prepare(
    "SELECT token_hash FROM relay_config WHERE id = 1",
  ).first<{ token_hash: string }>();
  if (!row || row.token_hash !== hash) {
    return text("unauthorized", 401);
  }
  return null;
}

async function getSession(env: Env, code: string): Promise<SessionRow | null> {
  return env.REGISTRY.prepare("SELECT * FROM session WHERE code = ?")
    .bind(code)
    .first<SessionRow>();
}

function sessionPublic(row: SessionRow) {
  return {
    code: row.code,
    name: row.name,
    setlistId: row.setlist_id,
    setName: row.set_name,
    notes: row.notes,
    setDate: row.set_date,
    setTime: row.set_time,
    zipAvailable: Boolean(row.r2_key),
    expiresAt: row.expires_at,
    createdAt: row.created_at,
    updatedAt: row.updated_at,
  };
}

function roomStub(env: Env, code: string) {
  const id = env.SET_PLAY_ROOM.idFromName(code);
  return env.SET_PLAY_ROOM.get(id);
}

async function pinFailCount(request: Request, code: string): Promise<number> {
  const key = `pinfail:${code}:${clientIp(request)}`;
  const cache = caches.default;
  const cacheUrl = new URL("https://set-play-relay.internal/" + encodeURIComponent(key));
  const hit = await cache.match(cacheUrl);
  if (!hit) {
    return 0;
  }
  return Number(await hit.text()) || 0;
}

async function bumpPinFail(request: Request, code: string): Promise<void> {
  const key = `pinfail:${code}:${clientIp(request)}`;
  const cache = caches.default;
  const cacheUrl = new URL("https://set-play-relay.internal/" + encodeURIComponent(key));
  const count = await pinFailCount(request, code);
  const next = new Response(String(count + 1), {
    headers: { "Cache-Control": `max-age=${Math.ceil(PIN_WINDOW_MS / 1000)}` },
  });
  await cache.put(cacheUrl, next);
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") {
      return new Response(null, { headers: CORS_HEADERS });
    }

    if (url.pathname === "/api/sessions" && request.method === "GET") {
      const denied = await requireOwner(request, env);
      if (denied) {
        return denied;
      }
      const rows = await env.REGISTRY.prepare(
        "SELECT * FROM session ORDER BY updated_at DESC",
      ).all<SessionRow>();
      return json({ sessions: (rows.results || []).map(sessionPublic) });
    }

    if (url.pathname === "/api/sessions" && request.method === "POST") {
      const denied = await requireOwner(request, env);
      if (denied) {
        return denied;
      }
      let body: {
        name?: string;
        setlistId?: number;
        setName?: string;
        notes?: string;
        setDate?: string;
        setTime?: string;
        state?: unknown;
      };
      try {
        body = (await request.json()) as typeof body;
      } catch {
        return text("bad json", 400);
      }
      const name = (body.name || "").trim();
      if (!name) {
        return text("name required", 400);
      }
      const pin = randomPin();
      const pinHash = await sha256Hex(pin);
      const stamp = nowIso();
      let code = "";
      for (let attempt = 0; attempt < 16; attempt++) {
        const candidate = randomCode();
        try {
          await env.REGISTRY.prepare(
            `INSERT INTO session (code, name, passphrase_hash, setlist_id, set_name, notes,
              set_date, set_time, r2_key, expires_at, created_at, updated_at)
             VALUES (?, ?, ?, ?, ?, ?, ?, ?, NULL, NULL, ?, ?)`,
          )
            .bind(
              candidate,
              name,
              pinHash,
              body.setlistId ?? null,
              body.setName ?? null,
              body.notes ?? null,
              body.setDate ?? null,
              body.setTime ?? null,
              stamp,
              stamp,
            )
            .run();
          code = candidate;
          break;
        } catch {
          /* unique collision */
        }
      }
      if (!code) {
        return text("could not allocate session", 500);
      }
      const stub = roomStub(env, code);
      await stub.fetch(
        new Request("https://internal/internal/init", { method: "POST" }),
      );
      if (body.state) {
        await stub.fetch(
          new Request("https://internal/internal/replace-state", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ state: body.state }),
          }),
        );
      }
      return json({ roomCode: code, passphrase: pin, name });
    }

    const sessionMatch = url.pathname.match(/^\/api\/sessions\/([^/]+)(?:\/(zip|clear))?$/);
    if (sessionMatch) {
      const code = decodeURIComponent(sessionMatch[1]).toUpperCase();
      const extra = sessionMatch[2];
      const row = await getSession(env, code);
      if (!row) {
        return text("not found", 404);
      }

      if (extra === "zip" && request.method === "GET") {
        const pin = request.headers.get("X-Zip-Passphrase") || "";
        if (!row.r2_key) {
          return text("not found", 404);
        }
        if ((await pinFailCount(request, code)) >= PIN_FAIL_LIMIT) {
          return text("not found", 404);
        }
        const hash = await sha256Hex(pin);
        if (hash !== row.passphrase_hash) {
          await bumpPinFail(request, code);
          return text("not found", 404);
        }
        const obj = await env.ZIPS.get(row.r2_key);
        if (!obj) {
          return text("not found", 404);
        }
        return new Response(obj.body, {
          headers: {
            "Content-Type": "application/zip",
            "Content-Disposition": zipContentDisposition(row.set_name, code),
            ...CORS_HEADERS,
          },
        });
      }

      const denied = await requireOwner(request, env);
      if (denied) {
        return denied;
      }

      if (extra === "clear" && request.method === "POST") {
        await roomStub(env, code).fetch(
          new Request("https://internal/internal/clear", { method: "POST" }),
        );
        return json({ ok: true });
      }

      if (extra === "zip" && request.method === "PUT") {
        const buf = await request.arrayBuffer();
        if (buf.byteLength > MAX_ZIP_BYTES) {
          return text("zip too large", 413);
        }
        const expiresAt = request.headers.get("X-Expires-At");
        const key = `zips/${code}.zip`;
        await env.ZIPS.put(key, buf, {
          httpMetadata: { contentType: "application/zip" },
        });
        await env.REGISTRY.prepare(
          "UPDATE session SET r2_key = ?, expires_at = ?, updated_at = ? WHERE code = ?",
        )
          .bind(key, expiresAt, nowIso(), code)
          .run();
        await roomStub(env, code).fetch(
          new Request("https://internal/internal/zip-flag", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ zip_available: true }),
          }),
        );
        return json({ ok: true, expiresAt });
      }

      if (extra === "zip" && request.method === "DELETE") {
        if (row.r2_key) {
          await env.ZIPS.delete(row.r2_key);
        }
        await env.REGISTRY.prepare(
          "UPDATE session SET r2_key = NULL, expires_at = NULL, updated_at = ? WHERE code = ?",
        )
          .bind(nowIso(), code)
          .run();
        await roomStub(env, code).fetch(
          new Request("https://internal/internal/zip-flag", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ zip_available: false }),
          }),
        );
        return json({ ok: true });
      }

      if (!extra && request.method === "PATCH") {
        let body: {
          name?: string;
          republish?: boolean;
          setlistId?: number;
          setName?: string;
          notes?: string;
          setDate?: string;
          setTime?: string;
          state?: unknown;
        };
        try {
          body = (await request.json()) as typeof body;
        } catch {
          return text("bad json", 400);
        }
        if (body.name && body.name.trim() && body.name.trim() !== row.name) {
          await env.REGISTRY.prepare(
            "UPDATE session SET name = ?, updated_at = ? WHERE code = ?",
          )
            .bind(body.name.trim(), nowIso(), code)
            .run();
        }
        if (body.republish) {
          if (row.r2_key) {
            await env.ZIPS.delete(row.r2_key);
          }
          await env.REGISTRY.prepare(
            `UPDATE session SET setlist_id = ?, set_name = ?, notes = ?, set_date = ?,
             set_time = ?, r2_key = NULL, expires_at = NULL, updated_at = ? WHERE code = ?`,
          )
            .bind(
              body.setlistId ?? row.setlist_id,
              body.setName ?? row.set_name,
              body.notes ?? row.notes,
              body.setDate ?? row.set_date,
              body.setTime ?? row.set_time,
              nowIso(),
              code,
            )
            .run();
          await roomStub(env, code).fetch(
            new Request("https://internal/internal/replace-state", {
              method: "POST",
              headers: { "Content-Type": "application/json" },
              body: JSON.stringify({ state: body.state ?? {} }),
            }),
          );
        } else if (body.setDate !== undefined || body.setTime !== undefined || body.notes !== undefined) {
          await env.REGISTRY.prepare(
            `UPDATE session SET notes = ?, set_date = ?, set_time = ?, updated_at = ? WHERE code = ?`,
          )
            .bind(
              body.notes ?? row.notes,
              body.setDate ?? row.set_date,
              body.setTime ?? row.set_time,
              nowIso(),
              code,
            )
            .run();
        }
        const updated = await getSession(env, code);
        return json({ session: updated ? sessionPublic(updated) : null });
      }

      if (!extra && request.method === "DELETE") {
        if (row.r2_key) {
          await env.ZIPS.delete(row.r2_key);
        }
        await env.REGISTRY.prepare("DELETE FROM session WHERE code = ?").bind(code).run();
        await roomStub(env, code).fetch(
          new Request("https://internal/internal/destroy", { method: "POST" }),
        );
        return json({ ok: true });
      }
    }

    const wsMatch = url.pathname.match(/^\/api\/rooms\/([^/]+)\/ws$/);
    if (wsMatch) {
      const code = decodeURIComponent(wsMatch[1]).toUpperCase();
      const row = await getSession(env, code);
      if (!row) {
        return text("not found", 404);
      }
      let role = "assistant";
      const token = bearerToken(request);
      if (token) {
        const hash = await sha256Hex(token);
        const cfg = await env.REGISTRY.prepare(
          "SELECT token_hash FROM relay_config WHERE id = 1",
        ).first<{ token_hash: string }>();
        if (cfg && cfg.token_hash === hash) {
          role = "leader";
        }
      }
      const stub = roomStub(env, code);
      const headers = new Headers(request.headers);
      headers.set("X-Internal-Role", role);
      return stub.fetch(
        new Request(request.url, { method: request.method, headers }),
      );
    }

    if (url.pathname === "/playback") {
      const dest = new URL(request.url);
      dest.pathname = "/playback/";
      return Response.redirect(dest.toString(), 302);
    }

    return env.ASSETS.fetch(request);
  },

  async scheduled(_event: ScheduledEvent, env: Env): Promise<void> {
    const now = nowIso();
    const expired = await env.REGISTRY.prepare(
      "SELECT code, r2_key FROM session WHERE r2_key IS NOT NULL AND expires_at IS NOT NULL AND expires_at <= ?",
    )
      .bind(now)
      .all<{ code: string; r2_key: string }>();
    for (const row of expired.results || []) {
      if (row.r2_key) {
        await env.ZIPS.delete(row.r2_key);
      }
      await env.REGISTRY.prepare(
        "UPDATE session SET r2_key = NULL, expires_at = NULL, updated_at = ? WHERE code = ?",
      )
        .bind(now, row.code)
        .run();
      await roomStub(env, row.code).fetch(
        new Request("https://internal/internal/zip-flag", {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ zip_available: false }),
        }),
      );
    }
  },
};

export { randomToken, sha256Hex };
