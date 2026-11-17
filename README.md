# NOVA (working name)

A personal voice assistant that controls an Android phone. Talk or type ("torch jalao", "open YouTube",
"set an alarm for 6:30", "call Rahim"), and it does it. It remembers what you ask it to remember.

Everything is online: the app is built by GitHub Actions and the backend runs on Cloudflare. No computer,
Termux, Node.js or Android Studio is needed on the phone.

## How it works

```
Android app (android/)  ──HTTPS──>  Cloudflare Worker (worker/)  ──>  Workers AI (Llama 3.3 70B)
  voice in / voice out                pairing, conversation, memory        decides what to do
  runs phone actions  <── actions ──  D1 database
```

1. You speak; Android's speech recognition turns it into text (Bangla or English).
2. The app sends the text to the backend, which adds recent conversation and saved memories, and asks the AI.
3. The AI answers, or asks for a phone action (open app, alarm, torch...). The app does it and sends the result back.
4. Calls and SMS are only prepared: the app asks "Yes/No" first, then opens the dialer or SMS app filled in, and you press call/send.

## Install on the phone

1. Open the **Releases** page of this repository, release **"NOVA (latest build)"**, and download **nova.apk**.
2. Open the file. Android asks to allow installing from your browser once: allow it, then install.
3. Open NOVA, enter the **pairing code** (you got it in the setup message), press **Pair**.
4. Tap the mic and talk.

To make the long-press of the home button open NOVA: Android Settings → Apps → Default apps → Digital assistant app → NOVA.

## What it can do now (phase 1)

| Say | Does |
|---|---|
| "torch jalao" / "turn off the flashlight" | flashlight on/off |
| "open WhatsApp" | opens any installed app by name |
| "set an alarm for 6:30" / "5 minute timer" | alarm / timer |
| "volume 50%" | media volume |
| "battery koto?" | battery level |
| "call Rahim" / "SMS Mim: I'm coming" | asks to confirm, then opens dialer / SMS app filled in |
| "Dhaka jabo, direction dao" | Google Maps directions |
| "search best phones 2026" / "open alu-mrk-triedge site" | web search / open a site |
| "open wifi settings" | settings pages |
| "remember that my sister is Mim" / "forget Mim" | long-term memory |

## Backend (worker/)

- Cloudflare Worker `nova-assistant`, D1 database `nova-db`, Workers AI. All on Cloudflare's free plan.
- Secret `PAIR_CODE`: needed once per phone to pair. Change it with `npx wrangler secret put PAIR_CODE`.
- API: `POST /api/pair`, `POST /api/chat`, `GET|DELETE /api/history`, `GET /api/memories`, `DELETE /api/memories/:id`, `POST /api/unpair`.
- Database schema: `worker/schema.sql`.

Deploy (from any machine with Node, or a GitHub Action later):
```
cd worker
npx wrangler d1 execute nova-db --remote --file=schema.sql   # first time only
npx wrangler deploy
```

## Renaming

The name appears in two places: `android/app/src/main/res/values/strings.xml` (`app_name`) and
`worker/src/index.js` (`ASSISTANT_NAME`).

## Old code

`legacy/jarvis-termux/` is an earlier experiment. **Do not run it**: see `legacy/jarvis-termux/DO-NOT-RUN.md`.
