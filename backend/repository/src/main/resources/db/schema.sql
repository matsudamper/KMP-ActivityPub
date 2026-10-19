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

-- ActivityPub の HTTP Signatures に使う
CREATE TABLE account_keys (
    account_id INTEGER PRIMARY KEY REFERENCES accounts (id) ON DELETE CASCADE,
    public_key_pem TEXT NOT NULL,
    private_key_pem TEXT NOT NULL
);

-- 他のサーバーのアクター。フォロワーが消えた後の Delete を検証できるよう、フォローが外れても鍵は残す
CREATE TABLE remote_actors (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    actor_uri TEXT NOT NULL UNIQUE,
    inbox TEXT NOT NULL,
    shared_inbox TEXT,
    public_key_pem TEXT NOT NULL,
    preferred_username TEXT,
    display_name TEXT,
    profile_url TEXT,
    icon_url TEXT,
    updated_at TEXT NOT NULL
);

CREATE TABLE account_followers (
    account_id INTEGER NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    remote_actor_id INTEGER NOT NULL REFERENCES remote_actors (id) ON DELETE CASCADE,
    follow_activity_uri TEXT NOT NULL,
    received_at TEXT NOT NULL,
    -- Accept を送れるまでは NULL。NULL の間はフォローが成立していない
    accepted_at TEXT,
    PRIMARY KEY (account_id, remote_actor_id)
);

-- 送れたら行を消す
CREATE TABLE follow_accept_deliveries (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL,
    remote_actor_id INTEGER NOT NULL,
    body TEXT NOT NULL,
    attempts INTEGER NOT NULL,
    next_attempt_at TEXT NOT NULL,
    UNIQUE (account_id, remote_actor_id),
    FOREIGN KEY (account_id, remote_actor_id) REFERENCES account_followers (account_id, remote_actor_id) ON DELETE CASCADE
);

CREATE TABLE health_check (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    checked_at TEXT NOT NULL
);
