const TOPIC_KEY = "kikiTopic";
const HISTORY_KEY = "kikiHistory";
const TOPIC_PATTERN = /^[A-Za-z0-9]{40}$/;
const MAX = 300;
const HISTORY_SIZE = 10;
const STALE_MS = 20 * 60 * 1000; // the TV says "still here" every 15 min
const STICKER_SIZE = 400;
const STICKER_BORDER = 14;

const $ = (id) => document.getElementById(id);
const topic = () => localStorage.getItem(TOPIC_KEY);
const replyTopic = () => topic() + "-r";
const hhmm = (sec) => new Date(sec * 1000).toLocaleTimeString("fr-FR", { hour: "2-digit", minute: "2-digit" });

let stickerBlob = null;
let replyStream = null;
let tvStatus = null; // { on, time }

// ---------- sending ----------

/** Sends a text, a sticker, or both. Returns the ntfy message id, or null on failure. */
async function send(text, sticker) {
  try {
    let response;
    if (sticker) {
      const url = new URL("https://ntfy.sh/" + topic());
      url.searchParams.set("filename", "sticker.png");
      if (text) url.searchParams.set("message", text);
      response = await fetch(url, { method: "PUT", body: sticker });
    } else {
      response = await fetch("https://ntfy.sh/" + topic(), { method: "POST", body: text });
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

async function deliver(text, sticker, label) {
  $("send").disabled = true;
  $("send-poll").disabled = true;
  const id = await send(text, sticker);
  if (id) {
    addHistory({ id, label, sent: Math.floor(Date.now() / 1000) });
    setStatus("✓ Envoyé", true);
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

function handleLine(raw) {
  try {
    const event = JSON.parse(raw);
    if (event.event === "message") applyReply(JSON.parse(event.message));
  } catch {
    // not one of ours
  }
}

async function loadReplies() {
  try {
    const response = await fetch(`https://ntfy.sh/${replyTopic()}/json?poll=1&since=12h`);
    const text = await response.text();
    text.split("\n").filter(Boolean).forEach(handleLine);
  } catch {
    // offline: the live stream will catch up
  }
  renderTv();
  replyStream?.close();
  replyStream = new EventSource(`https://ntfy.sh/${replyTopic()}/sse`);
  replyStream.onmessage = (e) => handleLine(e.data);
}

function renderTv() {
  const tv = $("tv");
  if (!tvStatus) {
    tv.textContent = "⚪ État inconnu";
    return;
  }
  const age = Date.now() - tvStatus.time * 1000;
  if (!tvStatus.on) {
    tv.textContent = `⚫ TV éteinte depuis ${hhmm(tvStatus.time)}`;
  } else if (age > STALE_MS) {
    tv.textContent = `⚪ Pas de nouvelles depuis ${Math.round(age / 60000)} min`;
  } else {
    tv.textContent = "🟢 TV allumée";
  }
}

// ---------- screens ----------

function showSetup() {
  $("compose").hidden = true;
  $("setup").hidden = false;
  $("topic").value = "";
  $("topic").focus();
}

function showCompose() {
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
  if (!TOPIC_PATTERN.test(value)) {
    $("topic-error").hidden = false;
    return;
  }
  $("topic-error").hidden = true;
  localStorage.setItem(TOPIC_KEY, value);
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
  if (await deliver(text, stickerBlob, label)) {
    $("message").value = "";
    clearSticker();
  }
});

$("send-poll").addEventListener("click", async () => {
  const q = $("question").value.trim();
  const list = choices();
  if (!q || list.length < 2) return;
  const body = JSON.stringify({ kiki: "poll", q, choices: list });
  if (await deliver(body, null, `📊 ${q}`)) {
    $("question").value = "";
    document.querySelectorAll(".choice").forEach((c) => (c.value = ""));
    updateButtons();
  }
});

document.querySelectorAll(".chip").forEach((chip) => {
  chip.addEventListener("click", () => deliver(chip.dataset.text, null, chip.dataset.text));
});

$("change").addEventListener("click", (event) => {
  event.preventDefault();
  localStorage.removeItem(TOPIC_KEY);
  replyStream?.close();
  showSetup();
});

setInterval(renderTv, 60_000);

if (TOPIC_PATTERN.test(topic() || "")) {
  showCompose();
} else {
  showSetup();
}
