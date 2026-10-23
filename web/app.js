const TOPIC_KEY = "kikiTopic";
const TOPIC_PATTERN = /^[A-Za-z0-9]{40}$/;
const MAX = 300;

const $ = (id) => document.getElementById(id);

async function send(topic, text) {
  try {
    const response = await fetch("https://ntfy.sh/" + topic, { method: "POST", body: text });
    return response.ok;
  } catch {
    return false;
  }
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
  $("send").disabled = text.trim() === "";
}

function setStatus(text, ok) {
  const status = $("status");
  status.textContent = text;
  status.className = "status " + (ok ? "ok" : "error");
  status.hidden = false;
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

$("send").addEventListener("click", async () => {
  const text = $("message").value.trim();
  if (!text) return;
  $("send").disabled = true;
  const ok = await send(localStorage.getItem(TOPIC_KEY), text);
  if (ok) {
    $("message").value = "";
    setStatus("✓ Envoyé", true);
  } else {
    setStatus("✗ Pas envoyé, réessaie", false);
  }
  updateCount();
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
