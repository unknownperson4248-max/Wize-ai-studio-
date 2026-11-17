# PROJECT_CONTEXT.md — JARVIS Personal AI Android Assistant (Termux / PWA Edition)

## 1. Project Overview
JARVIS holo ekti intelligent voice-controlled personal AI assistant, ja Android Chrome e access kora jabe ebong PWA hishebe install kora jay. Eti Voice API, Web Speech API, termux-api, FreeLLMAPI (localhost:3001) o tool calling diye Android phone control ebong automation korte parbe. Native APK build hobe na phone e; GitHub Actions diye native APK build hobe pore.

## 2. Goals (Core)
- Voice-controlled Android assistant (STT + TTS)
- Natural Bangla/English multilingual commands
- Android phone control via Accessibility API, NotificationListener, Intents, termux-api
- Task execution with confirmation before sensitive actions (SMS, call)
- Conversation memory (long-term, searchable, deletable)
- Device info tools: battery, torch, volume, notification
- Zero local Android SDK/Gradle install (built via GitHub Actions later)

## 3. Constraints (EXPLICITLY NO)
- ❌ Native Android SDK / Gradle installation on Termux phone
- ❌ Local dev server on phone (no wrangler dev)
- ❌ Stripe billing / payments
- ❌ Image/audio/video generation
- ❌ Kubernetes / complex infra
- ❌ Server-side git operations on phone (GitHub API only)
- ❌ Hardcoded API keys
- ❌ Bypassing authentication, screen locks, or permission dialogs
- ❌ Inventory management module (removed per latest requirement)

## 4. High-Level Architecture

### Client (React/Vite PWA — Zero Bundler)
- Dark black background + blue-silver accents
- Central JARVIS visualizer with animated voice waveform
- Live listening indicator
- Mic button (always visible) — Web Speech API STT
- AI conversation panel
- Device automation dashboard
- Settings & permissions screen
- Bottom navigation: Chat | Automation | Memory | Settings
- PWA install on Android Chrome

### Backend (Termux Node.js Server — localhost:3200)
- Express server on port 3200
- FreeLLMAPI proxy endpoint (/api/chat)
- Tool calling bridge to termux-api commands
- Static serving of PWA client

### AI Layer (FreeLLMAPI / OpenAI-compatible)
- Configurable base URL + API key (stored in Settings, encrypted)
- Tool calling for device actions
- Conversation context management
- Model selection in UI
- Streaming chat responses

### Device Control (termux-api + Android Intents)
- `termux-battery-status` — battery info (present: true, percentage: 53, temperature: 33°C)
- `termux-torch on/off` — flashlight
- `termux-volume` — volume control (music stream)
- `termux-notification` — show notifications
- `termux-open-url` — open website
- `termux-clipboard-set/get` — clipboard read/write
- Intent-based: SMS send (with screen confirmation), phone call (with screen confirmation), open app
- User confirmation required before SMS/call

## 5. Folder Structure (Final)

```
wize-ai-studio/
├── package.json            # Node.js deps: express, cors, dotenv
├── server.js               # Termux Node.js server (port 3200) — FreeLLMAPI proxy + termux-api
├── .env.example            # PORT=3200, LLM_BASE_URL, LLM_MODEL
├── .gitignore              # Secrets, node_modules, dist
├── client/
│   ├── index.html          # Single HTML, vanilla ES modules — works on Android Chrome
│   ├── manifest.json       # PWA manifest
│   ├── sw.js               # Service worker (offline cache)
│   └── src/
│       ├── services/
│       │   ├── api.js      # API client (health, chat, tool)
│       │   ├── stt.js      # Web Speech API STT
│       │   └── tts.js      # Web Speech API TTS
│       └── (optional: components inline in index.html events)
README.md
```

## 6. Services & Cost (All Free Tier)

| Service | Plan/Detail | Est. Monthly Cost |
|---------|-------------|-------------------|
| Termux Node.js server | Localhost on phone (port 3200) | $0 |
| Web Speech API (browser) | Native STT/TTS | $0 |
| FreeLLMAPI / OpenRouter | Pay-per-use, free tier available | $0–$20 (optional) |
| termux-api | Local Android APIs (battery, torch, etc.) | $0 |
| GitHub Actions (future APK build) | Free minutes | $0 |
| Domain | None needed (localhost/PWA) | $0 |
| **Total** | | **$0–$20** |

## 7. Security Considerations

### Auth & Secrets
- No password auth — PWA runs locally on phone
- API keys stored in Settings → localStorage; never hardcoded
- FreeLLMAPI key entered by user in Settings UI
- No native Android SDK installed → no native APK built on phone

