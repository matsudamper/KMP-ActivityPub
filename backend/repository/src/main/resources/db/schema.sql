CREATE TABLE accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    -- 大文字小文字だけが違う名前を別々に持たせない
    username TEXT COLLATE NOCASE NOT NULL UNIQUE,
    created_at TEXT NOT NULL
);

-- 行が無ければパスワードは無い
CREATE TABLE account_passwords (
    account_id INTEGER PRIMARY KEY REFERENCES accounts (id) ON DELETE CASCADE,
    password_hash TEXT NOT NULL,
    -- :backend:crypto の PasswordHashType.dbValue
    password_hash_type TEXT NOT NULL
);

CREATE TABLE user_sessions (
    token_hash TEXT NOT NULL PRIMARY KEY,
    account_id INTEGER NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL
);

CREATE INDEX user_sessions_account_id ON user_sessions (account_id);

-- WebAuthn の user.id。アカウントの id を認証器に渡さないよう、乱数を別に持つ
CREATE TABLE passkey_user_handles (
    account_id INTEGER PRIMARY KEY REFERENCES accounts (id) ON DELETE CASCADE,
    -- base64url
    user_handle TEXT NOT NULL UNIQUE
);

CREATE TABLE passkeys (
    -- base64url
    credential_id TEXT NOT NULL PRIMARY KEY,
    account_id INTEGER NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    -- COSE_Key
    public_key BLOB NOT NULL,
    sign_count INTEGER NOT NULL,
    created_at TEXT NOT NULL,
    last_used_at TEXT
);

CREATE INDEX passkeys_account_id ON passkeys (account_id);

-- 発行した challenge。使ったら消して、同じものを二度通さない
CREATE TABLE passkey_challenges (
    challenge TEXT NOT NULL PRIMARY KEY,
    -- :backend:repository の PasskeyChallengePurpose.dbValue
    purpose TEXT NOT NULL,
    -- 登録では誰のための challenge かを持つ。ログインでは NULL
    account_id INTEGER REFERENCES accounts (id) ON DELETE CASCADE,
    expires_at TEXT NOT NULL
);

CREATE TABLE health_check (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    checked_at TEXT NOT NULL
);
