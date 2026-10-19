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

-- 相手の inbox に送る配信。1 行 = 1 宛先への 1 件。送れたら消し、諦めたら failed で残す
CREATE TABLE delivery_queue (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    -- accept_follow: Follow への Accept。行の形は種別に依らないので、種別を足しても作り直さずに済む
    kind TEXT NOT NULL CHECK (kind IN ('accept_follow')),
    -- 署名するこちらのアカウント
    account_id INTEGER NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    -- accept_follow で、送れたときに成立させるフォローの相手。関係しない種別では NULL
    remote_actor_id INTEGER,
    inbox TEXT NOT NULL,
    -- 諦めた行は二度と送らないので NULL にする
    body TEXT,
    -- delivering のまま残った行は、送っている途中で落ちた分
    state TEXT NOT NULL CHECK (state IN ('pending', 'delivering', 'failed')),
    -- 送っている途中で落ちた分も数えるため、送り終えたときではなく取り出したときに増やす
    attempts INTEGER NOT NULL,
    -- failed では NULL
    next_attempt_at TEXT,
    enqueued_at TEXT NOT NULL,
    last_error TEXT,
    -- フォローが外れたら、その Accept はもう送らない
    FOREIGN KEY (account_id, remote_actor_id) REFERENCES account_followers (account_id, remote_actor_id) ON DELETE CASCADE
);

CREATE INDEX delivery_queue_state_next_attempt_at ON delivery_queue (state, next_attempt_at);

CREATE TABLE health_check (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    checked_at TEXT NOT NULL
);
