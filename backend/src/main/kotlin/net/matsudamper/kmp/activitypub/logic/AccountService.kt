package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kmp.activitypub.crypto.PasswordHashType
import net.matsudamper.kmp.activitypub.repository.Account
import net.matsudamper.kmp.activitypub.repository.AccountRepository
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kotpub.actor.ActorKey
import net.matsudamper.kotpub.crypto.RsaKeys

class AccountService(
    private val accountRepository: AccountRepository,
) {
    /**
     * ユーザーがいなくても同じだけ計算し、応答時間で名前の有無を分からなくする。
     * 遅延生成すると最初の 1 回だけ遅くなるので最初から初期化しておく
     */
    private val dummyPasswordHash: String = checkNotNull(PasswordHashType.Current.encoder.encode("dummy-password"))

    fun account(id: AccountId): Account? = accountRepository.findById(id)

    fun account(username: String): Account? = accountRepository.findByUsername(username)

    /**
     * kotpub の ActorKey は読み込み元を持つが、DB から読む種類が無いので Environment にしている。起動ログ以外には使われない
     */
    fun actorKey(id: AccountId): ActorKey? {
        val privateKeyPem = accountRepository.findPrivateKeyPem(id) ?: return null
        return ActorKey(privateKey = RsaKeys.decodePrivateKeyPem(privateKeyPem), origin = ActorKey.Origin.Environment)
    }

    fun authenticate(
        username: String,
        password: String,
    ): AccountId? {
        val credential = accountRepository.findCredential(username.trim())
        if (credential == null) {
            PasswordHashType.Current.encoder.matches(password, dummyPasswordHash)
            return null
        }

        // パスワード違いと区別できるよう失敗にする
        val hashType =
            checkNotNull(PasswordHashType.fromDbValue(credential.passwordHashType)) {
                "対応していない password_hash_type: ${credential.passwordHashType}"
            }
        return credential.id.takeIf { hashType.encoder.matches(password, credential.passwordHash) }
    }
}
