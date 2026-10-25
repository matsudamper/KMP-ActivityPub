package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.delivery.DeliverySenders
import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kotpub.actor.ActorDirectory
import net.matsudamper.kotpub.actor.ActorPrivateKey
import net.matsudamper.kotpub.actor.LocalActor
import net.matsudamper.kotpub.crypto.RsaKeys

internal class LocalActors(
    private val directory: ActorDirectory,
    private val accountService: AccountService,
) : DeliverySenders {
    override fun find(username: String): LocalActor? {
        val urls = directory.resolve(username) ?: return null
        val privateKeyPem = accountService.privateKeyPem(urls.username) ?: return null
        return LocalActor(
            urls = urls,
            privateKey = ActorPrivateKey(
                privateKey = RsaKeys.decodePrivateKeyPem(privateKeyPem),
                // DB から読んだ鍵に当たる Origin が無い。Origin は起動ログにしか使われない
                origin = ActorPrivateKey.Origin.Environment,
            ),
        )
    }
}
