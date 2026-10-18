package net.matsudamper.kmp.activitypub.delivery

import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kotpub.actor.ActorPrivateKey
import net.matsudamper.kotpub.actor.ActorPrivateKeyLoader
import net.matsudamper.kotpub.actor.ActorPrivateKeySource

/**
 * 配信に署名するこちらのアカウントの秘密鍵の引き先
 */
fun interface LocalActorKeys {
    fun find(username: String): ActorPrivateKey?
}

internal class RepositoryLocalActorKeys(
    private val accountRepository: AccountRepository,
) : LocalActorKeys {
    override fun find(username: String): ActorPrivateKey? {
        val pem = accountRepository.findPrivateKeyPem(username) ?: return null
        return ActorPrivateKeyLoader.load(ActorPrivateKeySource.Pem(pem))
    }
}
