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

`data/kmp-activitypub.db` に登録する。ユーザー名とパスワードは、聞かれたときに入力する。

```sh
./gradlew --quiet :cli:addAccount
```

ユーザー名は大文字小文字を区別せず一意になる。区別しないのは ASCII の英字だけなので、使う文字は英数字と `_` `.` `-` に限る。ログインでは前後の空白を落として照合するので、前後に空白を入れない。

### Valkey を起動する

セッションは Valkey に保存する。

```sh
docker run -d --name kmp-activitypub-valkey -p 6379:6379 valkey/valkey:8
```

### 起動する

```sh
COOKIE_SECURE=false VALKEY_URI=redis://localhost:6379 PASSKEY_RP_ID=localhost PASSKEY_ORIGINS=http://localhost:8080 ./gradlew :backend:run
```

環境変数は `docker-compose.yml` を参照。

画面は `./gradlew :frontend:app-web:wasmJsBrowserDistribution` で作った
`frontend/app-web/build/dist/wasmJs/productionExecutable` を `STATIC_SRC_DIR` に渡すと
同じサーバーから出る。
