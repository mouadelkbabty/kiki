const CODE_KEY = "kikiCode";
const HISTORY_KEY = "kikiHistory2";
const OLD_KEYS = ["kikiTopic", "kikiHistory"]; // before encryption: wiped on load
const CODE_PATTERN = /^[A-Za-z0-9]{32}$/;
const MAX = 300;
const HISTORY_SIZE = 10;
const PING_WAIT_MS = 8000;
const STICKER_SIZE = 400;
const STICKER_BORDER = 14;

const $ = (id) => document.getElementById(id);
const hhmm = (sec) => new Date(sec * 1000).toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });
const now = () => Math.floor(Date.now() / 1000);

let stickerBlob = null;
let replyStream = null;
let tvStatus = null; // { on, time }
let pingSentAt = 0;
let pingTimer = null;
let secret = null; // { topic, replyTopic, key }

OLD_KEYS.forEach((k) => localStorage.removeItem(k));

// ---------- encryption (same scheme as the TV: AES-256-GCM, 12-byte IV, "k1:" + base64) ----------

async function sha256(text) {
  return new Uint8Array(await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text)));
}

async function deriveSecret(code) {
  const hex = [...(await sha256("kiki-topic:" + code))].map((b) => b.toString(16).padStart(2, "0")).join("");
  const key = await crypto.subtle.importKey("raw", await sha256("kiki-key:" + code), "AES-GCM", false, [
    "encrypt",
    "decrypt",
  ]);
  const topic = hex.slice(0, 40);
  return { topic, replyTopic: topic + "-r", key };
}

async function seal(bytes) {
  const iv = crypto.getRandomValues(new Uint8Array(12));
  const cipher = new Uint8Array(await crypto.subtle.encrypt({ name: "AES-GCM", iv }, secret.key, bytes));
  const out = new Uint8Array(iv.length + cipher.length);
  out.set(iv);
  out.set(cipher, iv.length);
  return out;
}

async function sealText(text) {
  const bytes = await seal(new TextEncoder().encode(text));
  let binary = "";
  bytes.forEach((b) => (binary += String.fromCharCode(b)));
  return "k1:" + btoa(binary);
}

async function openText(sealed) {
  if (!sealed.startsWith("k1:")) return null;
  try {
    const bytes = Uint8Array.from(atob(sealed.slice(3)), (c) => c.charCodeAt(0));
    const plain = await crypto.subtle.decrypt({ name: "AES-GCM", iv: bytes.slice(0, 12) }, secret.key, bytes.slice(12));
    return new TextDecoder().decode(plain);
  } catch {
    return null;
  }
}

// ---------- sending ----------

/**
 * Publishes a sealed envelope (and optional sealed sticker). Returns the id or null.
 * Texts are never cached by ntfy. A sticker must be stored for the TV to download it: ntfy then
 * keeps the sealed file (unreadable without the code) for at most ~3 hours.
 */
async function publish(envelope, sticker) {
  try {
    const url = new URL("https://ntfy.sh/" + secret.topic);
    if (!sticker) url.searchParams.set("cache", "no");
    const message = await sealText(JSON.stringify(envelope));
    let response;
    if (sticker) {
      url.searchParams.set("filename", "sticker.bin");
      url.searchParams.set("message", message);
      const sealed = await seal(new Uint8Array(await sticker.arrayBuffer()));
      response = await fetch(url, { method: "PUT", body: sealed });
    } else {
      response = await fetch(url, { method: "POST", body: message });
    }
    if (!response.ok) return null;
    return (await response.json()).id;
  } catch {
    return null;
  }
}

