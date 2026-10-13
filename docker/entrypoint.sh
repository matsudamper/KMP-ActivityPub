#!/bin/sh
set -eu

# 起動時の自動適用は無いので、テーブルが 1 つも無い（初回の）DB にだけスキーマを入れる。
# 既にある DB のスキーマの変更は sqlite3def で手で行う
mkdir -p "$(dirname "$DB_PATH")"
table_count=$(sqlite3 "$DB_PATH" "SELECT count(*) FROM sqlite_master WHERE type = 'table'")
if [ "$table_count" = "0" ]; then
    # 途中で失敗すると一部のテーブルだけが残り、次からは適用を飛ばしてしまうので 1 トランザクションで入れる
    { echo "BEGIN;"; cat /app/schema.sql; echo "COMMIT;"; } | sqlite3 -bail "$DB_PATH"
fi

exec /app/kmp-activitypub
