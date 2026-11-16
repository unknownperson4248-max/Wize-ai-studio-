// WIZE AI STUDIO — Cloudflare Worker (Phase 1: Auth + GitHub)
// GitHub OAuth flow, repo/file APIs, D1-backed sessions + settings.
// AI base URL / API key are stored encrypted in D1 (key held in Worker secret).

import { handleOAuthStart, handleOAuthCallback } from "./auth/oauth";
import { requireAuth, type Session } from "./auth/session";
import { listRepos, getRepoTree, getRepoFile } from "./github";
import { getSettings, putSettings } from "./settings";
import { getDb } from "./db";
import { serveWeb } from "./web";

export interface Env {
  DB: D1Database;
  GITHUB_CLIENT_ID: string;
  GITHUB_CLIENT_SECRET: string;
  ENCRYPTION_KEY: string;
  WEB_BASE_URL: string;
  SESSION_SECRET: string;
  ENVIRONMENT: string;
}

export default {
  async fetch(request: Request, env: Env, ctx: EventContext) {
    const url = new URL(request.url);
    const path = url.pathname;

    // Static web app (Vite build) served at root
    if (path === "/" || path.startsWith("/assets/") || path.startsWith("/_") || path === "/manifest.json" || path === "/sw.js") {
      return serveWeb(request, env);
    }

    // Auth
    if (path === "/api/auth/github" && request.method === "GET") return handleOAuthStart(request, env);
    if (path === "/api/auth/callback" && request.method === "GET") return handleOAuthCallback(request, env);

    // Session / me
    if (path === "/api/me" && request.method === "GET") {
      const session = await requireAuth(request, env);
      if (!session) return json({ error: "unauthorized" }, 401);
      return json({ user: session.user });
    }

    // GitHub repos
    if (path === "/api/github/repos" && request.method === "GET") {
      const session = await requireAuth(request, env);
      if (!session) return json({ error: "unauthorized" }, 401);
      try {
        const repos = await listRepos(session);
        return json({ repos });
      } catch (e: any) {
        return json({ error: e.message }, 500);
      }
    }

    // Repo tree + single file
    const treeMatch = path.match(/^\/api\/github\/repo\/([^/]+)\/([^/]+)\/files$/);
    if (treeMatch && request.method === "GET") {
      const session = await requireAuth(request, env);
      if (!session) return json({ error: "unauthorized" }, 401);
      const [, owner, repo] = treeMatch;
      const ref = url.searchParams.get("ref");
      try {
        const tree = await getRepoTree(session, owner, repo, ref);
        return json({ tree });
      } catch (e: any) {
        return json({ error: e.message }, 500);
      }
    }
    const fileMatch = path.match(/^\/api\/github\/repo\/([^/]+)\/([^/]+)\/content\/(.+)$/);
    if (fileMatch && request.method === "GET") {
      const session = await requireAuth(request, env);
      if (!session) return json({ error: "unauthorized" }, 401);
      const [, owner, repo, filePath] = fileMatch;
      const ref = url.searchParams.get("ref");
      try {
        const file = await getRepoFile(session, owner, repo, decodeURIComponent(filePath), ref);
        return json({ file });
      } catch (e: any) {
        return json({ error: e.message }, 500);
      }
    }

    // Settings (AI base URL / key)
    if (path === "/api/settings" && request.method === "GET") {
      const session = await requireAuth(request, env);
      if (!session) return json({ error: "unauthorized" }, 401);
      const settings = await getSettings(env.DB, session.user.id);
      return json({ settings: settings ?? { ai_base_url: null, ai_model: null, theme: "dark" } });
    }
    if (path === "/api/settings" && request.method === "POST") {
      const session = await requireAuth(request, env);
      if (!session) return json({ error: "unauthorized" }, 401);
      const body = await request.json().catch(() => ({}));
      await putSettings(env.DB, env, session.user.id, body);
      return json({ ok: true });
    }

    return json({ error: "not_found" }, 404);
  },
} satisfies ExportedHandler<Env>;

function json(data: any, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { "content-type": "application/json" },
  });
}