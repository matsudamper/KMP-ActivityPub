package net.matsudamper.kmp.activitypub.repository

/**
 * サーバー自身のアクター（/actor）の鍵。他のサーバーは署名の検証のために公開鍵を覚えるので、
 * 起動のたびに作り直さず保存しておく
 */
interface InstanceActorKeyRepository {
    /**
     * 保存した秘密鍵の PEM を返す。まだ無ければ [newPrivateKeyPem] を保存して返す
     */
    fun findOrCreatePrivateKeyPem(newPrivateKeyPem: () -> String): String
}
