CREATE TABLE accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    -- 大文字小文字だけが違う名前を別々に持たせない
    username TEXT COLLATE NOCASE NOT NULL UNIQUE,
    created_at TEXT NOT NULL,
    -- 未設定なら NULL
    display_name TEXT,
    -- 画像の置き場の中でのプロフィール画像の場所。未設定なら NULL
    icon_path TEXT,
    last_active_at TEXT
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
    published_at TEXT NOT NULL,
    -- 画面が送信ごとに作る ID。応答だけ失われて送り直されても 2 つ目を作らない
    request_id TEXT
);

CREATE INDEX notes_account_id_published_at ON notes (account_id, published_at, public_id);

CREATE INDEX notes_published_at ON notes (published_at, public_id);

-- 投稿に付けた画像。position は付けた順
CREATE TABLE note_images (
    note_id INTEGER NOT NULL REFERENCES notes (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    -- 画像の URL に入る識別子
    public_id TEXT NOT NULL UNIQUE,
    -- 画像の置き場の中での場所
    path TEXT NOT NULL,
    content_type TEXT NOT NULL,
    description TEXT,
    PRIMARY KEY (note_id, position)
);

-- 置いたがまだ投稿に付けていない画像。投稿に付けたら note_images に移し、期限までに付かなければ消す
CREATE TABLE uploaded_note_images (
    public_id TEXT PRIMARY KEY,
    account_id INTEGER NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    path TEXT NOT NULL,
    content_type TEXT NOT NULL,
    description TEXT,
    expires_at TEXT NOT NULL
);

CREATE INDEX uploaded_note_images_expires_at ON uploaded_note_images (expires_at);

CREATE UNIQUE INDEX notes_account_id_request_id ON notes (account_id, request_id);

-- 相手の inbox に送る配信。1 行 = 1 宛先への 1 件。送れたら消し、諦めたら failed で残す
CREATE TABLE delivery_queue (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    -- accept_follow: Follow への Accept / create_note: 投稿の Create / update_actor: プロフィールの Update / delete_note: 投稿の Delete。
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

CREATE TABLE account_following (
    account_id INTEGER NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    actor_uri TEXT NOT NULL,
    created_at TEXT NOT NULL,
    follow_activity_uri TEXT,
    accepted_at TEXT,
    PRIMARY KEY (account_id, actor_uri)
);

CREATE INDEX account_following_actor_uri ON account_following (actor_uri, account_id);

CREATE TABLE remote_notes (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    object_uri TEXT NOT NULL UNIQUE,
    remote_actor_id INTEGER NOT NULL REFERENCES remote_actors (id) ON DELETE CASCADE,
    content_html TEXT NOT NULL,
    visibility TEXT NOT NULL,
    in_reply_to TEXT,
    summary TEXT,
    "sensitive" INTEGER NOT NULL,
    published_at TEXT NOT NULL,
    received_at TEXT NOT NULL,
    score_ms INTEGER NOT NULL,
    -- ブーストで知っただけの投稿は 1。後から Create が届いたら宛先を登録して 0 にする
    via_boost INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX remote_notes_actor_score ON remote_notes (remote_actor_id, score_ms DESC, id DESC);

CREATE TABLE remote_note_images (
    remote_note_id INTEGER NOT NULL REFERENCES remote_notes (id) ON DELETE CASCADE,
    position INTEGER NOT NULL,
    url TEXT NOT NULL,
    description TEXT,
    PRIMARY KEY (remote_note_id, position)
);

CREATE TABLE remote_note_recipients (
    remote_note_id INTEGER NOT NULL REFERENCES remote_notes (id) ON DELETE CASCADE,
    account_id INTEGER NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    kind TEXT NOT NULL,
    score_ms INTEGER NOT NULL,
    member TEXT NOT NULL,
    PRIMARY KEY (remote_note_id, account_id, kind)
);

CREATE INDEX remote_note_recipients_account ON remote_note_recipients (account_id, remote_note_id);

CREATE INDEX remote_notes_actor_score_member ON remote_notes (remote_actor_id, score_ms DESC, ('r' || CAST(id AS varchar)) DESC);

CREATE INDEX remote_note_recipients_account_score ON remote_note_recipients (account_id, kind, score_ms DESC, member DESC);

-- 他のサーバーから届いた、こちらの投稿へのお気に入り
CREATE TABLE note_favourites (
    note_id INTEGER NOT NULL REFERENCES notes (id) ON DELETE CASCADE,
    remote_actor_id INTEGER NOT NULL REFERENCES remote_actors (id) ON DELETE CASCADE,
    received_at TEXT NOT NULL,
    PRIMARY KEY (note_id, remote_actor_id)
);

CREATE INDEX note_favourites_remote_actor_id ON note_favourites (remote_actor_id);

-- Like より先に届いた Undo が指していた Like の id。後から届いた Like を記録しないために覚える
CREATE TABLE early_undone_likes (
    actor_uri TEXT NOT NULL,
    activity_uri TEXT NOT NULL,
    expires_at TEXT NOT NULL,
    PRIMARY KEY (actor_uri, activity_uri)
);

-- 他のサーバーから届いた、こちらの投稿への絵文字のスタンプ。1 人が同じ投稿に違う絵文字を押せる
CREATE TABLE note_stamps (
    note_id INTEGER NOT NULL REFERENCES notes (id) ON DELETE CASCADE,
    remote_actor_id INTEGER NOT NULL REFERENCES remote_actors (id) ON DELETE CASCADE,
    -- 絵文字そのもの、またはカスタム絵文字の :name:
    emoji TEXT NOT NULL,
    -- カスタム絵文字の画像の URL。Unicode の絵文字では NULL
    emoji_image_url TEXT,
    received_at TEXT NOT NULL,
    PRIMARY KEY (note_id, remote_actor_id, emoji)
);

CREATE INDEX note_stamps_remote_actor_id ON note_stamps (remote_actor_id);

-- 他のサーバーから届いた、こちらの投稿のブースト。Undo は Announce の id で取り消すので覚えておく
CREATE TABLE note_boosts (
    note_id INTEGER NOT NULL REFERENCES notes (id) ON DELETE CASCADE,
    remote_actor_id INTEGER NOT NULL REFERENCES remote_actors (id) ON DELETE CASCADE,
    activity_uri TEXT NOT NULL,
    received_at TEXT NOT NULL,
    PRIMARY KEY (note_id, remote_actor_id)
);

CREATE INDEX note_boosts_remote_actor_id_activity_uri ON note_boosts (remote_actor_id, activity_uri);

-- フォローしている相手が他のサーバーの投稿をブーストしたもの。タイムラインには remote_actor_id の相手のフォロワーに出す
CREATE TABLE remote_boosts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    activity_uri TEXT NOT NULL UNIQUE,
    remote_actor_id INTEGER NOT NULL REFERENCES remote_actors (id) ON DELETE CASCADE,
    remote_note_id INTEGER NOT NULL REFERENCES remote_notes (id) ON DELETE CASCADE,
    score_ms INTEGER NOT NULL
);

CREATE UNIQUE INDEX remote_boosts_actor_note ON remote_boosts (remote_actor_id, remote_note_id);

CREATE INDEX remote_boosts_actor_score ON remote_boosts (remote_actor_id, score_ms DESC, id DESC);

CREATE INDEX remote_boosts_remote_note_id ON remote_boosts (remote_note_id);
