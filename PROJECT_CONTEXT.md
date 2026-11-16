# PROJECT_CONTEXT.md — WIZE AI STUDIO (Personal AI Coding Website)

## 1. Project Overview
WIZE AI STUDIO holo personal AI coding website, Android Chrome theke access kora jabe, PWA install kora jay. User repo select korbo, chat diye instruction dibe, AI file change korbe, user diff approve korbe, tarpor GitHub commit ar Cloudflare deploy hobe. Chat history permanent thakbe (model change pore jabe na).

## 2. Goals (Core)
- GitHub-integrated AI coding assistant
- Chat-based repo modification workflow
- Diff review + approve before commit
- Permanent chat/memory (model change er pore data lose na)
- Zero-cost hosting (Cloudflare free tier)
- OpenAI-compatible AI endpoint (FreeLLMAPI/OpenRouter)

## 3. Constraints (EXPLICITLY NO)
- ❌ Image/audio/video generation
- ❌ Stripe billing / payments
- ❌ Mobile app (beyond PWA)
- ❌ Kubernetes / complex infra
- ❌ Server-side git operations (GitHub API only)

## 4. High-Level Architecture

### Client (React + Vite PWA)
- Dark + blue-silver theme
- Bottom navigation: Chat | Projects | Preview | Memory | Settings
- PWA install on Android Chrome
- GitHub OAuth login

### Backend (Cloudflare Workers)
- GitHub OAuth flow handler
- REST API for repo/file operations
- Webhook receiver for GitHub events
- AI proxy: transforms chat messages → OpenAI-compatible calls
- Diff generation + apply logic
- Cloudflare Pages deployment trigger

### Database (Cloudflare D1)
- Chat sessions + history (permanent storage)
- User preferences (theme, API key, base URL)
- Project/repo metadata (last selected repo, permissions)
- Deployment history

### AI Layer (OpenAI-compatible)
- Base URL configurable (FreeLLMAPI/OpenRouter)
- API key stored securely in D1/Worker env
- Model selection in UI
- Streaming chat responses
- Function calling for file operations (optional)

### GitHub Integration (REST API only)
- OAuth authentication
- Repo listing + selection
- File read (contents API)
- File write (contents API) — create/edit/delete
- PR/Commit creation via REST
- Diff generation comparison

## 5. Folder Structure (Monorepo-ish, simple)

```
wize-ai-studio/
├── packages/
│   ├── ui/              # React components (shadcn/vue-inspired)
│   ├── api/             # Cloudflare Workers handlers
│   ├── db/              # D1 schema + queries
│   ├── ai/              # AI prompt/config helpers
│   └── auth/            # GitHub OAuth helpers
├── apps/
│   ├── web/             # Vite + React PWA (entry point)
│   └── functions/       # Any edge functions (if needed)
├── scripts/
│   └── migrate/         # DB schema migrations
├── wrangler.toml        # Cloudflare config (multiple environments)
├── package.json
├── tsconfig.json
└── README.md
```

## 6. Services & Cost (All Free Tier)

| Service | Plan/Detail | Est. Monthly Cost |
|---------|-------------|-------------------|
| Cloudflare Pages | Free tier, static + SSR | $0 |
| Cloudflare Workers | Free tier (100K requests/day) | $0 |
| Cloudflare D1 | Free tier (up to 1GB storage, 1M queries/mo) | $0 |
| GitHub OAuth | Free | $0 |
| OpenAI-compatible (FreeLLMAPI/OpenRouter) | Pay-per-use, can start $0 with free credits | $0–$20 (optional) |
| Domain (custom) | Free Cloudflare subdomain or cheap domain | $0–$12 |
| **Total** | | **$0–$12** |

### Free Tier Limits (Sufficient for personal use)
- Workers: 100K requests/day → ~3M/month
- Pages: Unlimited bandwidth, 1GB functions assets
- D1: 1GB storage, 1M queries/month → generous for chat history
- GitHub API: Standard rate limits (5000/hr for OAuth)

## 7. Security Considerations

### Auth
- GitHub OAuth only (no password auth)
- Tokens stored in D1 (encrypted at rest)
- Short-lived session cookies

### Data Privacy
- Chat history in D1 — user owns the data
- API keys never sent to client — proxied through Worker
- No third-party storage (S3/R2 eliminated from scope)

### Rate Limiting
- Workers built-in rate limiting
- GitHub API rate limits respected
- Per-user request quotas in DB

### Supply Chain
- Dependencies minimal (React, Zod, date-fns, etc.)
- `npm audit` run CI
- No native code compilation on client

## 8. Phase Checklist

