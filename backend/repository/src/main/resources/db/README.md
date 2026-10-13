# スキーマの管理

`schema.sql` がスキーマの定義。jOOQ の生成コードはビルド時にこのファイルから作られる。実 DB へは [sqlite3def](https://github.com/sqldef/sqldef) で適用する。

```
sqlite3def --file backend/repository/src/main/resources/db/schema.sql /path/to/kmp-activitypub.db
```

ユーザーの追加手順はリポジトリ直下の README を参照。
