-- スキーマの定義。jOOQ の生成コードはビルド時にこのファイルから作られる。
--
-- 開発用 DB で形を決めて
--   ./gradlew :backend:repository:dumpSchema -PdevDb=/絶対パス/dev.db
-- で書き出す。実 DB へは sqlite3def で適用する。詳細は同じディレクトリの README.md
CREATE TABLE accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    -- 大文字小文字だけが違う名前を別々に持たせない
    username TEXT COLLATE NOCASE NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    -- :backend:crypto の PasswordHashType.dbValue
    password_hash_type TEXT NOT NULL,
    created_at TEXT NOT NULL
);

CREATE TABLE health_check (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    checked_at TEXT NOT NULL
);

CREATE TABLE user_sessions (
    token_hash TEXT NOT NULL PRIMARY KEY,
    account_id INTEGER NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    created_at TEXT NOT NULL,
    expires_at TEXT NOT NULL
);

CREATE INDEX user_sessions_account_id ON user_sessions (account_id);