/** Crops the photo to a circle with a white outline, like a chat sticker. Returns a PNG blob. */
async function makeSticker(file) {
  const bitmap = await createImageBitmap(file);
  const canvas = document.createElement("canvas");
  canvas.width = canvas.height = STICKER_SIZE;
  const ctx = canvas.getContext("2d");
  const center = STICKER_SIZE / 2;
  const radius = center - STICKER_BORDER;

  ctx.beginPath();
  ctx.arc(center, center, center - 2, 0, Math.PI * 2);
  ctx.fillStyle = "#fff";
  ctx.fill();

  ctx.save();
  ctx.beginPath();
  ctx.arc(center, center, radius, 0, Math.PI * 2);
  ctx.clip();
  const side = Math.min(bitmap.width, bitmap.height);
  const sx = (bitmap.width - side) / 2;
  const sy = (bitmap.height - side) / 2;
  ctx.drawImage(bitmap, sx, sy, side, side, center - radius, center - radius, radius * 2, radius * 2);
  ctx.restore();
  bitmap.close?.();

  return new Promise((resolve) => canvas.toBlob(resolve, "image/png"));
}

async function deliver(envelope, sticker, label) {
  $("send").disabled = true;
  $("send-poll").disabled = true;
  const id = await publish(envelope, sticker);
  if (id) {
    addHistory({ id, label, sent: now() });
    setStatus("✓ Envoyé 🔐", true);
  } else {
    setStatus("✗ Pas envoyé, réessaie", false);
  }
  updateButtons();
  return id;
}

// ---------- history & replies ----------

function loadHistory() {
  try {
    return JSON.parse(localStorage.getItem(HISTORY_KEY)) || [];
  } catch {
    return [];
  }
}

function saveHistory(items) {
  localStorage.setItem(HISTORY_KEY, JSON.stringify(items.slice(0, HISTORY_SIZE)));
  renderHistory();
}

function addHistory(item) {
  saveHistory([item, ...loadHistory()]);
}

function renderHistory() {
  const list = $("history");
  list.innerHTML = "";
  for (const item of loadHistory()) {
    const li = document.createElement("li");
    const what = document.createElement("span");
    what.className = "what";
    what.textContent = item.label;
    const state = document.createElement("span");
    state.className = "state";
    if (item.vote) {
      state.textContent = `Réponse : ${item.vote} · ${hhmm(item.seen)}`;
      state.classList.add("ok");
    } else if (item.seen) {
      state.textContent = `✓ vu à ${hhmm(item.seen)}`;
      state.classList.add("ok");
    } else {
      state.textContent = `envoyé à ${hhmm(item.sent)}`;
    }
    li.append(what, state);
    list.append(li);
  }
}

function applyReply(reply) {
  if (reply.type === "status") {
    if (!tvStatus || reply.time >= tvStatus.time) tvStatus = { on: reply.state === "on", time: reply.time };
    renderTv();
    return;
  }
  const items = loadHistory();
  const item = items.find((i) => i.id === reply.id);
  if (!item) return;
  item.seen = reply.time;
  if (reply.type === "vote") item.vote = reply.choice;
  saveHistory(items);
}

async function handleLine(raw) {
  try {
    const event = JSON.parse(raw);
    if (event.event !== "message") return;
    const plain = await openText(event.message);
    if (plain) applyReply(JSON.parse(plain));
  } catch {
    // not one of ours
  }
}

async function loadReplies() {
  try {
    const response = await fetch(`https://ntfy.sh/${secret.replyTopic}/json?poll=1&since=12h`);
    for (const line of (await response.text()).split("\n").filter(Boolean)) await handleLine(line);
  } catch {
    // offline: the live stream will catch up
  }
  replyStream?.close();
  replyStream = new EventSource(`https://ntfy.sh/${secret.replyTopic}/sse`);
  replyStream.onmessage = (e) => handleLine(e.data);
  replyStream.onopen = ping;
}

/** Asks the TV "are you on?"; it answers with its status within seconds if it is. */
async function ping() {
  pingSentAt = now();
  renderTv();
  clearTimeout(pingTimer);
  pingTimer = setTimeout(renderTv, PING_WAIT_MS);
  await publish({ type: "ping" }, null);
}

function renderTv() {
  const tv = $("tv");
  const answered = tvStatus && tvStatus.on && tvStatus.time >= pingSentAt - 2;
  if (answered) {
    tv.textContent = "🟢 TV allumée";
  } else if (now() - pingSentAt < PING_WAIT_MS / 1000) {
    tv.textContent = "⏳ Je regarde…";
  } else if (tvStatus && !tvStatus.on) {
    tv.textContent = `⚫ TV éteinte depuis ${hhmm(tvStatus.time)}`;
  } else {
    tv.textContent = "⚫ TV éteinte ou injoignable";
  }
}

