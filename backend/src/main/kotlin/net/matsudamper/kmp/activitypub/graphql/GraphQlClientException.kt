package net.matsudamper.kmp.activitypub.graphql

/**
 * クライアントの送り方が原因で実行を止めるときに投げる。サーバーの障害ではないので ERROR では記録しない
 */
class GraphQlClientException(
    message: String,
) : Exception(message)
