CREATE TABLE accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    -- 大文字小文字だけが違う名前を別々に持たせない
    username TEXT COLLATE NOCASE NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    -- :backend:crypto の PasswordHashType.dbValue
    password_hash_type TEXT NOT NULL,
    created_at TEXT NOT NULL
);
