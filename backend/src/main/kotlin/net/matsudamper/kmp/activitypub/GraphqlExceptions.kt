package net.matsudamper.kmp.activitypub

sealed class GraphqlExceptions(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause) {
    class Admin : GraphqlExceptions("管理画面にログインしていない")

    class User : GraphqlExceptions("ユーザーとしてログインしていない")
}
