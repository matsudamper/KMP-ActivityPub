# スキーマの管理

`schema.sql` がスキーマの定義。jOOQ の生成コードはビルド時にこのファイルから作られる。実 DB へは [sqlite3def](https://github.com/sqldef/sqldef) で適用する。

```
sqlite3def --file backend/repository/src/main/resources/db/schema.sql /path/to/kmp-activitypub.db
```

形を変えるときは、開発用 DB を直接いじってから `./gradlew :backend:repository:dumpSchema -PdevDb=/絶対パス/dev.db` で `schema.sql` に書き出す。

ユーザーの追加手順はリポジトリ直下の README を参照。
