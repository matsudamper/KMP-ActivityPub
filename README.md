# KMP-ActivityPub

ActivityPub のバックエンドと、Web と Android のフロントのアプリケーション。
管理画面で登録したユーザーがログインして投稿すると、Mastodon などのフォロワーに配信される。

横断的な設計は [docs/architecture.md](docs/architecture.md)、Mastodon から見える仕様
（エンドポイント・鍵の扱いなど）は [docs/mastodon-spec.md](docs/mastodon-spec.md) を参照。
これからやることは [GitHub の Issue](https://github.com/matsudamper/KMP-ActivityPub/issues) に置く。

ActivityPub の実装は [matsudamper/kotpub](https://github.com/matsudamper/kotpub) を使う。
いまできるのは配信まで（フォローを受けて、投稿をフォロワーに届ける）。

## 技術スタック

- backend: Ktor・GraalVM native-image・SQLite（jOOQ）・GraphQL（graphql-java-tools）
- GraphQL のコード生成: [matsudamper/graphql-java-codegen](https://github.com/matsudamper/graphql-java-codegen)（サーバー）、Apollo Kotlin（画面）
- frontend: Compose Multiplatform（Web は Kotlin/Wasm）と Jetpack Compose（Android）

## 必要なもの

JDK が 1 つあれば足りる。バージョンは問わない（Gradle を起動できればよい）。

ビルドに使う JDK 25 と、native-image に使う GraalVM 25 は Gradle が必要に応じて
取ってくる（`settings.gradle.kts` の foojay-resolver）。

Android アプリをビルドするときは Android SDK が要る（`local.properties` の `sdk.dir` か `ANDROID_HOME`）。

### GitHub Packages の資格情報

graphql-java-codegen の fork と kotpub の `activitypub` は GitHub Packages から取る。
無いと構成の時点で落ちるので、`~/.gradle/gradle.properties` に置く。

```properties
gpr.user=<GitHub のユーザー名>
gpr.key=<read:packages を付けたパーソナルアクセストークン>
```

環境変数 `GITHUB_ACTOR` / `GITHUB_TOKEN` でも読む。CI はそちらを使っている。
CI の `GITHUB_TOKEN` で読めるよう、2 つのパッケージの設定でこのリポジトリに読み取りを許可しておく。

## ビルド

```sh
# 全モジュールのコンパイル
./gradlew :compileAll

# テスト
./gradlew test
```

### backend

```sh
# JVM で起動する（http://localhost:8080）
# DOMAIN は必須。Mastodon から実際に引かせるときは公開しているホスト名にすること
DOMAIN=example.com ./gradlew :backend:run

# ネイティブバイナリを生成して起動する
./gradlew :backend:nativeCompile
DOMAIN=example.com ./backend/build/native/nativeCompile/kmp-activitypub
```

起動できたかは `GET /healthz` で見る。`{"status":"ok"}` が返れば動いている。

DB のスキーマは起動時に適用しない。先に sqlite3def などで `backend/repository/src/main/resources/db/schema.sql` を
適用しておく（[backend/repository/src/main/resources/db/README.md](backend/repository/src/main/resources/db/README.md)）。

### Web

```sh
# 開発サーバー（http://localhost:8081、ホットリロードあり）
./gradlew :frontend:wasmJsBrowserDevelopmentRun

# production 用の配布物（frontend/build/dist/wasmJs/productionExecutable/）
./gradlew :frontend:wasmJsBrowserDistribution
```

backend から画面を出すときは、配布物のディレクトリを `STATIC_SRC_DIR` に渡す。

```sh
DOMAIN=example.com \
STATIC_SRC_DIR=frontend/build/dist/wasmJs/productionExecutable \
ADMIN_PASSWORD_HASH='pbkdf2-sha256:...' \
COOKIE_SECURE=false \
  ./gradlew :backend:run
```

### Android

```sh
./gradlew :android:assembleDebug
```

初回起動で接続先のサーバーの URL を入れる。ログインの Cookie はアプリに残るので、
次からはそのまま投稿できる。Android アプリに管理画面は無い。

## 使い方

1. 管理画面のパスワードハッシュを作る。標準入力にパスワードを渡す（引数にするとシェルの履歴に残る）

   ```sh
   # 表示された 1 行がそのまま ADMIN_PASSWORD_HASH の値
   ./gradlew --quiet :backend:crypto:passwordHash
   ```

2. `/admin` で管理画面にログインし、「ユーザーの登録」でユーザー名とパスワードを決める
3. `/`（Android ならアプリ）でそのユーザーとしてログインし、左のカラムから投稿する
4. Mastodon の検索窓に `@<ユーザー名>@<DOMAIN>` を入れるとフォローできる。フォロー後の投稿が届く

ユーザー名は後から変えられない。Mastodon はリモートのアカウントを永続キャッシュするので、
アクターの ID が変わると相手には別のアカウントとして見える。
使える文字は英数字と `_` `.` `-` で、先頭と末尾は英数字か `_`、長さは 30 文字まで。
パスワードは 8 文字以上。

### 画面のパス

| パス | 画面 |
| --- | --- |
| `/` | トップ。Mastodon と同じカラム方式で、いまは左端の投稿カラム（ログインと投稿）だけ |
| `/admin` | 管理画面のトップ。ログインと、下の各画面への入口 |
| `/admin/accounts` | ユーザーの一覧 |
| `/admin/accounts/new` | ユーザーの登録 |
| それ以外 | 見つからない（HTTP は 200 のまま） |

## 環境変数

| 変数 | 既定値 | 内容 |
| --- | --- | --- |
| `HOST` | `0.0.0.0` | バインドするアドレス |
| `PORT` | `8080` | 待ち受けポート |
| `DB_PATH` | `./data/kmp-activitypub.db` | SQLite の DB ファイル |
| `DOMAIN` | 必須 | 外部に公開するドメイン。WebFinger の `acct:` とアクターの `id` に使う |
| `ACTOR_PRIVATE_KEY_PATH` | `./data/actor-private-key.pem` | アクターの秘密鍵 (PEM)。無ければ起動時に生成して書き出す |
| `ACTOR_PRIVATE_KEY_PEM` | なし | 秘密鍵の PEM を直接渡す場合に使う。`ACTOR_PRIVATE_KEY_PATH` とは併用できない |
| `STATIC_SRC_DIR` | なし | 配信する静的ファイルのディレクトリ。未設定なら画面を出さない |
| `ADMIN_PASSWORD_HASH` | なし | 管理画面のパスワードハッシュ。未設定でも起動するが、その間は管理画面にログインできない |
| `COOKIE_SECURE` | `true` | 管理画面とユーザーのセッション Cookie に `Secure` を付けるか。手元で http で試すときだけ `false` にする |

`DOMAIN` はアクターの ID に焼き込まれ、変えると相手からは別人のアカウントに見える。

## モジュール

| モジュール | 内容 |
| --- | --- |
| `:backend` | サーバーの組み立て。ルーティング・GraphQL のリゾルバ・配信ワーカー |
| `:backend:crypto` | パスワードのハッシュ |
| `:backend:graphql` | GraphQL のスキーマと、そこから生成したモデル・リゾルバのインタフェース |
| `:backend:repository` | SQLite への保存 |
| `:shared` | backend と画面の両方から見る値 |
| `:frontend` | Web アプリの入口 |
| `:frontend:feature:home` | トップのカラム画面（Web と Android） |
| `:frontend:feature:admin` | 管理画面（Web のみ） |
| `:frontend:navigation` | Web の画面のパスと遷移の口 |
| `:frontend:api` | GraphQL クライアント |
| `:frontend:ui` | 画面部品 |
| `:frontend:common-component` | HTML の input を載せる部品 |
| `:android` | Android アプリ |

分け方の理由は [docs/architecture.md](docs/architecture.md) の「モジュールの分け方」を参照。
