# スキーマの管理

`schema.sql` が、スキーマの唯一の定義。
テーブルの型（jOOQ の生成コード）もここから作られる。

マイグレーションの履歴管理はしていない。
開発用 DB を直接いじって形を決め、`schema.sql` に書き出して commit し、実 DB へは sqlite3def で手適用する。

## スキーマの変えかた

1. 開発用 DB（ローカルの SQLite ファイル。git には入れない）を sqlite3 などで直接いじって形を決める
2. `./gradlew :backend:repository:dumpSchema -PdevDb=/絶対パス/dev.db` で `schema.sql` に書き出す
3. diff を見て commit する

`schema.sql` は手で編集しない。

## 実 DB への適用のしかた

[sqlite3def](https://github.com/sqldef/sqldef) をローカルで手で叩く。
`schema.sql` と実 DB の差分を計算して適用してくれる。

```
sqlite3def --file backend/repository/src/main/resources/db/schema.sql /path/to/kmp-activitypub.db
```

起動時の自動適用は無いので、新しい binary を動かす前に適用する。

## スキーマから対応するコードが生成されるまで

jOOQ の生成コードは、`schema.sql` からビルド時に作られる。

1. `buildJooqSchema` が使い捨ての SQLite (`build/jooq/schema.db`) を作り直し、`schema.sql` を適用する
2. `generateJooq` がその DB を読んで `build/generated/jooq` に Java を生成する
3. `compileKotlin` / `compileJava` が生成物を含めてコンパイルする

これらのタスクは `build-logic` の `DatabaseCodegenPlugin` が登録する。生成コードは git に入れない。

## テストでの使いかた

テストは `TestSchema.applyTo(dbPath)` で `schema.sql` を適用してから `Repositories.create` する。

## 現在のテーブル

| テーブル | 内容 |
| --- | --- |
| `accounts` | ログインできるユーザー。`username` は大文字小文字を区別せず一意。パスワードはハッシュで持つ |
| `user_sessions` | ログイン中のセッション。トークンの SHA-256 とアカウント、期限 |
| `health_check` | 起動時の書き込み確認用。行は常に 1 件 |
