// GitHub OAuth flow (Phase 1 — minimal, to be extended in Phase 2)
import { getDb } from "../db";

const GITHUB_AUTHORIZE = "https://github.com/login/oauth/authorize";
const GITHUB_TOKEN = "https://github.com/login/oauth/access_token";

export interface OAuthEnv {
  GITHUB_CLIENT_ID: string;
  GITHUB_CLIENT_SECRET: string;
  GITHUB_REDIRECT_URI: string;
  DB: D1Database;
}

// GET /api/auth/github — redirect user to GitHub
export function handleOAuthStart(request: Request, env: OAuthEnv) {
  const state = crypto.randomUUID();
  const redirectUri = env.GITHUB_REDIRECT_URI;
  const url = `${GITHUB_AUTHORIZE}?client_id=${env.GITHUB_CLIENT_ID}&redirect_uri=${encodeURIComponent(redirectUri)}&response_type=code&state=${state}&scope=repo`;
  return Response.redirect(url, 302);
}

// GET /api/auth/callback — exchange code for token, store session in D1
export async function handleOAuthCallback(request: Request, env: OAuthEnv) {
  const url = new URL(request.url);
  const code = url.searchParams.get("code");
  if (!code) return new Response("Missing code", { status: 400 });

  const tokenResp = await fetch(GITHUB_TOKEN, {
    method: "POST",
    headers: { "content-type": "application/json", "accept": "application/json" },
    body: JSON.stringify({
      client_id: env.GITHUB_CLIENT_ID,
      client_secret: env.GITHUB_CLIENT_SECRET,
      code,
      redirect_uri: env.GITHUB_REDIRECT_URI,
    }),
  });
  const tokenData: any = await tokenResp.json();
  const accessToken = tokenData.access_token;
  if (!accessToken) {
    return new Response("GitHub token exchange failed", { status: 500 });
  }

  // Fetch user profile
  const userResp = await fetch("https://api.github.com/user", {
    headers: {
      "authorization": `Bearer ${accessToken}`,
      "accept": "application/vnd.github+json",
      "user-agent": "wize-ai-studio",
    },
  });
  const user: any = await userResp.json();
  const userId = String(user.id);

  // Create session in D1
  const db = await getDb(env.DB);
  const sessionToken = crypto.randomUUID();
  const expiresAt = Date.now() + 7 * 24 * 60 * 60 * 1000; // 7 days
  await db
    .prepare(
      `INSERT OR IGNORE INTO users (id, name, github, avatar) VALUES (?, ?, ?, ?)`
    )
    .bind(userId, user.login, user.login, user.avatar_url || null)
    .run();
  await db
    .prepare(
      `INSERT INTO sessions (user_id, github_token, session_token, expires_at, created_at)
       VALUES (?, ?, ?, ?, ?)
       ON CONFLICT(session_token) DO UPDATE SET github_token=excluded.github_token,
       expires_at=excluded.expires_at`
    )
    .bind(userId, accessToken, sessionToken, expiresAt, Date.now())
    .run();

  // Redirect to web app with session token in cookie
  return new Response(null, {
    status: 302,
    headers: {
      "location": "/",
      "set-cookie": `session=${sessionToken}; HttpOnly; Secure; SameSite=Lax; Max-Age=${7 * 24 * 60 * 60}; Path=/`,
    },
  });
}