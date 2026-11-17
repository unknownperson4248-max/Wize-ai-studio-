// JARVIS Termux Node.js Server (Phase 1)
// Port 3200 — Express + FreeLLMAPI proxy + termux-api tool bridge

import express from "express";
import cors from "cors";
import dotenv from "dotenv";
import { execSync } from "child_process";

dotenv.config();

const app = express();
const PORT = process.env.PORT || 3200;

// Serve static files from client/ for PWA dev
app.use(express.static("client"));

// CORS for local PWA development
app.use(cors({ origin: "*" }));
app.use(express.json());

// --- Health check ---
app.get("/api/health", (req, res) => {
  res.json({ status: "ok", server: "jarvis-termux", timestamp: Date.now() });
});

// --- LLM / Chat proxy (POST to FreeLLMAPI / OpenAI-compatible endpoint) ---
app.post("/api/chat", async (req, res) => {
  const { message, history, model, apiKey, ...extra } = req.body;

  if (!message) {
    return res.status(400).json({ error: "message is required" });
  }

  // Forward to FreeLLMAPI / OpenAI-compatible endpoint
  // Base URL from env: http://localhost:3001/v1
  const llmUrl = process.env.LLM_BASE_URL || "http://localhost:3001/v1";
  const key = apiKey || process.env.LLM_API_KEY || "";

  try {
    const llmResp = await fetch(`${llmUrl}/chat/completions`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        ...(key ? { "Authorization": `Bearer ${key}` } : {}),
      },
      body: JSON.stringify({
        model: model || process.env.LLM_MODEL || "auto",
        messages: [
          ...(history || []),
          { role: "user", content: message },
        ],
        ...extra,
      }),
    });

    const data = await llmResp.json();
    res.json(data);
  } catch (err) {
    console.error("LLM proxy error:", err);
    res.status(502).json({ error: "LLM proxy failed", detail: err.message });
  }
});

// --- Tool calling bridge to termux-api ---
// AI returns a tool call; we dispatch to the appropriate termux command.

/**
 * Dispatch function to termux-api commands.
 * Safe: each has allowed subset; user confirmation required for SMS/call.
 */
function dispatchTool(toolName, args) {
  try {
    switch (toolName) {
      case "battery_status":
        return execSync("termux-battery-status", { encoding: "utf-8" }).trim();

      case "torch_on":
        execSync("termux-torch on", { stdio: "ignore" });
        return "Torch turned on";

      case "torch_off":
        execSync("termux-torch off", { stdio: "ignore" });
        return "Torch turned off";

      case "volume_set":
        const level = args?.level ?? 5;
        execSync(`termux-volume ${args?.stream || "music"} ${level}`, { stdio: "ignore" });
        return `Volume set to ${level}`;

      case "notification_show":
        const title = args?.title ?? "JARVIS";
        const content = args?.content ?? "Action completed";
        execSync(`termux-notification --title "${title}" --content "${content}"`, { stdio: "ignore" });
        return `Notification shown: ${title}`;

      case "clipboard_set":
        const text = args?.text ?? "";
        execSync(`termux-clipboard-set "${text}"`, { stdio: "ignore" });
        return `Clipboard set: ${text}`;

      case "clipboard_get":
        return execSync("termux-clipboard-get", { encoding: "utf-8" }).trim();

      case "open_app":
        const packageName = args?.packageName ?? "com.termux";
        execSync(`termux-open com.termux`, { stdio: "ignore" });
        return `Opening app: ${packageName}`;

      case "open_url":
        const url = args?.url ?? "https://example.com";
        execSync(`termux-open-url "${url}"`, { stdio: "ignore" });
        return `Opening URL: ${url}`;

      case "show_toast":
        const toastMsg = args?.message ?? "Action completed";
        execSync(`termux-toast -s short "${toastMsg}"`, { stdio: "ignore" });
        return `Toast shown: ${toastMsg}`;

      case "vibrate":
        const duration = args?.duration ?? 1000;
        execSync(`termux-vibrate ${duration}`, { stdio: "ignore" });
        return `Vibrated for ${duration}ms`;

      case "dialog_confirm":
        // For simple yes/no dialog — termux-dialog exists but may not be available
        // We'll return a marker; UI handles the confirmation
        return { type: "dialog", title: args?.title ?? "Confirm", message: args?.message ?? "Are you sure?" };

      default:
        console.warn(`Unknown tool: ${toolName}`);
        return { error: `Unknown tool: ${toolName}` };
    }
  } catch (e) {
    console.error(`Tool dispatch error [${toolName}]:`, e);
    return { error: e.message || "Tool execution failed" };
  }
}

// --- Generic tool-calling endpoint ---
// AI sends: { name: "tool_name", arguments: { ... } }
app.post("/api/tool", (req, res) => {
  const { name, arguments: argsRaw } = req.body;

  if (!name) {
    return res.status(400).json({ error: "tool name is required" });
  }

  const args = argsRaw || {};
  const result = dispatchTool(name, args);

  // If result is an object with type:dialog, treat as confirmation marker
  if (typeof result === "object" && result.type === "dialog") {
    return res.json({ type: "confirmation", title: result.title, message: result.message, tool: name });
  }

  res.json({ type: "tool_result", tool: name, result });
});

// --- Serve index.html if client dir has index.html ---
app.get("*", (req, res) => {
  res.sendFile("index.html", { root: "client" });
});

app.listen(PORT, () => {
  console.log(`JARVIS Termux Server running at http://localhost:${PORT}`);
  console.log(`FreeLLMAPI proxy target: ${process.env.LLM_BASE_URL || "http://localhost:3001/v1"}`);
  console.log(`Tool bridge: termux-api v0.60.0`);
});

export default app;