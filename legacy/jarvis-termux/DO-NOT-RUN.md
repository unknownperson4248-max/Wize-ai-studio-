# Do not run this old server

This is the earlier "JARVIS" Termux experiment, kept only for reference.

`server.js` has a critical security problem: it accepts requests from any website (CORS `*`), has no
password, and passes user text straight into shell commands (`execSync`). While it runs on a phone, any web
page opened in that phone's browser could run commands on the phone.

The new assistant (NOVA) replaces it: the Android app in `android/` and the backend in `worker/`.
