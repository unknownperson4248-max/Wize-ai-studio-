// JARVIS API client (talks to localhost:3200 Termux server)

const BASE = "/api";

export async function health() {
  const res = await fetch(`${BASE}/health`);
  return res.json();
}

export async function chat(message, history, model) {
  const res = await fetch(`${BASE}/chat`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ message, history, model }),
  });
  return res.json();
}

export async function tool(name, args) {
  const res = await fetch(`${BASE}/tool`, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ name, arguments: args }),
  });
  return res.json();
}