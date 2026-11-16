// D1 setup and schema bootstrap (Phase 1)
// Schema mirrors the plan: users, sessions, projects, chat_history, settings, deployment_history.

export async function getDb(db: D1Database): Promise<D1Database> {
  const stmts = [
    `CREATE TABLE IF NOT EXISTS users (
      id TEXT PRIMARY KEY,
      name TEXT NOT NULL,
      github TEXT NOT NULL,
      avatar TEXT
    );`,
    `CREATE TABLE IF NOT EXISTS sessions (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL,
      github_token TEXT NOT NULL,
      session_token TEXT NOT NULL UNIQUE,
      expires_at INTEGER NOT NULL,
      created_at INTEGER NOT NULL
    );`,
    `CREATE TABLE IF NOT EXISTS projects (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL,
      owner TEXT NOT NULL,
      name TEXT NOT NULL,
      repo_url TEXT NOT NULL,
      created_at INTEGER NOT NULL
    );`,
    `CREATE TABLE IF NOT EXISTS chat_history (
      id TEXT PRIMARY KEY,
      session_id TEXT NOT NULL,
      user_id TEXT NOT NULL,
      role TEXT NOT NULL,
      content TEXT NOT NULL,
      timestamp INTEGER NOT NULL,
      model TEXT
    );`,
    `CREATE TABLE IF NOT EXISTS settings (
      user_id TEXT PRIMARY KEY,
      ai_base_url TEXT,
      ai_key TEXT,
      ai_model TEXT,
      theme TEXT DEFAULT 'dark',
      created_at INTEGER NOT NULL,
      updated_at INTEGER NOT NULL
    );`,
    `CREATE TABLE IF NOT EXISTS deployment_history (
      id TEXT PRIMARY KEY,
      user_id TEXT NOT NULL,
      repo_url TEXT NOT NULL,
      commit_sha TEXT NOT NULL,
      triggered_at INTEGER NOT NULL,
      status TEXT DEFAULT 'triggered'
    );`,
  ];
  await db.batch(stmts.map((s) => db.prepare(s)));
  return db;
}