### Phase 0 — Foundation (1 week)
- [ ] Repo initialize + Turbo/monorepo config
- [ ] Cloudflare account + Pages + Workers + D1 setup
- [ ] Wrangler config (local dev + CI)
- [ ] GitHub OAuth app register (callback URL: `<pages-url>/api/auth/callback`)
- [ ] Basic Vite + React + TS setup
- [ ] Tailwind + dark mode + blue-silver theme config
- [ ] Bottom nav component: Chat | Projects | Preview | Memory | Settings

### Phase 1 — Auth + GitHub (1 week)
- [ ] GitHub OAuth flow (redirect → exchange code → store session)
- [ ] User onboarding: first repo select
- [ ] D1 schema: sessions, projects, chat_history, user_prefs
- [ ] API: `/api/github/repos` (list user repos)
- [ ] API: `/api/github/repo/:owner/:repo/files` (tree + content)

### Phase 2 — Chat + AI (2 weeks)
- [ ] Chat UI (message bubbles, input, send)
- [ ] AI proxy: forward chat → OpenAI-compatible endpoint
- [ ] Streaming response in UI
- [ ] System prompt: "You are a coding assistant. Modify repo files based on user instructions. Output diff only."
- [ ] Diff generation (unified diff format)
- [ ] User approval UI (side-by-side diff, accept/reject)

### Phase 3 — File Operations (2 weeks)
- [ ] API: apply diff → GitHub Contents API (create/edit/delete)
- [ ] Preview mode: render modified files
- [ ] Commit creation via GitHub REST (`/repos/:owner/:repo/git/refs`, `/git/commits`, `/git/trees`)
- [ ] Cloudflare Pages trigger: re-deploy after commit (webhook or manual)

### Phase 4 — Memory + Settings (1 week)
- [ ] Permanent chat history in D1 (persist across model changes)
- [ ] User settings: API key, base URL, model selector, theme toggle
- [ ] Memory cleanup: TTL or manual purge
- [ ] PWA manifest + service worker (offline cache, install prompt)

### Phase 5 — Polish + Launch (1 week)
- [ ] PWA install prompt on Android Chrome
- [ ] Mobile-friendly chat UI (vertical bottom nav)
- [ ] Error handling + edge cases
- [ ] Documentation (README + inline comments)
- [ ] Initial public release (free tier only)

## 9. Key Flows

### Flow 1: GitHub Login + Repo Select
1. User visits PWA → "Login with GitHub" CTA
2. OAuth redirect → GitHub → callback with code
3. Exchange code → store GitHub token in D1 (encrypted)
4. Fetch user repos → show in Projects nav
5. User selects repo → chat UI loads with repo context

### Flow 2: Chat → Diff → Commit
1. User types instruction in chat: "Add binary search function to src/utils.py"
2. AI proxy sends to OpenAI-compatible endpoint with system prompt
3. AI returns unified diff of proposed changes
4. UI shows diff in review panel
5. User clicks "Accept & Commit"
6. Worker applies diff via GitHub Contents API
7. Worker creates commit SHA → trigger Pages redeploy (via webhook or re-deploy API)
8. UI shows: "Committed & deployed ✓"

### Flow 3: Memory Persistence
1. Every chat message saved to D1 `chat_history` table
2. Per-session ID (from Worker env or OAuth user_id)
3. On model change: history persists, only new queries use new model
4. User can purge memory from Settings → "Clear chat history"

## 10. OpenAI-Compatible Config

### Settings UI (bottom nav → Settings)
- **Base URL**: configurable in Settings (stored encrypted in D1 via Worker secret)
- **API Key**: stored in Worker env (not exposed to client)
- **Default Model**: e.g., `anthropic/claude-3-haiku`, `openai/gpt-4o-mini`
- **Temperature**: 0.2 (low for coding consistency)
- **Max Tokens**: 4096 (sufficient for diff generation)

### Key Rotation
- User can change key anytime in Settings
- New key stored in D1, Worker env updated on next deploy
- Old key immediately invalidated

---

### Workflow / Deployment (Termux — no local dev server)
- Local `wrangler dev` will not run (no Termux dev server support).
- Build entirely via Cloudflare Pages + Workers + D1 (deploy-only model).
- Each phase ends: commit → push to GitHub `main` → Cloudflare Pages auto-deploys.
- AI base URL + API key: set in Settings, encrypted at rest in D1 (encryption key held as a Worker secret), never exposed to client.

*Eti pure planning document. Code ekhono na likhe gelam. MVP target: $0 mahine cost, Android Chrome + PWA use, GitHub + AI coding workflow.*