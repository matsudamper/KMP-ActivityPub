package net.matsudamper.kmp.activitypub.federation

internal class FederationUrls(
    val localDomain: String,
) {
    private val origin = "https://$localDomain"

    fun actor(username: String): String = "$origin/users/$username"

    fun inbox(username: String): String = "${actor(username)}/inbox"

    fun publicKey(username: String): String = "${actor(username)}#main-key"

    /**
     * 画面側のプロフィールのパス
     */
    fun profilePage(username: String): String = "$origin/@$username"

    fun acct(username: String): String = "acct:$username@$localDomain"

    /**
     * `acct:user@example.com` と Actor の URL を受ける。他のドメインなら null
     */
    fun usernameOf(resource: String): String? {
        val actorPrefix = actor("")
        if (resource.startsWith(actorPrefix)) {
            return resource.removePrefix(actorPrefix).takeIf { it.isNotEmpty() && '/' !in it }
        }

        val acct = resource.removePrefix("acct:").removePrefix("@")
        val atIndex = acct.lastIndexOf('@').takeIf { it >= 0 } ?: return null
        val domain = acct.substring(atIndex + 1)
        if (!domain.equals(localDomain, ignoreCase = true)) return null
        return acct.substring(0, atIndex).takeIf { it.isNotEmpty() }
    }
}
