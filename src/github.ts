// GitHub REST API client (Phase 1)

export interface GitHubRepo {
  id: number;
  name: string;
  full_name: string;
  private: boolean;
  html_url: string;
  default_branch: string;
  permissions?: {
    admin: boolean;
    push: boolean;
    pull: boolean;
  };
}

export interface GitHubTreeEntry {
  path: string;
  mode: string;
  type: "blob" | "tree";
  size?: number;
  sha: string;
  url: string;
}

export interface GitHubFile {
  path: string;
  sha: string;
  size: number;
  content: string; // base64 decoded text for now
  encoding: string;
}

const GITHUB_API = "https://api.github.com";
const HEADERS = {
  "Accept": "application/vnd.github+json",
  "User-Agent": "wize-ai-studio",
};

async function githubFetch(session: { user: { github: string } } & Record<string, unknown>, path: string, options: Record<string, any> = {}): Promise<any> {
  const token = session.user?.github as string; // not used directly — GitHub token stored in D1
  // For Phase 1, token is retrieved inside the caller via sessions table.
  // Here we accept token passed via session.token if present.
  const headers = { ...HEADERS };
  const tokenValue = (session as any).token || (session as any).access_token;
  if (tokenValue) headers["Authorization"] = `Bearer ${tokenValue}`;
  const url = `${GITHUB_API}${path}`;
  const resp = await fetch(url, { ...options, headers });
  if (!resp.ok) {
    const err = await resp.text().catch(() => "");
    throw new Error(`GitHub API error ${resp.status}: ${err}`);
  }
  return resp.json();
}

// GET /api/github/repos — list user repos
export async function listRepos(session: any): Promise<GitHubRepo[]> {
  const data: GitHubRepo[] = await githubFetch(session, "/user/repos?per_page=100&sort=updated");
  return data;
}

// GET /api/github/repo/:owner/:repo/files — tree
export async function getRepoTree(session: any, owner: string, repo: string, ref?: string): Promise<GitHubTreeEntry[]> {
  const branch = ref || await defaultBranch(session, owner, repo);
  const data: any = await githubFetch(session, `/repos/${owner}/${repo}/git/trees/${branch}?recursive=1`);
  return (data.tree || []).map((t: any) => ({
    path: t.path,
    mode: t.mode,
    type: t.type,
    size: t.size,
    sha: t.sha,
    url: t.url,
  }));
}

async function defaultBranch(session: any, owner: string, repo: string): Promise<string> {
  const data: any = await githubFetch(session, `/repos/${owner}/${repo}`);
  return data.default_branch || "main";
}

// GET /api/github/repo/:owner/:repo/content/:path
export async function getRepoFile(session: any, owner: string, repo: string, filePath: string, ref?: string): Promise<GitHubFile> {
  const branch = ref || await defaultBranch(session, owner, repo);
  const data: any = await githubFetch(session, `/repos/${owner}/${repo}/contents/${encodeURIComponent(filePath)}?ref=${branch}`);
  if (Array.isArray(data)) throw new Error("Path is a directory, not a file");
  const content = data.encoding === "base64"
    ? decodeBase64(data.content)
    : data.content;
  return {
    path: data.path,
    sha: data.sha,
    size: data.size,
    content,
    encoding: data.encoding,
  };
}

function decodeBase64(b64: string): string {
  const bin = atob(b64);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);
  return new TextDecoder().decode(bytes);
}