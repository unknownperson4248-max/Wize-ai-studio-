// Web app serving (Phase 1 — inline HTML until Vite build is wired)
// Served on Termux-compatible deploy-only workflow (no local wrangler dev).

import type { Env } from "./index";

export async function serveWeb(request: Request, env: Env) {
  const url = new URL(request.url);

  // Simple single-page app served inline by the Worker
  const html = `<!DOCTYPE html>
<html lang="en">
<head>
  <meta charset="UTF-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0"/>
  <title>WIZE AI STUDIO</title>
  <style>
    * { box-sizing: border-box; margin: 0; padding: 0; }
    body { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif; background: #0f172a; color: #f8fafc; min-height: 100vh; }
    .container { max-width: 800px; margin: 0 auto; padding: 2rem; }
    .center { display: flex; flex-direction: column; align-items: center; justify-content: center; height: 100vh; gap: 1rem; }
    h1 { font-size: 1.5rem; margin-bottom: 0.5rem; color: #38bdf8; }
    p { color: #94a3b8; }
    .btn { display: inline-block; padding: 0.75rem 1.5rem; background: #38bdf8; color: #0f172a; border-radius: 6px; text-decoration: none; font-weight: 600; margin-top: 1rem; }
    .btn:hover { background: #7dd3fc; }
    .repo-list { list-style: none; margin-top: 1rem; }
    .repo-list li { padding: 0.75rem 1rem; border: 1px solid #334155; border-radius: 6px; margin-bottom: 0.5rem; }
    .repo-list a { color: #38bdf8; text-decoration: none; }
    .user-info { margin-top: 1rem; color: #94a3b8; }
    .error { color: #f87171; margin-top: 1rem; }
  </style>
</head>
<body>
  <div id="root"></div>
  <script type="module">
    const root = document.getElementById('root');

    async function load() {
      root.innerHTML = '<div style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:100vh;gap:1rem"><div style="font-size:1.5rem;color:#38bdf8">WIZE AI STUDIO</div><p style="color:#94a3b8">Loading...</p></div>';
      const meResp = await fetch('/api/me');
      let user = null;
      if (meResp.ok) { const data = await meResp.json(); user = data.user; }

      if (!user) {
        root.innerHTML = '<div style="max-width:800px;margin:0 auto;padding:2rem"><div style="font-size:1.5rem;color:#38bdf8">WIZE AI STUDIO</div><p style="color:#94a3b8">Personal AI coding assistant</p><a href="/api/auth/github" style="display:inline-block;padding:0.75rem 1.5rem;background:#38bdf8;color:#0f172a;border-radius:6px;text-decoration:none;font-weight:600;margin-top:1rem">Login with GitHub</a></div>';
        return;
      }

      const reposResp = await fetch('/api/github/repos');
      const repos = reposResp.ok ? (await reposResp.json()).repos : [];

      root.innerHTML = '<div style="max-width:800px;margin:0 auto;padding:2rem">' +
        '<div style="display:flex;justify-content:space-between;align-items:center;gap:1rem;margin-bottom:1rem"><div style="font-size:1.5rem;color:#38bdf8">Projects</div><div style="font-size:0.9rem;color:#94a3b8">' + user.name + ' (@' + user.github + ')</div></div>' +
        '<ul style="list-style:none">' + repos.map((r) => '<li style="padding:0.75rem 1rem;border:1px solid #334155;border-radius:6px;margin-bottom:0.5rem"><a href="' + r.html_url + '" target="_blank" rel="noopener noreferrer" style="color:#38bdf8;text-decoration:none"><strong>' + r.full_name + '</strong></a></li>').join('') + '</ul>' +
        '</div>';
    }

    load();
  </script>
</body>
</html>`;

  return new Response(html, {
    headers: { "content-type": "text/html; charset=utf-8" },
  });
}