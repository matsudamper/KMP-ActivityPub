package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.repository.InstanceActorKeyRepository
import net.matsudamper.kotpub.actor.ActorPrivateKey
import net.matsudamper.kotpub.actor.InstanceActor
import net.matsudamper.kotpub.actor.InstanceActorUrls
import net.matsudamper.kotpub.crypto.RsaKeys

/**
 * 保存した鍵でサーバー自身のアクターを作る。鍵がまだ無ければ作って保存する
 */
internal fun storedInstanceActor(domain: String, keys: InstanceActorKeyRepository): InstanceActor {
    val privateKeyPem = keys.findOrCreatePrivateKeyPem { RsaKeys.encodeToPem(RsaKeys.generateKeyPair().private) }
    return InstanceActor(
        urls = InstanceActorUrls(domain),
        privateKey = ActorPrivateKey(
            privateKey = RsaKeys.decodePrivateKeyPem(privateKeyPem),
            // DB から読んだ鍵に当たる Origin が無い。Origin は起動ログにしか使われない
            origin = ActorPrivateKey.Origin.Environment,
        ),
    )
}
