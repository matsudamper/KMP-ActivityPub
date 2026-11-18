package net.matsudamper.kmp.activitypub.frontend.screen.post

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostNoteImage
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostNotePoll
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostNoteResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserUploadNoteImageResult
import net.matsudamper.kmp.activitypub.frontend.ui.WebpImage
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

    /**
     * 画像を 1 枚ずつ置いてから投稿する。送り直しでは画像も置き直す。
     * 前に置いた画像は投稿に付いているか、付かずにサーバーの期限で消える
     *
     * @param replyToPostId 返信するタイムラインの投稿の id。返信でなければ null
     * @param quotePostId 引用するタイムラインの投稿の id。引用でなければ null
     * @param contentWarning 閲覧注意の注意書き。閲覧注意にしなければ null
     * @param mentionAcct 宛てるユーザーの `username@host`。返信先の投稿者のほかに宛てなければ null
     * @param sensitive 閲覧注意の投稿にするか
     * @param poll 付ける投票。投票でなければ null
     */
    suspend fun submit(
        body: String,
        requestId: String,
        contentWarning: String?,
        images: List<WebpImage>,
        replyToPostId: String?,
        quotePostId: String?,
        mentionAcct: String?,
        sensitive: Boolean,
        poll: UserPostNotePoll?,
    ): PostNoteSubmitResult =
        scope.async {
            val imageIds =
                images.map { image ->
                    when (val uploaded = api.uploadNoteImage(UserPostNoteImage(content = image.openContent(), description = null))) {
                        is UserUploadNoteImageResult.Success -> uploaded.imageId
                        else -> return@async PostNoteSubmitResult.UploadFailed(uploaded)
                    }
                }
            PostNoteSubmitResult.Submitted(
                api.postNote(
                    body = body,
                    requestId = requestId,
                    contentWarning = contentWarning,
                    imageIds = imageIds,
                    replyToPostId = replyToPostId,
                    quotePostId = quotePostId,
                    mentionAcct = mentionAcct,
                    sensitive = sensitive,
                    poll = poll,
                ),
            )
        }.await()
}

internal sealed interface PostNoteSubmitResult {
    data class UploadFailed(
        val result: UserUploadNoteImageResult,
    ) : PostNoteSubmitResult

    data class Submitted(
        val result: UserPostNoteResult,
    ) : PostNoteSubmitResult
}
