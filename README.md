# KMP-ActivityPub

ActivityPubのバックエンドとWebとAndroidフロントのアプリケーション

## ローカルで動かす

### DB を作る

スキーマは [sqlite3def](https://github.com/sqldef/sqldef) で適用する。

```sh
mkdir -p data
sqlite3def --file backend/repository/src/main/resources/db/schema.sql data/kmp-activitypub.db
```

### ユーザーを登録する

ユーザーは、パスワードのハッシュを作って sqlite3 で `accounts` に入れて登録する。

```sh
# 標準入力に 1 行でパスワードを渡すと、ハッシュが 1 行出る
HASH=$(./gradlew --quiet :backend:crypto:passwordHash) && \
sqlite3 data/kmp-activitypub.db \
  "INSERT INTO accounts (username, password_hash, password_hash_type, created_at) VALUES ('user1', '$HASH', 'argon2', strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));"
```

ユーザー名は大文字小文字を区別せず一意になる。区別しないのは ASCII の英字だけなので、使う文字は英数字と `_` `.` `-` に限る。ログインでは前後の空白を落として照合するので、前後に空白を入れない。

### 起動する

```sh
COOKIE_SECURE=false ./gradlew :backend:run
```

環境変数は `docker-compose.yml` を参照。

画面は `./gradlew :frontend:app-web:wasmJsBrowserDistribution` で作った
`frontend/app-web/build/dist/wasmJs/productionExecutable` を `STATIC_SRC_DIR` に渡すと
同じサーバーから出る。
