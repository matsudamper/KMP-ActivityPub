package net.matsudamper.kmp.activitypub.federation

import net.matsudamper.kmp.activitypub.logic.AccountService
import net.matsudamper.kotpub.actor.LocalActor
import net.matsudamper.kotpub.httpsignature.SignedFetchActor

/**
 * 他のサーバーへの GET を、最初に登録したアカウントで署名する。
 *
 * サーバー自身を表すアクターを持っていないので、代わりにアカウントの鍵を使う。
 * 署名しないと、署名の無い GET を拒むサーバーの投稿やアクターを取得できない
 */
internal class OldestAccountFetchSigner(
    private val accountService: AccountService,
    private val localActors: LocalActors,
) : SignedFetchActor {
    override suspend fun find(): LocalActor? {
        val username = accountService.oldestUsername() ?: return null
        return localActors.find(username)
    }
}
