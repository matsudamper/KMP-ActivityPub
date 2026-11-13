# GraphQL

## 共通ルール

### 識別子の型
- 識別子に組み込みの `ID` を使わない。何の識別子かが分かる名前の scalar を定義して使う（例: `UserId`、`NoteImageId`、`FollowingGroupId`）
- `ID` のままだと、別の種類の識別子を取り違えて渡してもスキーマでもコードでも気付けない

## リポジトリ固有ルール

### scalar の追加
- scalar を足したら、次をそろえる
  - `backend/graphql/src/main/resources/graphql/scalars.graphqls` に `scalar` を宣言する
  - `backend/graphql/build.gradle.kts` の `customTypesMapping` で Kotlin の型に対応させる。型は `shared` に置く
  - `GraphQlScalars` に Coercing を足し、`GraphQlEngine` の `scalars` に登録する
  - `frontend/api/build.gradle.kts` で `mapScalarToKotlinString` に足す
