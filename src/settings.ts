// Settings management with encryption for AI API keys
import type { Env } from "./index";

export interface UserSettings {
  ai_base_url?: string;
  ai_key?: string;
  ai_model?: string;
  theme?: "dark" | "light";
}

export async function getSettings(db: D1Database, env: Env, userId: string): Promise<UserSettings> {
  const row = await db
    .prepare("SELECT * FROM settings WHERE user_id = ?")
    .bind(userId)
    .first();

  if (!row) return { theme: "dark" };

  // Decrypt AI key if present
  let decryptedKey: string | undefined;
  if (row.ai_key) {
    decryptedKey = await decryptKey(row.ai_key, env);
  }

  return {
    ai_base_url: row.ai_base_url || undefined,
    ai_key: decryptedKey,
    ai_model: row.ai_model || undefined,
    theme: (row.theme as "dark" | "light") || "dark",
  };
}

export async function putSettings(db: D1Database, env: any, userId: string, body: any) {
  const now = Date.now();
  const current = await getSettings(db, env, userId);

  const ai_base_url = body.ai_base_url ?? current.ai_base_url;
  const ai_model = body.ai_model ?? current.ai_model;
  const theme = body.theme ?? current.theme;

  // Encrypt API key if provided
  let encryptedKey: string | null = null;
  if (body.ai_key) {
    encryptedKey = await encryptKey(body.ai_key, env);
  } else if (current.ai_key) {
    encryptedKey = await encryptKey(current.ai_key, env);
  }

  await db
    .prepare(
      `INSERT INTO settings (user_id, ai_base_url, ai_key, ai_model, theme, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?, ?)
       ON CONFLICT(user_id) DO UPDATE SET ai_base_url=excluded.ai_base_url,
       ai_key=excluded.ai_key, ai_model=excluded.ai_model,
       theme=excluded.theme, updated_at=excluded.updated_at`
    )
    .bind(userId, ai_base_url, encryptedKey, ai_model, theme, now, now)
    .run();
}

async function encryptKey(key: string, env: any): Promise<string> {
  const encryptionKey = env.ENCRYPTION_KEY;
  if (!encryptionKey) throw new Error("Encryption key not configured in Worker env");

  const iv = crypto.getRandomValues(new Uint8Array(12));
  const keyBuffer = new TextEncoder().encode(encryptionKey);
  const cryptoKey = await crypto.subtle.importKey(
    "raw",
    keyBuffer,
    { name: "AES-GCM" },
    false,
    ["encrypt"]
  );

  const encrypted = await crypto.subtle.encrypt(
    { name: "AES-GCM", iv },
    cryptoKey,
    new TextEncoder().encode(key)
  );

  // Return base64 of iv + ciphertext
  return btoa(String.fromCharCode(...iv) + String.fromCharCode(...new Uint8Array(encrypted)));
}

async function decryptKey(encrypted: string, env: any): Promise<string> {
  const encryptionKey = env.ENCRYPTION_KEY;
  if (!encryptionKey) return "";

  try {
    const buffer = Uint8Array.from(atob(encrypted), (c) => c.charCodeAt(0));
    const iv = buffer.slice(0, 12);
    const ciphertext = buffer.slice(12);

    const keyBuffer = new TextEncoder().encode(encryptionKey);
    const cryptoKey = await crypto.subtle.importKey(
      "raw",
      keyBuffer,
      { name: "AES-GCM" },
      false,
      ["decrypt"]
    );

    const decrypted = await crypto.subtle.decrypt(
      { name: "AES-GCM", iv },
      cryptoKey,
      ciphertext
    );

    return new TextDecoder().decode(decrypted);
  } catch {
    return "";
  }
}