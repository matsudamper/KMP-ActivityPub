# スキーマの管理

`schema.sql` がスキーマの定義。実 DB へは [sqlite3def](https://github.com/sqldef/sqldef) で適用する。

```
sqlite3def --file backend/repository/src/main/resources/db/schema.sql /path/to/kmp-activitypub.db
```

ユーザーの追加手順はリポジトリ直下の README を参照。

## 現在のテーブル

| テーブル | 内容 |
| --- | --- |
| `accounts` | ログインできるユーザー。`username` は大文字小文字を区別せず一意。パスワードはハッシュで持つ |
