const TOPIC_KEY = "kikiTopic";
const TOPIC_PATTERN = /^[A-Za-z0-9]{40}$/;
const MAX = 300;
const STICKER_SIZE = 400;
const STICKER_BORDER = 14;

const $ = (id) => document.getElementById(id);

let stickerBlob = null;

/** Sends a text, a sticker, or both. ntfy takes the image as body and the text as a query parameter. */
async function send(topic, text, sticker) {
  try {
    let response;
    if (sticker) {
      const url = new URL("https://ntfy.sh/" + topic);
      url.searchParams.set("filename", "sticker.png");
      if (text) url.searchParams.set("message", text);
      response = await fetch(url, { method: "PUT", body: sticker });
    } else {
      response = await fetch("https://ntfy.sh/" + topic, { method: "POST", body: text });
    }
    return response.ok;
  } catch {
    return false;
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

function showSetup() {
  $("compose").hidden = true;
  $("setup").hidden = false;
  $("topic").value = "";
  $("topic").focus();
}

function showCompose() {
  $("setup").hidden = true;
  $("compose").hidden = false;
  updateCount();
}

function updateCount() {
  const text = $("message").value;
  $("count").textContent = `${[...text].length}/${MAX}`;
  $("send").disabled = text.trim() === "" && !stickerBlob;
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
  updateCount();
}

async function deliver(text, sticker) {
  $("send").disabled = true;
  const ok = await send(localStorage.getItem(TOPIC_KEY), text, sticker);
  if (ok) {
    $("message").value = "";
    clearSticker();
    setStatus("✓ Envoyé", true);
  } else {
    setStatus("✗ Pas envoyé, réessaie", false);
  }
  updateCount();
}

$("save").addEventListener("click", () => {
  const topic = $("topic").value.trim();
  if (!TOPIC_PATTERN.test(topic)) {
    $("topic-error").hidden = false;
    return;
  }
  $("topic-error").hidden = true;
  localStorage.setItem(TOPIC_KEY, topic);
  showCompose();
});

$("message").addEventListener("input", () => {
  $("status").hidden = true;
  updateCount();
});

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
  updateCount();
});

$("remove").addEventListener("click", clearSticker);

$("send").addEventListener("click", () => {
  const text = $("message").value.trim();
  if (!text && !stickerBlob) return;
  deliver(text, stickerBlob);
});

document.querySelectorAll(".chip").forEach((chip) => {
  chip.addEventListener("click", () => deliver(chip.dataset.text, null));
});

$("change").addEventListener("click", (event) => {
  event.preventDefault();
  localStorage.removeItem(TOPIC_KEY);
  showSetup();
});

if (TOPIC_PATTERN.test(localStorage.getItem(TOPIC_KEY) || "")) {
  showCompose();
} else {
  showSetup();
}
