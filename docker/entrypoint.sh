#!/bin/sh
set -eu

# 起動時の自動適用は無いので、テーブルが 1 つも無い（初回の）DB にだけスキーマを入れる。
# 既にある DB のスキーマの変更は sqlite3def で手で行う
table_count=$(sqlite3 "$DB_PATH" "SELECT count(*) FROM sqlite_master WHERE type = 'table'")
if [ "$table_count" = "0" ]; then
    sqlite3 "$DB_PATH" < /app/schema.sql
fi

exec /app/kmp-activitypub
