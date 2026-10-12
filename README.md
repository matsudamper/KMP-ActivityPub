# KMP-ActivityPub

ActivityPubのバックエンドとWebとAndroidフロントのアプリケーション

## ローカルで動かす

### DB を作る

スキーマは [sqlite3def](https://github.com/sqldef/sqldef) で適用する。起動時の自動適用は無い。

```sh
mkdir -p data
sqlite3def --file backend/repository/src/main/resources/db/schema.sql data/kmp-activitypub.db
```

### ユーザーを登録する

登録の画面や API は無い。パスワードのハッシュを作り、sqlite3 で直接入れる。

```sh
# 標準入力に 1 行でパスワードを渡すと、ハッシュが 1 行出る
HASH=$(./gradlew --quiet :backend:crypto:passwordHash)

sqlite3 data/kmp-activitypub.db \
  "INSERT INTO accounts (username, password_hash, created_at) VALUES ('user1', '$HASH', strftime('%Y-%m-%dT%H:%M:%fZ', 'now'));"
```

ユーザー名は大文字小文字を区別せず一意になる。

### 起動する

```sh
COOKIE_SECURE=false ./gradlew :backend:run
```

| 環境変数 | 既定値 | 内容 |
| --- | --- | --- |
| `HOST` | `0.0.0.0` | 待ち受けるアドレス |
| `PORT` | `8080` | 待ち受けるポート |
| `DB_PATH` | `./data/kmp-activitypub.db` | SQLite の DB ファイル |
| `COOKIE_SECURE` | `true` | セッション Cookie に `Secure` を付けるか。http で試すときは `false` |
| `STATIC_SRC_DIR` | なし | 画面の配布物を置いたディレクトリ |

画面は `./gradlew :frontend:app-web:wasmJsBrowserDistribution` で作った
`frontend/app-web/build/dist/wasmJs/productionExecutable` を `STATIC_SRC_DIR` に渡すと
同じサーバーから出る。
