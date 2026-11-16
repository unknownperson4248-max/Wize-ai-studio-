// Session management (Phase 1 placeholder)
import { getDb } from "../db";

export type Session = {
  user: {
    id: string;
    name: string;
    github: string;
  };
  token: string;
  expires_at: number;
};

export async function requireAuth(request: Request, env: any): Promise<Session | null> {
  const auth_header = request.headers.get("Authorization");
  if (!auth_header || !auth_header.startsWith("Bearer ")) return null;

  const token = auth_header.substring(7);
  const db = await getDb(env.DB);

  const row = await db
    .prepare("SELECT * FROM sessions WHERE session_token = ? AND expires_at > ?")
    .bind(token, Date.now())
    .first();

  if (!row) return null;

  const user = await db
    .prepare("SELECT * FROM users WHERE id = ?")
    .bind(row.user_id)
    .first();

  if (!user) return null;

  return {
    user: { id: user.id, name: user.name, github: user.github },
    token,
    expires_at: row.expires_at,
  };
}