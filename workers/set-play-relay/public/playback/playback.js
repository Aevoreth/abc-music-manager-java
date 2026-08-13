/**
 * Band Assistant web client — connects to the same-origin Set Play relay.
 * Protocol: set_play_state_v2 (full snapshot over WebSocket).
 */
(function () {
  "use strict";

  const STATE_TYPE = "set_play_state_v2";
  const CARD_W = 9;
  const CARD_H = 7;
  const BASE_PPU = 15;
  const MIN_PPU = 6;
  const X_MIN = -145;
  const Y_MIN = -105;
  const GRID_PANE_MIN = 120;
  const SETLIST_PANE_MIN = 120;
  const GRID_H_STORAGE = "abcmm-playback-grid-h";
  const EMPTY_PART = "---";

  const COLORS = {
    surface: "#12101a",
    outline: "#3d3654",
    onSurface: "#e8e4dc",
    textSecondary: "#b4a8a8",
    primary: "#c9a227",
    error: "#7a3030",
    warning: "#d48a3a",
    dup: "#ff4444",
  };

  const setInput = document.getElementById("set-input");
  const pinInput = document.getElementById("pin-input");
  const connectBtn = document.getElementById("connect-btn");
  const disconnectBtn = document.getElementById("disconnect-btn");
  const reconnectBtn = document.getElementById("reconnect-btn");
  const downloadBtn = document.getElementById("download-btn");
  const statusEl = document.getElementById("status");
  const statusPill = document.getElementById("status-pill");
  const setInfo = document.getElementById("set-info");
  const songTbody = document.getElementById("song-tbody");
  const playersList = document.getElementById("players-list");
  const connCode = document.getElementById("conn-code");
  const connHost = document.getElementById("conn-host");
  const connRev = document.getElementById("conn-rev");
  const currentTitle = document.getElementById("current-title");
  const currentMeta = document.getElementById("current-meta");
  const nextTitle = document.getElementById("next-title");
  const nextMeta = document.getElementById("next-meta");
  const layoutViewport = document.getElementById("layout-viewport");
  const canvas = document.getElementById("layout-canvas");
  const layoutEmpty = document.getElementById("layout-empty");
  const recenterBtn = document.getElementById("recenter-btn");
  const playbackSplit = document.querySelector(".playback-split");
  const splitHandle = document.getElementById("playback-split-handle");
  const gridCard = document.querySelector(".grid-card");
  const ctx = canvas.getContext("2d");

  const tabButtons = [...document.querySelectorAll(".tab")];
  const panels = {
    connection: document.getElementById("panel-connection"),
    playback: document.getElementById("panel-playback"),
    parts: document.getElementById("panel-parts"),
  };
  const instrumentsBtn = document.getElementById("instruments-btn");
  const instrumentsDialog = document.getElementById("instruments-dialog");
  const instrumentsBody = document.getElementById("instruments-body");
  const instrumentsCopyAll = document.getElementById("instruments-copy-all");
  const instrumentsClose = document.getElementById("instruments-close");
  const partsPlayerFilter = document.getElementById("parts-player-filter");
  const partsPlayerSummary = document.getElementById("parts-player-summary");
  const partsSelectedOnly = document.getElementById("parts-selected-only");

  /** @type {WebSocket | null} */
  let ws = null;
  /** @type {string | null} */
  let lastCode = null;
  /** @type {Set<number>} */
  const highlightPlayers = new Set();
  let lastPlayerFilterKey = "";
  /** @type {Array<object>} */
  let lastCards = [];
  /** @type {object | null} */
  let lastSnapshot = null;
  let hasSynced = false;
  /** @type {string} */
  let lastLayoutKey = "";
  let needsFit = false;
  /** @type {number} */
  let ppu = BASE_PPU;
  let lastCanvasW = 0;
  let lastCanvasH = 0;

  // Canvas pan state (pixel offsets applied after logical→view transform)
  let viewOffsetX = 0;
  let viewOffsetY = 0;
  let panStart = null;

  function setStatus(msg) {
    statusEl.textContent = msg;
  }

  function setPill(text, kind) {
    statusPill.textContent = text;
    statusPill.classList.remove("ok", "warn");
    if (kind) statusPill.classList.add(kind);
  }

  function fmtDuration(sec) {
    if (sec == null || Number.isNaN(Number(sec))) return "—";
    const s = Math.max(0, Math.floor(Number(sec)));
    const h = Math.floor(s / 3600);
    const m = Math.floor((s % 3600) / 60);
    const r = s % 60;
    if (h > 0) {
      return `${h}:${String(m).padStart(2, "0")}:${String(r).padStart(2, "0")}`;
    }
    return `${m}:${String(r).padStart(2, "0")}`;
  }

  function displayPartNumber(raw) {
    const s = raw == null ? "" : String(raw).trim();
    if (!s || s === "###" || s === "---") return EMPTY_PART;
    return s;
  }

  /**
   * @param {string} raw
   * @returns {{ code: string, pin?: string } | null}
   */
  function parseJoinInput(raw) {
    const text = (raw || "").trim();
    if (!text) return null;
    let pin = null;

    if (/^https?:\/\//i.test(text) || /^wss?:\/\//i.test(text)) {
      try {
        let urlStr = text;
        if (/^wss:\/\//i.test(urlStr)) urlStr = "https://" + urlStr.slice(6);
        else if (/^ws:\/\//i.test(urlStr)) urlStr = "http://" + urlStr.slice(5);
        const u = new URL(urlStr);
        pin = pinFromFragment(u.hash);
        const setParam = u.searchParams.get("set") || u.searchParams.get("code");
        if (setParam && setParam.trim().length >= 5) {
          return { code: setParam.trim().toUpperCase(), pin };
        }
        const m = u.pathname.match(/\/api\/rooms\/([^/]+)/i);
        if (m && m[1].length >= 5) {
          return { code: decodeURIComponent(m[1]).toUpperCase(), pin };
        }
      } catch {
        return null;
      }
      return null;
    }

    let body = text;
    const hash = body.indexOf("#");
    if (hash >= 0) {
      pin = pinFromFragment(body.slice(hash));
      body = body.slice(0, hash).trim();
    }
    const code = body.toUpperCase().replace(/[^A-Z0-9]/g, "");
    if (code.length >= 5) return { code, pin };
    return null;
  }

  function pinFromFragment(fragment) {
    if (!fragment) return null;
    const raw = String(fragment).replace(/^#/, "");
    if (/^p=/i.test(raw)) {
      const val = raw.slice(2).trim();
      return val || null;
    }
    for (const part of raw.split("&")) {
      const eq = part.indexOf("=");
      if (eq > 0 && part.slice(0, eq).toLowerCase() === "p") {
        const val = part.slice(eq + 1).trim();
        return val || null;
      }
    }
    return null;
  }

  function zipDownloadFileName() {
    const meta = lastSnapshot && lastSnapshot.set_meta ? lastSnapshot.set_meta : {};
    const raw = String(meta.name || lastCode || "set").trim();
    let base = raw
      .replace(/[\\/:*?"<>|\x00-\x1F]/g, " ")
      .replace(/\s+/g, " ")
      .trim()
      .replace(/^[.]+/, "")
      .replace(/[.]+$/, "");
    if (!base) base = lastCode || "set";
    if (base.length > 120) base = base.slice(0, 120).trim();
    return base + ".zip";
  }

  function updateDownloadButton() {
    if (!downloadBtn) return;
    const pin = (pinInput && pinInput.value ? pinInput.value : "").trim();
    const zip = !!(lastCode && lastSnapshot && lastSnapshot.zip_available);
    downloadBtn.hidden = !zip;
    downloadBtn.disabled = !zip || !pin;
    downloadBtn.title = zip && !pin
      ? "Enter the download PIN on the Connect tab"
      : "";
  }

  function wsUrlForCode(code) {
    const proto = location.protocol === "https:" ? "wss:" : "ws:";
    return `${proto}//${location.host}/api/rooms/${encodeURIComponent(code)}/ws`;
  }

  function setContentTabsEnabled(on) {
    for (const btn of tabButtons) {
      if (btn.dataset.tab === "connection") continue;
      btn.disabled = !on;
    }
  }

  function showTab(name) {
    for (const btn of tabButtons) {
      const active = btn.dataset.tab === name;
      btn.classList.toggle("active", active);
      btn.setAttribute("aria-selected", active ? "true" : "false");
    }
    for (const [key, panel] of Object.entries(panels)) {
      const active = key === name;
      panel.classList.toggle("active", active);
      panel.hidden = !active;
    }
    if (name === "playback") {
      requestAnimationFrame(() => {
        restoreGridPaneHeight();
        refreshLayoutCanvas();
      });
    }
  }

  function disconnect() {
    if (ws) {
      try {
        ws.close();
      } catch {
        /* ignore */
      }
      ws = null;
    }
    disconnectBtn.disabled = true;
    reconnectBtn.disabled = !(lastCode && lastCode.length >= 5);
    setStatus("Disconnected.");
    setPill("Disconnected", "warn");
  }

  function connect(code) {
    const parsed = parseJoinInput(code) || (code ? { code: String(code).toUpperCase() } : null);
    if (!parsed || parsed.code.length < 5) {
      setStatus("Enter a valid set code or paste a share link.");
      setPill("Not connected");
      return;
    }
    lastCode = parsed.code;
    if (parsed.pin && pinInput) pinInput.value = parsed.pin;
    setInput.value = lastCode;
    connCode.textContent = lastCode;
    connHost.textContent = location.host;

    if (ws) {
      try {
        ws.onclose = null;
        ws.close();
      } catch {
        /* ignore */
      }
      ws = null;
    }

    setStatus("Connecting…");
    setPill("Connecting…", "warn");
    const url = wsUrlForCode(lastCode);
    ws = new WebSocket(url);

    ws.onopen = () => {
      disconnectBtn.disabled = false;
      reconnectBtn.disabled = false;
      setStatus("Connected — waiting for set state…");
      setPill("Connected", "ok");
    };

    ws.onclose = (ev) => {
      ws = null;
      disconnectBtn.disabled = true;
      reconnectBtn.disabled = !!(lastCode && lastCode.length >= 5);
      const reason = ev && ev.reason ? String(ev.reason) : "";
      if (/session ended/i.test(reason)) {
        setStatus("Session ended by the bandleader.");
        setPill("Session ended", "warn");
        setContentTabsEnabled(false);
        if (downloadBtn) {
          downloadBtn.disabled = true;
          downloadBtn.hidden = true;
        }
      } else {
        setStatus("Relay disconnected.");
        setPill("Disconnected", "warn");
      }
    };

    ws.onerror = () => {
      setStatus("Relay connection error.");
      setPill("Error", "warn");
    };

    ws.onmessage = (ev) => {
      let data;
      try {
        data = JSON.parse(String(ev.data));
      } catch {
        return;
      }
      if (!data || data.type !== STATE_TYPE) return;
      applySnapshot(data);
    };
  }

  function rowById(data, itemId) {
    if (itemId == null) return null;
    const rows = Array.isArray(data.rows) ? data.rows : [];
    return rows.find((r) => Number(r.item_id) === Number(itemId)) || null;
  }

  function songMetaLine(row) {
    if (!row) return "";
    const parts = [];
    if (row.part_count != null) parts.push(`${row.part_count} parts`);
    if (row.duration_seconds != null) parts.push(fmtDuration(row.duration_seconds));
    if (row.artist) parts.push(String(row.artist));
    return parts.join(" · ");
  }

  function applySnapshot(data) {
    lastSnapshot = data;
    const meta = data.set_meta || {};
    const lines = [
      `<b>${escapeHtml(meta.name || "Set")}</b>`,
      `Date: ${escapeHtml(meta.set_date || "—")}  Time: ${escapeHtml(meta.set_time || "—")}`,
      `Notes: ${escapeHtml((meta.notes || "").trim() || "—")}`,
    ];
    if (meta.computed_duration_seconds != null) {
      lines.push(`Duration (incl. switches): ${fmtDuration(meta.computed_duration_seconds)}`);
    }
    setInfo.innerHTML = lines.join("<br/>");

    const played = new Set((data.played_item_ids || []).map(Number));
    const skipped = new Set((data.skipped_item_ids || []).map(Number));
    const currentId = data.current_item_id != null ? Number(data.current_item_id) : null;
    const nextId = data.next_item_id != null ? Number(data.next_item_id) : null;
    const rows = Array.isArray(data.rows) ? data.rows : [];
    const order = Array.isArray(data.order_item_ids) ? data.order_item_ids.map(Number) : [];
    const byId = new Map(rows.map((r) => [Number(r.item_id), r]));
    const ordered = order.length
      ? order.map((id) => byId.get(id)).filter(Boolean)
      : rows;

    songTbody.replaceChildren();
    for (const r of ordered) {
      const id = Number(r.item_id);
      const tr = document.createElement("tr");
      const isSkipped = skipped.has(id);
      const isCurrent = currentId === id;
      const isNext = nextId === id;
      const isPlayed = played.has(id);
      if (isSkipped) tr.className = "row-skipped";
      else if (isCurrent) tr.className = "row-current";
      else if (isNext) tr.className = "row-next";
      else if (isPlayed) tr.className = "row-played";

      tr.innerHTML = [
        statusBadgeCell(isSkipped, isCurrent, isNext, isPlayed),
        `<td>${escapeHtml(r.title || "")}</td>`,
        `<td>${escapeHtml(String(r.part_count ?? ""))}</td>`,
        `<td>${fmtDuration(r.duration_seconds)}</td>`,
        `<td>${escapeHtml(r.artist || "—")}</td>`,
      ].join("");
      songTbody.appendChild(tr);
    }

    const cur = rowById(data, currentId);
    const nxt = rowById(data, nextId);
    currentTitle.textContent = cur ? cur.title || "—" : "—";
    currentMeta.textContent = songMetaLine(cur);
    nextTitle.textContent = nxt ? nxt.title || "—" : "—";
    nextMeta.textContent = songMetaLine(nxt);

    lastCards = Array.isArray(data.next_layout_cards) ? data.next_layout_cards : [];
    const layoutKey = lastCards
      .map((c) => `${c.player_id}:${c.x},${c.y}`)
      .sort()
      .join("|");
    const layoutChanged = layoutKey !== lastLayoutKey;
    lastLayoutKey = layoutKey;

    renderPlayers(lastCards);
    recenterBtn.hidden = !lastCards.length;
    needsFit = needsFit || layoutChanged || !hasSynced;

    connRev.textContent = String(data.revision ?? "—");
    setStatus(`Synced (rev ${data.revision ?? "—"}).`);
    setPill(`Synced · rev ${data.revision ?? "—"}`, "ok");
    renderParts(data);
    updateDownloadButton();

    if (!hasSynced) {
      hasSynced = true;
      setContentTabsEnabled(true);
      showTab("playback");
    } else if (panels.playback.classList.contains("active")) {
      requestAnimationFrame(() => refreshLayoutCanvas());
    }
  }

  function statusBadgeCell(skipped, current, next, played) {
    let label = "";
    if (skipped) label = "SKIP";
    else if (current) label = "NOW";
    else if (next) label = "NEXT";
    else if (played) label = "✓";
    return `<td class="status-badge">${label}</td>`;
  }

  function renderParts(data) {
    const head = document.getElementById("parts-head");
    const body = document.getElementById("parts-tbody");
    if (!head || !body) return;
    const sheet = data.parts_sheet || {};
    const allColumns = Array.isArray(sheet.columns) ? sheet.columns : [];
    renderPartsPlayerFilter();
    const selectedOnly = !!(partsSelectedOnly && partsSelectedOnly.checked);
    const columns = allColumns.filter((c) => {
      if (!selectedOnly || highlightPlayers.size === 0) return true;
      if (c.player_id == null) return true;
      return highlightPlayers.has(Number(c.player_id));
    });
    const sheetRows = Array.isArray(sheet.rows) ? sheet.rows : [];
    const byItem = new Map(sheetRows.map((r) => [Number(r.item_id), r]));
    const played = new Set((data.played_item_ids || []).map(Number));
    const skipped = new Set((data.skipped_item_ids || []).map(Number));
    const currentId = data.current_item_id != null ? Number(data.current_item_id) : null;
    const nextId = data.next_item_id != null ? Number(data.next_item_id) : null;
    const rows = Array.isArray(data.rows) ? data.rows : [];
    const order = Array.isArray(data.order_item_ids) ? data.order_item_ids.map(Number) : [];
    const byId = new Map(rows.map((r) => [Number(r.item_id), r]));
    const ordered = order.length
      ? order.map((id) => byId.get(id)).filter(Boolean)
      : rows;

    const hr = document.createElement("tr");
    hr.innerHTML = ["<th>Status</th>", "<th>Title</th>", "<th>Duration</th>", "<th>Parts</th>"]
      .concat(columns.map((c) => {
        const tintIdx = tintIndexForColumn(allColumns, c);
        const selected = isColumnPlayerSelected(c);
        return `<th class="part-tint-${tintIdx}${selected ? " part-selected" : ""}">${escapeHtml(c.title || c.key || "")}</th>`;
      }))
      .join("");
    head.replaceChildren(hr);
    body.replaceChildren();
    for (const r of ordered) {
      const id = Number(r.item_id);
      const tr = document.createElement("tr");
      const isSkipped = skipped.has(id);
      const isCurrent = currentId === id;
      const isNext = nextId === id;
      const isPlayed = played.has(id);
      if (isSkipped) tr.className = "row-skipped";
      else if (isCurrent) tr.className = "row-current";
      else if (isNext) tr.className = "row-next";
      else if (isPlayed) tr.className = "row-played";
      const cells = byItem.get(id);
      const cellHtml = columns.map((c) => {
        const sourceIndex = allColumns.indexOf(c);
        const val = cells && Array.isArray(cells.cells) ? cells.cells[sourceIndex] : "";
        const tintIdx = tintIndexForColumn(allColumns, c);
        const selected = isColumnPlayerSelected(c);
        return `<td class="part-tint-${tintIdx}${selected ? " part-selected" : ""}">${escapeHtml(val || "")}</td>`;
      });
      tr.innerHTML = [
        statusBadgeCell(isSkipped, isCurrent, isNext, isPlayed),
        `<td>${escapeHtml(r.title || "")}</td>`,
        `<td>${fmtDuration(r.duration_seconds)}</td>`,
        `<td>${escapeHtml(String(r.part_count ?? "—"))}</td>`,
        ...cellHtml,
      ].join("");
      body.appendChild(tr);
    }
  }

  function tintIndexForColumn(allColumns, col) {
    let canonical = 0;
    for (let i = 0; i < allColumns.length; i++) {
      const c = allColumns[i];
      if (c === col) {
        return (c.player_id != null ? canonical : i) % 8;
      }
      if (c.player_id != null) canonical++;
    }
    return 0;
  }

  function isColumnPlayerSelected(col) {
    return col && col.player_id != null && highlightPlayers.has(Number(col.player_id));
  }

  function listedPlayers() {
    const names = new Map();
    for (const c of lastCards) {
      names.set(Number(c.player_id), String(c.player_name || `Player ${c.player_id}`));
    }
    const sheet = lastSnapshot && lastSnapshot.parts_sheet ? lastSnapshot.parts_sheet : {};
    const cols = Array.isArray(sheet.columns) ? sheet.columns : [];
    for (const c of cols) {
      if (c.player_id == null) continue;
      const id = Number(c.player_id);
      if (!names.has(id)) names.set(id, String(c.title || `Player ${id}`));
    }
    return [...names.entries()]
      .sort((a, b) => a[1].localeCompare(b[1]))
      .map(([id, name]) => ({ id, name }));
  }

  function setPlayerHighlighted(pid, on) {
    if (on) highlightPlayers.add(pid);
    else highlightPlayers.delete(pid);
    lastPlayerFilterKey = "";
    renderPlayers(lastCards);
    if (lastSnapshot) renderParts(lastSnapshot);
    else renderPartsPlayerFilter();
    drawGrid();
  }

  function renderPartsPlayerFilter() {
    if (!partsPlayerFilter || !partsPlayerSummary) return;
    const players = listedPlayers();
    const key = players.map((p) => p.id + ":" + p.name).join("|")
      + "#" + [...highlightPlayers].sort().join(",");
    if (key === lastPlayerFilterKey) return;
    lastPlayerFilterKey = key;
    partsPlayerFilter.replaceChildren();
    if (!players.length) {
      partsPlayerSummary.textContent = "Players: —";
      return;
    }
    const selectedCount = players.filter((p) => highlightPlayers.has(p.id)).length;
    partsPlayerSummary.textContent = selectedCount === 0 || selectedCount === players.length
      ? "Players: All"
      : `Players: ${selectedCount}`;
    for (const p of players) {
      const label = document.createElement("label");
      const cb = document.createElement("input");
      cb.type = "checkbox";
      cb.checked = highlightPlayers.has(p.id);
      cb.addEventListener("change", () => setPlayerHighlighted(p.id, cb.checked));
      label.appendChild(cb);
      label.appendChild(document.createTextNode(p.name));
      partsPlayerFilter.appendChild(label);
    }
  }

  function instrumentsMarkdown(entry) {
    const name = entry && entry.player_name ? String(entry.player_name) : "Player";
    const inst = Array.isArray(entry && entry.instruments) ? entry.instruments : [];
    const lines = [`**${name}**`];
    if (!inst.length) lines.push("- (none)");
    else for (const item of inst) lines.push(`- ${item}`);
    return lines.join("\n") + "\n";
  }

  function visibleInstrumentsNeeded() {
    const sheet = lastSnapshot && lastSnapshot.parts_sheet ? lastSnapshot.parts_sheet : {};
    const list = Array.isArray(sheet.instruments_needed) ? sheet.instruments_needed : [];
    const selectedOnly = !!(partsSelectedOnly && partsSelectedOnly.checked);
    if (!selectedOnly || highlightPlayers.size === 0) return list;
    return list.filter((n) => highlightPlayers.has(Number(n.player_id)));
  }

  function openInstrumentsDialog() {
    if (!instrumentsDialog || !instrumentsBody) return;
    const needed = visibleInstrumentsNeeded();
    instrumentsBody.replaceChildren();
    if (!needed.length) {
      const empty = document.createElement("p");
      empty.className = "hint";
      empty.textContent = "No instruments listed.";
      instrumentsBody.appendChild(empty);
    } else {
      for (const n of needed) {
        const card = document.createElement("div");
        card.className = "instruments-card";
        const h = document.createElement("h3");
        h.textContent = n.player_name || "Player";
        const ul = document.createElement("ul");
        const inst = Array.isArray(n.instruments) ? n.instruments : [];
        if (!inst.length) {
          const li = document.createElement("li");
          li.textContent = "(none)";
          ul.appendChild(li);
        } else {
          for (const item of inst) {
            const li = document.createElement("li");
            li.textContent = item;
            ul.appendChild(li);
          }
        }
        const copy = document.createElement("button");
        copy.type = "button";
        copy.className = "btn";
        copy.textContent = "Copy";
        copy.addEventListener("click", () => {
          navigator.clipboard.writeText(instrumentsMarkdown(n)).catch(() => {});
        });
        card.appendChild(h);
        card.appendChild(ul);
        card.appendChild(copy);
        instrumentsBody.appendChild(card);
      }
    }
    if (typeof instrumentsDialog.showModal === "function") instrumentsDialog.showModal();
    else instrumentsDialog.setAttribute("open", "");
  }

  async function downloadZip() {
    const pin = (pinInput && pinInput.value ? pinInput.value : "").trim();
    if (!lastCode || !pin) return;
    try {
      const res = await fetch(`/api/sessions/${encodeURIComponent(lastCode)}/zip`, {
        headers: { "X-Zip-Passphrase": pin, Accept: "application/zip" },
      });
      if (!res.ok) {
        setStatus("Zip download failed.");
        return;
      }
      const blob = await res.blob();
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = zipDownloadFileName();
      document.body.appendChild(a);
      a.click();
      a.remove();
      URL.revokeObjectURL(url);
    } catch {
      setStatus("Zip download failed.");
    }
  }

  function escapeHtml(s) {
    return String(s)
      .replace(/&/g, "&amp;")
      .replace(/</g, "&lt;")
      .replace(/>/g, "&gt;")
      .replace(/"/g, "&quot;");
  }

  function renderPlayers(cards) {
    lastCards = Array.isArray(cards) ? cards : lastCards;
    const players = listedPlayers();
    playersList.replaceChildren();
    if (!players.length) {
      const empty = document.createElement("p");
      empty.className = "hint";
      empty.textContent = "No layout yet.";
      playersList.appendChild(empty);
      return;
    }
    for (const p of players) {
      const label = document.createElement("label");
      const cb = document.createElement("input");
      cb.type = "checkbox";
      cb.checked = highlightPlayers.has(p.id);
      cb.addEventListener("change", () => setPlayerHighlighted(p.id, cb.checked));
      label.appendChild(cb);
      label.appendChild(document.createTextNode(p.name));
      playersList.appendChild(label);
    }
  }

  function logicalToView(lx, ly) {
    return [
      (lx - X_MIN) * ppu + viewOffsetX,
      (ly - Y_MIN) * ppu + viewOffsetY,
    ];
  }

  function viewToLogical(vx, vy) {
    return [
      (vx - viewOffsetX) / ppu + X_MIN,
      (vy - viewOffsetY) / ppu + Y_MIN,
    ];
  }

  function cardBounds() {
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;
    for (const c of lastCards) {
      const x = Number(c.x);
      const y = Number(c.y);
      minX = Math.min(minX, x);
      minY = Math.min(minY, y);
      maxX = Math.max(maxX, x + CARD_W);
      maxY = Math.max(maxY, y + CARD_H);
    }
    return { minX, minY, maxX, maxY, w: maxX - minX, h: maxY - minY };
  }

  function fitCardsToView() {
    const vw = layoutViewport.clientWidth;
    const vh = layoutViewport.clientHeight;
    if (!lastCards.length || vw < 8 || vh < 8) {
      // Pane not visible yet — retry when Playback tab is shown.
      needsFit = true;
      return;
    }

    const b = cardBounds();
    const pad = 12;
    const ppuX = b.w > 0 ? (vw - pad * 2) / b.w : BASE_PPU;
    const ppuY = b.h > 0 ? (vh - pad * 2) / b.h : BASE_PPU;
    ppu = Math.max(MIN_PPU, Math.min(BASE_PPU, ppuX, ppuY));

    const centerX = (b.minX + b.maxX) / 2;
    const centerY = (b.minY + b.maxY) / 2;
    viewOffsetX = vw / 2 - (centerX - X_MIN) * ppu;
    viewOffsetY = vh / 2 - (centerY - Y_MIN) * ppu;
    needsFit = false;
  }

  function refreshLayoutCanvas() {
    const vw = layoutViewport.clientWidth;
    const vh = layoutViewport.clientHeight;
    const sizeChanged = vw !== lastCanvasW || vh !== lastCanvasH;
    lastCanvasW = vw;
    lastCanvasH = vh;
    resizeCanvas();
    if (needsFit || sizeChanged) fitCardsToView();
    drawGrid();
  }

  function resizeCanvas() {
    const dpr = window.devicePixelRatio || 1;
    const w = layoutViewport.clientWidth || 1;
    const h = layoutViewport.clientHeight || 1;
    canvas.width = Math.floor(w * dpr);
    canvas.height = Math.floor(h * dpr);
    canvas.style.width = `${w}px`;
    canvas.style.height = `${h}px`;
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  }

  function cardsOverlap(a, b) {
    const ax = Number(a.x);
    const ay = Number(a.y);
    const bx = Number(b.x);
    const by = Number(b.y);
    return !(ax + CARD_W <= bx || bx + CARD_W <= ax || ay + CARD_H <= by || by + CARD_H <= ay);
  }

  function fitText(context, text, maxWidth, fontFamily, startPx, minPx) {
    let size = startPx;
    while (size >= minPx) {
      context.font = `${size}px ${fontFamily}`;
      if (context.measureText(text).width <= maxWidth) return size;
      size -= 1;
    }
    context.font = `${minPx}px ${fontFamily}`;
    return minPx;
  }

  function drawGrid() {
    if (!ctx) return;
    const w = layoutViewport.clientWidth || 1;
    const h = layoutViewport.clientHeight || 1;
    if (canvas.width === 0) resizeCanvas();

    ctx.clearRect(0, 0, w, h);
    ctx.fillStyle = COLORS.surface;
    ctx.fillRect(0, 0, w, h);

    if (!lastCards.length) {
      layoutEmpty.classList.remove("hidden");
      return;
    }
    layoutEmpty.classList.add("hidden");

    // Dotted graph paper (matches Qt paintEvent)
    const [lx0, ly0] = viewToLogical(0, 0);
    const [lx1, ly1] = viewToLogical(w, h);
    ctx.fillStyle = COLORS.outline;
    for (let lx = Math.floor(lx0); lx <= lx1 + 1; lx++) {
      for (let ly = Math.floor(ly0); ly <= ly1 + 1; ly++) {
        const [vx, vy] = logicalToView(lx, ly);
        if (vx >= 0 && vx < w && vy >= 0 && vy < h) {
          ctx.fillRect(Math.round(vx), Math.round(vy), 1, 1);
        }
      }
    }

    const fontFamily = getComputedStyle(document.body).fontFamily || "sans-serif";

    for (const card of lastCards) {
      const [vx, vy] = logicalToView(Number(card.x), Number(card.y));
      const cw = CARD_W * ppu;
      const ch = CARD_H * ppu;
      const scale = ppu / BASE_PPU;
      const overlap = lastCards.some((other) => other !== card && cardsOverlap(card, other));

      ctx.fillStyle = COLORS.surface;
      ctx.strokeStyle = overlap ? COLORS.error : COLORS.outline;
      ctx.lineWidth = overlap ? 2 : 1;
      roundRect(ctx, vx, vy, cw, ch, 4);
      ctx.fill();
      ctx.stroke();

      if (highlightPlayers.has(Number(card.player_id))) {
        ctx.strokeStyle = COLORS.primary;
        ctx.lineWidth = 2;
        roundRect(ctx, vx + 1, vy + 1, cw - 2, ch - 2, 4);
        ctx.stroke();
      }

      const margin = Math.max(1, Math.round(2 * scale));
      const innerL = vx + margin;
      const innerT = vy + margin;
      const innerW = cw - margin * 2;
      const innerR = innerL + innerW;
      let y = innerT;

      const partDup = !!card.part_duplicate;
      const instChanged = !!card.instrument_changed_from_prior_in_set;
      const instWarn = !!card.instrument_warning;
      const useHeader = !!card.use_setlist_player_header;
      const partText = displayPartNumber(card.part_number);

      // Row 1: player name (+ gutters when setlist header)
      ctx.textBaseline = "middle";
      const nameSize = Math.max(7, Math.round(12 * scale));
      ctx.font = `${nameSize}px ${fontFamily}`;
      const lineH = nameSize + Math.max(2, Math.round(4 * scale));
      if (useHeader) {
        const gutter = ctx.measureText("999").width + 6;
        ctx.fillStyle = COLORS.textSecondary;
        ctx.textAlign = "left";
        ctx.fillText(String(card.neighbor_prev_part_label || ""), innerL, y + lineH / 2);
        ctx.textAlign = "right";
        ctx.fillText(String(card.neighbor_next_part_label || ""), innerR, y + lineH / 2);
        ctx.fillStyle = COLORS.onSurface;
        ctx.textAlign = "center";
        const name = String(card.player_name || "");
        fitText(ctx, name, Math.max(1, innerW - 2 * gutter), fontFamily, nameSize, 6);
        ctx.fillText(name, innerL + innerW / 2, y + lineH / 2, Math.max(1, innerW - 2 * gutter));
      } else {
        ctx.fillStyle = COLORS.onSurface;
        ctx.textAlign = "center";
        const name = String(card.player_name || "");
        fitText(ctx, name, innerW, fontFamily, nameSize, 6);
        ctx.fillText(name, innerL + innerW / 2, y + lineH / 2, innerW);
      }
      y += lineH + Math.max(1, Math.round(2 * scale));

      // Row 2: large bold part number
      let partColor = COLORS.onSurface;
      if (partDup) partColor = COLORS.dup;
      else if (instChanged) partColor = COLORS.warning;
      ctx.fillStyle = partColor;
      ctx.textAlign = "center";
      const big = fitText(ctx, partText, innerW, fontFamily, Math.max(12, Math.round(26 * scale)), 8);
      ctx.font = `bold ${big}px ${fontFamily}`;
      const bigH = big + Math.max(2, Math.round(4 * scale));
      ctx.fillText(partText, innerL + innerW / 2, y + bigH / 2, innerW);
      y += bigH + Math.max(1, Math.round(2 * scale));

      // Row 3: instrument
      if (partDup) ctx.fillStyle = COLORS.dup;
      else if (instWarn) ctx.fillStyle = COLORS.warning;
      else ctx.fillStyle = COLORS.onSurface;
      const inst = String(card.instrument_name || "");
      fitText(ctx, inst, innerW, fontFamily, Math.max(7, Math.round(11 * scale)), 6);
      const instH = Math.max(10, Math.round(14 * scale));
      ctx.fillText(inst, innerL + innerW / 2, y + instH / 2, innerW);
      y += instH + Math.max(1, Math.round(2 * scale));

      // Row 4: part name
      ctx.fillStyle = partDup ? COLORS.dup : COLORS.onSurface;
      const pname = String(card.part_name || "");
      const partNameH = Math.max(8, Math.round(12 * scale));
      fitText(ctx, pname, innerW, fontFamily, Math.max(6, Math.round(10 * scale)), 6);
      ctx.fillText(pname, innerL + innerW / 2, y + partNameH / 2, innerW);
    }
  }

  function roundRect(context, x, y, w, h, r) {
    const radius = Math.min(r, w / 2, h / 2);
    context.beginPath();
    context.moveTo(x + radius, y);
    context.arcTo(x + w, y, x + w, y + h, radius);
    context.arcTo(x + w, y + h, x, y + h, radius);
    context.arcTo(x, y + h, x, y, radius);
    context.arcTo(x, y, x + w, y, radius);
    context.closePath();
  }

  // Pan
  canvas.addEventListener("pointerdown", (e) => {
    canvas.setPointerCapture(e.pointerId);
    panStart = { x: e.clientX, y: e.clientY, ox: viewOffsetX, oy: viewOffsetY };
    canvas.classList.add("dragging");
  });
  canvas.addEventListener("pointermove", (e) => {
    if (!panStart) return;
    viewOffsetX = panStart.ox + (e.clientX - panStart.x);
    viewOffsetY = panStart.oy + (e.clientY - panStart.y);
    drawGrid();
  });
  function endPan() {
    panStart = null;
    canvas.classList.remove("dragging");
  }
  canvas.addEventListener("pointerup", endPan);
  canvas.addEventListener("pointercancel", endPan);

  window.addEventListener("resize", () => {
    if (!panels.playback.classList.contains("active")) return;
    refreshLayoutCanvas();
  });

  if (typeof ResizeObserver === "function" && layoutViewport) {
    const ro = new ResizeObserver(() => {
      if (!panels.playback.classList.contains("active")) return;
      refreshLayoutCanvas();
    });
    ro.observe(layoutViewport);
  }

  function clampGridPaneHeight(px) {
    const total = playbackSplit ? playbackSplit.clientHeight : 0;
    const handle = splitHandle ? splitHandle.offsetHeight : 8;
    if (total < GRID_PANE_MIN + SETLIST_PANE_MIN + handle) {
      return Math.max(GRID_PANE_MIN, px);
    }
    const max = Math.max(GRID_PANE_MIN, total - SETLIST_PANE_MIN - handle);
    return Math.max(GRID_PANE_MIN, Math.min(max, px));
  }

  function applyGridPaneHeight(px) {
    if (!gridCard) return;
    const h = clampGridPaneHeight(px);
    gridCard.style.flexBasis = `${h}px`;
    try {
      localStorage.setItem(GRID_H_STORAGE, String(Math.round(h)));
    } catch {
      /* ignore */
    }
  }

  function restoreGridPaneHeight() {
    if (!gridCard) return;
    let saved = NaN;
    try {
      saved = Number(localStorage.getItem(GRID_H_STORAGE));
    } catch {
      saved = NaN;
    }
    if (Number.isFinite(saved) && saved > 0) {
      applyGridPaneHeight(saved);
    }
  }

  function bindSplitHandle() {
    if (!splitHandle || !playbackSplit || !gridCard) return;
    let drag = null;

    splitHandle.addEventListener("pointerdown", (e) => {
      if (e.button !== 0 && e.pointerType === "mouse") return;
      splitHandle.setPointerCapture(e.pointerId);
      splitHandle.classList.add("dragging");
      drag = {
        startY: e.clientY,
        startH: gridCard.getBoundingClientRect().height,
      };
      e.preventDefault();
    });
    splitHandle.addEventListener("pointermove", (e) => {
      if (!drag) return;
      applyGridPaneHeight(drag.startH - (e.clientY - drag.startY));
    });
    function endSplitDrag() {
      drag = null;
      splitHandle.classList.remove("dragging");
    }
    splitHandle.addEventListener("pointerup", endSplitDrag);
    splitHandle.addEventListener("pointercancel", endSplitDrag);
    splitHandle.addEventListener("keydown", (e) => {
      const step = e.shiftKey ? 48 : 16;
      const cur = gridCard.getBoundingClientRect().height;
      if (e.key === "ArrowUp") {
        applyGridPaneHeight(cur + step);
        e.preventDefault();
      } else if (e.key === "ArrowDown") {
        applyGridPaneHeight(cur - step);
        e.preventDefault();
      } else if (e.key === "Home") {
        applyGridPaneHeight(GRID_PANE_MIN);
        e.preventDefault();
      } else if (e.key === "End") {
        applyGridPaneHeight(playbackSplit.clientHeight);
        e.preventDefault();
      }
    });
  }

  bindSplitHandle();
  restoreGridPaneHeight();

  recenterBtn.addEventListener("click", () => {
    fitCardsToView();
    drawGrid();
  });

  for (const btn of tabButtons) {
    btn.addEventListener("click", () => {
      if (btn.disabled) return;
      showTab(btn.dataset.tab);
    });
  }

  connectBtn.addEventListener("click", () => connect(setInput.value));
  disconnectBtn.addEventListener("click", () => disconnect());
  reconnectBtn.addEventListener("click", () => {
    if (lastCode) connect(lastCode);
    else connect(setInput.value);
  });
  if (downloadBtn) downloadBtn.addEventListener("click", () => downloadZip());
  if (pinInput) pinInput.addEventListener("input", () => updateDownloadButton());
  if (instrumentsBtn) instrumentsBtn.addEventListener("click", () => openInstrumentsDialog());
  if (partsSelectedOnly) {
    partsSelectedOnly.addEventListener("change", () => {
      if (lastSnapshot) renderParts(lastSnapshot);
    });
  }
  if (instrumentsClose && instrumentsDialog) {
    instrumentsClose.addEventListener("click", () => {
      if (typeof instrumentsDialog.close === "function") instrumentsDialog.close();
      else instrumentsDialog.removeAttribute("open");
    });
  }
  if (instrumentsCopyAll) {
    instrumentsCopyAll.addEventListener("click", () => {
      const needed = visibleInstrumentsNeeded();
      const text = needed.map(instrumentsMarkdown).join("\n");
      navigator.clipboard.writeText(text).catch(() => {});
    });
  }
  setInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter") connect(setInput.value);
  });

  connHost.textContent = location.host;

  const hashPin = pinFromFragment(location.hash);
  if (hashPin && pinInput) pinInput.value = hashPin;

  const params = new URLSearchParams(location.search);
  const initial = params.get("set") || params.get("code");
  if (initial && initial.trim().length >= 5) {
    setInput.value = initial.trim().toUpperCase();
    connect(setInput.value);
  }
})();
