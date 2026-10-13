package net.matsudamper.kmp.activitypub.graphql

/**
 * クライアント起因なので ERROR では記録しない
 */
class GraphQlClientException(
    message: String,
) : Exception(message)