// ---------- screens ----------

function showSetup() {
  $("compose").hidden = true;
  $("setup").hidden = false;
  $("topic").value = "";
  $("topic").focus();
}

async function showCompose() {
  secret = await deriveSecret(localStorage.getItem(CODE_KEY));
  $("setup").hidden = true;
  $("compose").hidden = false;
  renderHistory();
  updateButtons();
  loadReplies();
}

function choices() {
  return [...document.querySelectorAll(".choice")].map((c) => c.value.trim()).filter(Boolean);
}

function updateButtons() {
  const text = $("message").value;
  $("count").textContent = `${[...text].length}/${MAX}`;
  $("send").disabled = text.trim() === "" && !stickerBlob;
  $("send-poll").disabled = $("question").value.trim() === "" || choices().length < 2;
}

function setStatus(text, ok) {
  const status = $("status");
  status.textContent = text;
  status.className = "status " + (ok ? "ok" : "error");
  status.hidden = false;
}

function clearSticker() {
  stickerBlob = null;
  $("preview").hidden = true;
  $("sticker").removeAttribute("src");
  $("photo").value = "";
  updateButtons();
}

function selectTab(poll) {
  $("tab-message").classList.toggle("active", !poll);
  $("tab-poll").classList.toggle("active", poll);
  $("message-pane").hidden = poll;
  $("poll-pane").hidden = !poll;
  $("status").hidden = true;
}

// ---------- events ----------

$("save").addEventListener("click", () => {
  const value = $("topic").value.trim();
  if (!CODE_PATTERN.test(value)) {
    $("topic-error").hidden = false;
    return;
  }
  $("topic-error").hidden = true;
  localStorage.setItem(CODE_KEY, value);
  localStorage.removeItem(HISTORY_KEY);
  showCompose();
});

$("tab-message").addEventListener("click", () => selectTab(false));
$("tab-poll").addEventListener("click", () => selectTab(true));

document.querySelectorAll("#message, #question, .choice").forEach((el) =>
  el.addEventListener("input", () => {
    $("status").hidden = true;
    updateButtons();
  }),
);

$("photo").addEventListener("change", async () => {
  const file = $("photo").files[0];
  if (!file) return;
  try {
    stickerBlob = await makeSticker(file);
    $("sticker").src = URL.createObjectURL(stickerBlob);
    $("preview").hidden = false;
    $("status").hidden = true;
  } catch {
    clearSticker();
    setStatus("✗ Photo illisible", false);
  }
  updateButtons();
});

$("remove").addEventListener("click", clearSticker);

$("send").addEventListener("click", async () => {
  const text = $("message").value.trim();
  if (!text && !stickerBlob) return;
  const label = (stickerBlob ? "🖼️ " : "") + (text || "Sticker");
  const envelope = { type: "msg", text: text || null, sticker: !!stickerBlob };
  if (await deliver(envelope, stickerBlob, label)) {
    $("message").value = "";
    clearSticker();
  }
});

$("send-poll").addEventListener("click", async () => {
  const q = $("question").value.trim();
  const list = choices();
  if (!q || list.length < 2) return;
  if (await deliver({ type: "poll", q, choices: list }, null, `📊 ${q}`)) {
    $("question").value = "";
    document.querySelectorAll(".choice").forEach((c) => (c.value = ""));
    updateButtons();
  }
});

document.querySelectorAll(".chip").forEach((chip) => {
  chip.addEventListener("click", () => deliver({ type: "msg", text: chip.dataset.text, sticker: false }, null, chip.dataset.text));
});

$("change").addEventListener("click", (event) => {
  event.preventDefault();
  localStorage.removeItem(CODE_KEY);
  localStorage.removeItem(HISTORY_KEY);
  replyStream?.close();
  showSetup();
});

document.addEventListener("visibilitychange", () => {
  if (!document.hidden && secret) ping();
});

if (CODE_PATTERN.test(localStorage.getItem(CODE_KEY) || "")) {
  showCompose();
} else {
  showSetup();
}
