package net.matsudamper.kmp.activitypub.frontend.screen.post

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostNoteResult
import org.koin.core.annotation.Singleton

/**
 * 端末やブラウザの戻る操作は止められず、閉じると ViewModel ごと送信が打ち切られる。
 * サーバーでは投稿が確定しているのに利用者には分からず、送り直して重複するので、送信は画面より長く生きる場所で最後まで行う
 */
@Singleton
internal class PostNoteSubmitter(
    private val api: UserApi,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    suspend fun submit(body: String): UserPostNoteResult = scope.async { api.postNote(body = body) }.await()
}
