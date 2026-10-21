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
    -- webauthn4j の AttestedCredentialDataConverter の形
    attested_credential_data BLOB NOT NULL,
    sign_count INTEGER NOT NULL,
    -- ユーザー入力値
    name TEXT NOT NULL,
    created_at TEXT NOT NULL,
    last_used_at TEXT
);

CREATE INDEX passkeys_account_id ON passkeys (account_id);

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

-- 相手がパーマリンクや outbox を引きに来るので、配った投稿は残す
CREATE TABLE notes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    account_id INTEGER NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    public_id TEXT NOT NULL UNIQUE,
    -- サニタイズ済みの HTML
    content_html TEXT NOT NULL,
    published_at TEXT NOT NULL
);

CREATE INDEX notes_account_id_published_at ON notes (account_id, published_at, public_id);

CREATE INDEX notes_published_at ON notes (published_at, public_id);

-- 相手の inbox に送る配信。1 行 = 1 宛先への 1 件。送れたら消し、諦めたら failed で残す
CREATE TABLE delivery_queue (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    -- accept_follow: Follow への Accept / create_note: 投稿の Create。
    -- 行の形は種別に依らないので、種別を足しても作り直さずに済む。取りうる値は DeliveryQueueValues が持つ
    kind TEXT NOT NULL,
    -- 署名するこちらのアカウント
    account_id INTEGER NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    -- accept_follow で、送れたときに成立させるフォローの相手。関係しない種別と、諦めた行では NULL
    remote_actor_id INTEGER,
    -- create_note で配る投稿。投稿を消したら、まだ送っていない Create も消す。
    -- 残すと消したはずの投稿が後から届く。関係しない種別では NULL
    note_id INTEGER REFERENCES notes (id) ON DELETE CASCADE,
    inbox TEXT NOT NULL,
    -- 諦めた行は二度と送らないので NULL にする
    body TEXT,
    -- delivering のまま残った行は、送っている途中で落ちた分
    state TEXT NOT NULL,
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

-- 投稿を消したときに、外部キーで消す行をこれで引く
CREATE INDEX delivery_queue_note_id ON delivery_queue (note_id);

CREATE TABLE health_check (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    checked_at TEXT NOT NULL
);
