-- スキーマの唯一の定義。jOOQ の生成コードはビルド時にこのファイルから作られる。
--
-- 手で編集しない。開発用 DB を直接いじって形を決めたら
--   ./gradlew :backend:repository:dumpSchema -PdevDb=/絶対パス/dev.db
-- で書き出して commit する。実 DB への適用は sqlite3def で手動。
-- 詳細は同じディレクトリの README.md を参照。
CREATE TABLE accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    -- ログインに使う名前。大文字小文字だけが違う名前を別々に持てると、
    -- どちらでログインしたのか決まらないので NOCASE で一意にする
    username TEXT COLLATE NOCASE NOT NULL UNIQUE,
    -- ログインに使うパスワードのハッシュ。:backend:crypto の PasswordHash.encode の形
    password_hash TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE health_check (
    -- 起動時の書き込み確認に使うテーブル。
    -- 行は常に 1 件だけなので、CHECK で id を固定して UPSERT の対象を一意にする
    id INTEGER PRIMARY KEY CHECK (id = 1),
    checked_at TEXT NOT NULL
);

CREATE TABLE user_sessions (
    -- ログイン中のセッション。Cookie に入れるトークンそのものは持たず、SHA-256 のハッシュを持つ。
    -- DB が漏れても、そのままセッションとして使えない
    token_hash TEXT NOT NULL PRIMARY KEY,
    account_id INTEGER NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL
);

CREATE INDEX user_sessions_account_id ON user_sessions (account_id);
