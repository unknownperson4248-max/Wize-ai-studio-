// WIZE AI STUDIO — Minimal Phase 1 Web App
// Displays login button or repo list after auth

import { useEffect, useState } from "react";

type User = { id: string; name: string; github: string } | null;
type Repo = { id: number; name: string; full_name: string; private: boolean; html_url: string };

function App() {
  const [user, setUser] = useState<User>(null);
  const [repos, setRepos] = useState<Repo[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  async function fetchMe() {
    try {
      const resp = await fetch("/api/me");
      if (!resp.ok) return setUser(null);
      const data = await resp.json();
      setUser(data.user);
    } catch {
      setUser(null);
    }
  }

  async function fetchRepos() {
    try {
      const resp = await fetch("/api/github/repos");
      if (!resp.ok) throw new Error("Failed to fetch repos");
      const data = await resp.json();
      setRepos(data.repos);
    } catch (e: any) {
      setError(e.message);
    }
  }

  useEffect(() => {
    fetchMe().then(() => {
      setLoading(false);
    });
  }, []);

  useEffect(() => {
    if (user) fetchRepos();
  }, [user]);

  if (loading) return <div className="center">Loading...</div>;

  if (!user) {
    return (
      <main className="container">
        <h1>WIZE AI STUDIO</h1>
        <p>Personal AI coding assistant</p>
        <a href="/api/auth/github" className="btn">Login with GitHub</a>
      </main>
    );
  }

  return (
    <main className="container">
      <header>
        <h1>Projects</h1>
        <div className="user-info">
          <span>{user.name} (@{user.github})</span>
          <a href="/api/auth/github?logout=1" className="btn-small">Logout</a>
        </div>
      </header>
      {error && <div className="error">{error}</div>}
      <ul className="repo-list">
        {repos.map((repo) => (
          <li key={repo.id}>
            <a href={repo.html_url} target="_blank" rel="noopener noreferrer">
              <strong>{repo.full_name}</strong>
              <span className={repo.private ? "private" : "public"}>
                {repo.private ? "Private" : "Public"}
              </span>
            </a>
          </li>
        ))}
      </ul>
    </main>
  );
}

export default App;