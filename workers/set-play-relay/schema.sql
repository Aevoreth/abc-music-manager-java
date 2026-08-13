-- Set Play relay D1 registry (infrequent session metadata)

CREATE TABLE IF NOT EXISTS relay_config (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  token_hash TEXT NOT NULL,
  created_at TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS session (
  code TEXT PRIMARY KEY,
  name TEXT NOT NULL,
  passphrase_hash TEXT NOT NULL,
  setlist_id INTEGER,
  set_name TEXT,
  notes TEXT,
  set_date TEXT,
  set_time TEXT,
  r2_key TEXT,
  expires_at TEXT,
  created_at TEXT NOT NULL,
  updated_at TEXT NOT NULL
);
