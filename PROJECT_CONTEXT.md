# PROJECT_CONTEXT: JARVIS personal & business assistant

Was called NOVA during phases 0-1 of the first plan; renamed JARVIS when the full spec arrived (2026-11-17).
Android applicationId stays `app.nova.assistant` so new builds install over the old app; the worker is still named `nova-assistant`.
Owner works from an Android phone only: everything is built and hosted online (GitHub Actions + Cloudflare).

## Build environment
- This cloud workspace has Java 21 and Gradle, but no Android SDK platform (installing it needs accepting the SDK license, which was not done here).
- APKs are built by `.github/workflows/android.yml`: unit tests (`gradle testReleaseUnitTest`), then `assembleRelease`, published as release `nova-latest` (asset `nova.apk`).

## Spec phases (owner's JARVIS master prompt)
1. App + dashboard: DONE (this commit). Compose UI (Home, JARVIS core, Tasks, Control, Settings, Security), Room DB (tasks, task_steps, action_log, products), task recording for every request, action/audit log, emergency stop, pause automation, voice speed/volume, permission dashboard, unit tests in CI.
2. Voice + AI brain: partly done earlier (STT with Google service + dialog fallback, TTS, tool-calling loop on Workers AI). TODO: provider layer in the app (Claude / OpenAI / compatible) with keys encrypted by Android Keystore, usage tracking, wake phrase "Hey Jarvis".
3. Accessibility phone control: done earlier (read_screen, tap, type_text, scroll, press, risky-tap approval, bubble). TODO: horizontal swipe tool, screenshots, foreground-app tool.
4. Notification intelligence + WhatsApp workflows: TODO (NotificationListenerService, consent switch, summaries, reply drafts).
5. Inventory: TODO screens/tools (products table already exists; dashboard reads low stock).
6. Customers & orders: TODO.
7. Autonomous task engine: task records exist; TODO multi-step plans with approval summary.
8. Foreground service, Keystore secrets, release hardening: TODO.

## Architecture
- `android/`: Kotlin app, no external libraries (platform APIs only). Voice in: Android SpeechRecognizer (bn-BD / en-US).
  Voice out: Android TextToSpeech. Runs phone actions with intents and system services. Built by
  `.github/workflows/android.yml`, published as release `nova-latest` (asset `nova.apk`), signed with
  `android/keystore/nova.jks` so updates install over older versions.
- `worker/`: Cloudflare Worker `nova-assistant` (https://nova-assistant.rahatwize.workers.dev).
  D1 `nova-db` (devices, messages, memories, pair_attempts). Workers AI: `@cf/meta/llama-3.3-70b-instruct-fp8-fast`,
  fallback `@cf/meta/llama-4-scout-17b-16e-instruct`. Tool definitions in `worker/src/tools.js`.
- Phone actions are returned by the worker; the app runs them and posts the result back. Server-side tools
  (remember/forget) run in the worker.

## Security
- Pairing code (`PAIR_CODE` secret), 5 attempts per hour per IP. Device tokens are random 256-bit; only SHA-256 hashes are stored.
- Every API except /api/health and /api/pair needs a device token.
- No API keys in the app or the repository. Workers AI needs no key.
- Calls and SMS: Yes/No confirmation in the app, then the dialer / SMS app opens filled in; the user presses call/send.
  The app does not hold CALL_PHONE or SEND_SMS permissions.
- Signing key in the repo is for this personal sideloaded app only. Move to a GitHub secret before any public release.

## Status
Phase 1 (done, 2026-11-17)
- [x] Worker: pairing, device auth, chat with tool calling, conversation history, long-term memory, model fallback
- [x] Worker deployed, D1 created and migrated, PAIR_CODE set
- [x] Worker tested locally (19 checks) and on the live URL (pair, open_app, unpair)
- [x] Android app: dark animated orb UI, chat, mic (Bangla/English), spoken replies, settings, pairing, memory list, clear chat
- [x] Phone actions: open app, open URL, web search, call/SMS (confirmed, prepared), alarm, timer, torch, volume, battery, settings pages, maps directions
- [x] GitHub Actions APK build + release
- [x] First APK build verified on GitHub Actions
- [x] Installed and paired on the owner's phone (Honor); chat works

Phase 2 (in progress, 2026-11-17)
- [x] Mic fix: use Google's speech service directly (Honor default service gave error 5), fall back to the system voice dialog
- [x] Animated intro screen (hologram face build-up, star field, neon grid)
- [x] Phone control (Accessibility service): read_screen, tap, type_text, scroll, press; risky taps (send/pay/delete/...) need Yes; no typing into password fields
- [x] whatsapp_message tool (confirm first, then the app presses send)
- [x] Floating bubble: animated cyber-girl face (AvatarView), tap to talk (ListenActivity), hold to open, drag to move
- [ ] Tested on the owner's phone

Next
- [ ] Notification listener: read and summarise notifications
- [ ] Wake word
- [ ] Worker deploy from GitHub Actions (needs a Cloudflare API token as a GitHub secret)
- [ ] Optional extra AI providers (Gemini/OpenRouter key in the worker as a secret) for better Bangla

## Costs
Cloudflare Workers + D1 + Workers AI free plan: $0. Workers AI free allowance is about 10,000 neurons/day,
roughly 150-250 short requests per day with Llama 3.3 70B. GitHub Actions: free for public repos.
