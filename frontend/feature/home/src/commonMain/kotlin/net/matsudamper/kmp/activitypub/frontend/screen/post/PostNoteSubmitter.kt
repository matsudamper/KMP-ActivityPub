package net.matsudamper.kmp.activitypub.frontend.screen.post

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserApi
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostNoteImage
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserPostNoteResult
import net.matsudamper.kmp.activitypub.frontend.logic.user.UserUploadNoteImageResult
import net.matsudamper.kmp.activitypub.frontend.ui.PostAttachment
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
     * 画像や動画を 1 つずつ置いてから投稿する。送り直しでは置き直す。
     * 前に置いたものは投稿に付いているか、付かずにサーバーの期限で消える
     *
     * @param replyToPostId 返信するタイムラインの投稿の id。返信でなければ null
     * @param quotePostId 引用するタイムラインの投稿の id。引用でなければ null
     * @param contentWarning 閲覧注意の注意書き。閲覧注意にしなければ null
     * @param mentionAcct 宛てるユーザーの `username@host`。返信先の投稿者のほかに宛てなければ null
     */
    suspend fun submit(
        body: String,
        requestId: String,
        contentWarning: String?,
        images: List<PostAttachment>,
        replyToPostId: String?,
        quotePostId: String?,
        mentionAcct: String?,
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
