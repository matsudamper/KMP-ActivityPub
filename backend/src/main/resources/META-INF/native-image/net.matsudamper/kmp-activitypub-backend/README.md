# native-image の設定

どれも JVM のテストでは再現しないので、native バイナリを起動して確かめる。

## `resource-config.json`

- `graphql/*.graphqls` と `graphql/schema-list.txt`。native バイナリではディレクトリを列挙できないので一覧を別に持つ
- graphql-java の `i18n.*`。`SchemaParser` が起動時に引く

## GraphQL の結線

kickstart がリフレクションで結線するので、`GraphQlReflectionFeature` がビルド時に
`graphql.model` と `graphql.resolver` を走査して登録する。あわせて次も要る。

- `kotlin.reflect.jvm.internal.ReflectionFactoryImpl` の登録（kickstart が kotlin-reflect を使う）
- `*.kotlin_builtins` と `*.kotlin_module` の同梱（kotlin-reflect が読む）

## kotlinx.serialization

送受信とも serializer を明示する（`json/JsonResponse.kt`）。
Ktor の `ContentNegotiation` は serializer をリフレクションで引くため。

## リフレクションで詰まったら

tracing agent で原因を特定する。出力は特定に使い、設定はリフレクションを避ける書き方で直す。

    ./gradlew :backend:run -Pagent
    ./gradlew :backend:metadataCopy --task run \
      --dir backend/src/main/resources/META-INF/native-image/net.matsudamper/kmp-activitypub-backend
