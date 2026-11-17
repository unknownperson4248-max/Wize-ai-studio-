-- NOVA backend database (Cloudflare D1)

-- Phones allowed to talk to the assistant. Only a hash of each device token is stored.
CREATE TABLE IF NOT EXISTS devices (
  id TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  token_hash TEXT NOT NULL UNIQUE,
  created_at INTEGER NOT NULL,
  last_seen INTEGER
);

-- Conversation, kept on the server so it survives app reinstalls and model changes.
-- role: user | assistant | tool
CREATE TABLE IF NOT EXISTS messages (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  device_id TEXT NOT NULL,
  role TEXT NOT NULL,
  content TEXT NOT NULL,
  created_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS messages_device_time ON messages (device_id, id);

-- Long-term facts the assistant was asked to remember ("my sister is called Mim").
CREATE TABLE IF NOT EXISTS memories (
  id INTEGER PRIMARY KEY AUTOINCREMENT,
  text TEXT NOT NULL,
  created_at INTEGER NOT NULL
);

-- Pairing attempts per IP hash, to stop guessing the pairing code.
CREATE TABLE IF NOT EXISTS pair_attempts (
  ip_hash TEXT NOT NULL,
  at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS pair_attempts_ip ON pair_attempts (ip_hash, at);
