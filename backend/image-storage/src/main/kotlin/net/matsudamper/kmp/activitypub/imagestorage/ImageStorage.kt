package net.matsudamper.kmp.activitypub.imagestorage

import java.io.InputStream
import net.matsudamper.kmp.activitypub.shared.AccountId

interface ImageStorage {
    fun put(request: PutRequest): PutResult

    fun delete(request: DeleteRequest): DeleteResult

    /**
     * 認証なしで画像を取れる、1 時間で切れる URL
     */
    fun buildDisplayUrl(request: DisplayUrlRequest): String

    /**
     * @param ownerAccountId 置き場所の権限をアカウント単位に絞るために使う
     * @param maxBytes これを超えたら途中まで送った分も消して [PutResult.PayloadTooLarge] を返す
     */
    data class PutRequest(
        val ownerAccountId: AccountId,
        val path: ImagePath,
        val contentType: String,
        val maxBytes: Long,
        val inputStream: InputStream,
    )

    sealed interface PutResult {
        data class Success(val path: ImagePath) : PutResult

        data object PayloadTooLarge : PutResult

        data object Empty : PutResult

        data class Failure(val cause: Throwable) : PutResult
    }

    data class DeleteRequest(
        val ownerAccountId: AccountId,
        val path: ImagePath,
    )

    sealed interface DeleteResult {
        data object Success : DeleteResult

        data class Failure(val cause: Throwable) : DeleteResult
    }

    data class DisplayUrlRequest(
        val ownerAccountId: AccountId,
        val path: ImagePath,
    )
}

/**
 * ストレージの中での置き場所。`..` や先頭の `/` を許すと他の画像を上書きできる
 */
@JvmInline
value class ImagePath(val value: String) {
    init {
        require(value.isNotEmpty()) { "空の path は使えない" }
        require(!value.startsWith("/")) { "path は / で始めないこと: $value" }
        require(value.split('/').none { it.isEmpty() || it == "." || it == ".." }) { "path に空の要素や . / .. を含めないこと: $value" }
    }
}