### Permissions
- Android permissions requested via browser/termux-api with explanation
- SMS and call require **screen confirmation dialog** before execution
- No bypass of authentication, screen locks, or permission dialogs
- Minimum required permissions only

### Data Privacy
- Chat history stored locally (localStorage)
- API keys stored locally, never sent to remote without user action
- User-controlled memory deletion

## 8. Phase Checklist

### Phase 0 — Foundation (skipped: no Android SDK/Gradle on phone)
- ✅ Node.js installed via `pkg install nodejs-lts`
- ✅ termux-api installed via `pkg install termux-api`
- ✅ JDK available (openjdk-17 in apt); Gradle can run locally if needed

### Phase 1 — Termux Node.js Server + JARVIS PWA (Complete ✅)
- [x] Node.js Express server on localhost:3200
- [x] FreeLLMAPI proxy endpoint (/api/chat) — forwards to localhost:3001/v1
- [x] termux-api bridge: battery_status, torch_on/torch_off, volume_set, clipboard_set/get, notification_show, open_url, toast, vibrate
- [x] React-style PWA client: dark blue-silver UI, waveform visualizer, live listening indicator
- [x] Web Speech API: STT (Bangla/English multilingual) + TTS
- [x] Mic button with continuous listening
- [x] Chat panel with message bubbles + AI responses
- [x] Bottom navigation: Chat | Automation | Memory | Settings
- [x] Settings: API key, model selector, language (saved to localStorage, encrypted via .env template)
- [x] PWA manifest + service worker (install on Android Chrome)
- [x] Confirmation dialog for sensitive actions (SMS/call tool calls)
- [x] Commit → push to GitHub `main` → GitHub Pages auto-deploys

### Phase 2 — Android Device Control (Next)
- [ ] Open app via Intents (termux-am / intent)
- [ ] Open URL / website
- [ ] Clipboard read/write (termux-clipboard-get/set)
- [ ] SMS send (with screen confirmation)
- [ ] Phone call (with screen confirmation)
- [ ] Notification display (termux-notification)

### Phase 3 — AI Brain Enhancement
- [ ] Tool calling for device actions
- [ ] Conversation memory (local IndexedDB)
- [ ] Context management
- [ ] Task planning + execution verification
- [ ] Error recovery

### Phase 4 — Memory + Settings
- [ ] Long-term user preferences
- [ ] Conversation history (searchable)
- [ ] Task history
- [ ] User-controlled memory deletion
- [ ] API key rotation

### Phase 5 — Automation Engine
- [ ] Command → action mapping
- [ ] Structured execution plan
- [ ] Confirmation before sensitive actions
- [ ] Result verification
- [ ] Auditable task execution log

### Phase 6 — Polish + Launch
- [ ] PWA install prompt on Android Chrome
- [ ] Mobile-responsive UI
- [ ] Error handling + edge cases
- [ ] Documentation (README)
- [ ] GitHub Actions workflow for future native APK build (placeholder)

## 9. Key Flows

### Flow 1: Voice Command
1. User taps Mic button or says wake word
2. Web Speech API converts speech → text (Bangla/English)
3. Text sent to localhost:3200 `/api/chat` (FreeLLMAPI proxy)
4. AI determines required tool/action + responds
5. TTS speaks response; waveform animates during listening/speaking

### Flow 2: Device Action (e.g., "Turn on torch")
1. AI identifies tool: `torch on`
2. Server calls `termux-torch on`
3. Result verified; TTS confirms "Torch on"

### Flow 3: Sensitive Action (e.g., "Send SMS to Adam")
1. AI identifies tool: SMS send
2. Server returns pending confirmation (not executed yet)
3. UI shows confirmation dialog: "Send SMS to Adam? [Yes] [No]"
4. On confirm → server sends SMS via Intent; TTS confirms

### Flow 4: Memory Persistence
1. Every message saved locally (localStorage)
2. On model change: history persists
3. Settings → "Clear chat history" deletes memory

## 10. OpenAI-Compatible Config (Settings)

- **Base URL**: configurable in Settings (e.g., `http://localhost:3001/v1`)
- **API Key**: stored encrypted in localStorage (never hardcoded)
- **Default Model**: e.g., `anthropic/claude-3-haiku`
- **Temperature**: 0.2
- **Max Tokens**: 4096

---

### Workflow / Deployment (Termux — no local dev server)
- Node.js server runs locally on phone (localhost:3200)
- Client PWA served from GitHub Pages (auto-deploy on push)
- Each phase ends: commit → push to GitHub `main` → GitHub Pages auto-deploys
- AI base URL + API key: set in Settings, stored encrypted in localStorage, never exposed

*Eti pure planning document. MVP target: $0 mahine cost, Android Chrome + PWA use, voice AI assistant.*