# スキーマの管理

`schema.sql` がスキーマの定義。jOOQ の生成コードはビルド時にこのファイルから作られる。実 DB へは [sqlite3def](https://github.com/sqldef/sqldef) で適用する。

```
sqlite3def --file backend/repository/src/main/resources/db/schema.sql /path/to/kmp-activitypub.db
```

ユーザーの追加手順はリポジトリ直下の README を参照。

## CHECK 制約は使わない

列の取りうる値を CHECK 制約で縛らない。値の制限は Kotlin 側（例: `DeliveryQueueValues`）に持つ。
`health_check` の `CHECK (id = 1)` は行を 1 つに限るためのもので、条件を変えることが無いので残している。

SQLite の CHECK はテーブル定義の一部で、`ALTER TABLE` では変えられない。sqlite3def もテーブルを作り直すところまではしないので、`schema.sql` の CHECK を書き換えても既存の DB には反映されず、新しい値の INSERT が失敗する。

既存の DB に残った CHECK を外すには、テーブルを作り直す（サーバーを止めてバックアップしてから）。インデックスは名前を変えた古いテーブルに同じ名前で付いたまま残るので、古いテーブルを消してから作る。`delivery_queue` の例:

```sql
PRAGMA foreign_keys = OFF;
BEGIN;
ALTER TABLE delivery_queue RENAME TO delivery_queue_old;
-- ここに schema.sql の CREATE TABLE delivery_queue (...) だけを貼る
INSERT INTO delivery_queue (id, kind, account_id, remote_actor_id, note_id, inbox, body, state, attempts, next_attempt_at, enqueued_at, last_error)
  SELECT id, kind, account_id, remote_actor_id, note_id, inbox, body, state, attempts, next_attempt_at, enqueued_at, last_error FROM delivery_queue_old;
DROP TABLE delivery_queue_old;
-- ここに schema.sql の delivery_queue の CREATE INDEX を貼る
COMMIT;
PRAGMA foreign_key_check;
PRAGMA foreign_keys = ON;
```
