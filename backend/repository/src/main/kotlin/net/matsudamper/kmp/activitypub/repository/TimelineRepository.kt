package net.matsudamper.kmp.activitypub.repository

import java.time.Instant
import net.matsudamper.kmp.activitypub.shared.AccountId
import net.matsudamper.kmp.activitypub.shared.FollowingGroupId
import net.matsudamper.kmp.activitypub.shared.NoteImagePublicId
import net.matsudamper.kmp.activitypub.shared.PublicNoteId

interface TimelineRepository {
    fun receptionInterest(actorUri: String, localRecipients: Set<String>): RemoteNoteReceptionInterest
    fun shouldReceive(actorUri: String, localRecipients: Set<String>): Boolean = receptionInterest(actorUri, localRecipients).shouldReceive
    fun receive(note: RemoteNoteInput): TimelinePosition?

    /**
     * ブーストした相手をフォローしているアカウントが居なければ記録しない。
     * ブーストされた投稿が未保存なら一緒に保存する
     *
     * @return タイムラインに載せる位置。記録しなかったか、記録済みなら null
     */
    fun receiveBoost(boost: RemoteBoostInput): TimelinePosition?
    fun hasRemoteNote(objectUri: String): Boolean

    /**
     * 投稿者が消した投稿に印を付ける。タイムラインに削除済みと出すので行は残す
     *
     * @return 削除済みになったタイムラインの member。投稿そのものと、それをブーストした行。保存していないか、既に消されていれば空
     */
    fun deleteRemote(actorUri: String, objectUri: String, deletedAt: Instant): List<String>

    /**
     * [member] を載せるアカウントを、アカウントの id 順に [after] より後ろから返す
     */
    fun recipients(member: String, activeSince: Instant, after: AccountId?, limit: Int): List<TimelineRecipient>

    /**
     * 投稿者が編集した投稿を届いた内容に書き換え、書き換える前の版を残す。公開範囲と位置は変えない。
     * 配送は順番どおりとは限らないので、編集した時刻が保存済みの編集より新しくなければ書き換えない。
     * [RemoteNoteInput.editedAt] が無ければ [RemoteNoteInput.receivedAt] に編集したものとして扱う。
     * 本文も注意書きも画像も変わっていなければ版は残さない。そのときも [RemoteNoteInput.editedAt] があれば編集した時刻は進め、それより古い編集で巻き戻さない
     *
     * @return 中身か編集した時刻が変わったタイムラインの member。投稿そのものと、それをブーストした行。
     *   保存していないか、消されているか、[RemoteNoteInput.actorUri] の投稿でないか、何も変えなかったら空
     */
    fun reviseRemote(note: RemoteNoteInput): List<String>

    /**
     * [viewablePost] で [viewer] が開ける投稿の版を、新しい順に返す。最初が今の版。編集されていなければ今の版だけ。
     * ブーストならブーストした元の投稿の版
     *
     * @param olderThan この版より古い版を返す。null なら今の版から
     * @return 開けない投稿か、[member] が無ければ null
     */
    fun revisions(viewer: AccountId, member: String, olderThan: PostRevisionPosition?, limit: Int): List<PostRevision>?
    fun touch(accountId: AccountId, now: Instant, updateIntervalSeconds: Long)

    /**
     * @param groupId 読む購読先。null ならメイン
     */
    fun rebuild(accountId: AccountId, groupId: FollowingGroupId?, limit: Int): List<TimelinePosition>

    /**
     * @param groupId 読む購読先。null ならメイン
     */
    fun page(accountId: AccountId, groupId: FollowingGroupId?, after: TimelinePosition?, limit: Int): List<TimelinePosition>
    fun hydrate(accountId: AccountId, members: List<String>): List<TimelinePost>

    /**
     * 個別投稿画面で [viewer] が開ける投稿。タイムラインに載る投稿に加えて、プロフィールに並ぶ投稿も開ける。
     * このサーバーの投稿とこのサーバーのアカウントのブーストは誰でも、他のサーバーの投稿は [viewer] が読めるものと公開のもの、
     * 他のサーバーのブーストは公開している投稿のものなら開ける
     *
     * @return 開けない投稿か、[member] が無ければ null
     */
    fun viewablePost(viewer: AccountId, member: String): TimelinePost?

    /**
     * [viewablePost] で [viewer] が開ける投稿の本文だけを返す。ブーストならブーストした元の投稿の本文。開けなければ null
     */
    fun contentHtml(viewer: AccountId, member: String): String?

    /**
     * [accountId] が読める、[actorUri] の保存済みの投稿と [actorUri] のブーストを新しい順に返す
     *
     * @param before この位置より古いものを返す。null なら最新から
     */
    fun actorPosts(accountId: AccountId, actorUri: String, before: TimelinePosition?, limit: Int): List<TimelinePost>
    fun localPosition(publicId: String): TimelinePosition?

    /**
     * [localPosition] を複数の投稿でまとめて引く。無い投稿は返さない
     */
    fun localPositionsByPublicId(publicIds: Set<PublicNoteId>): Map<PublicNoteId, TimelinePosition>

    /**
     * [booster] が付けたブーストを新しい順に返す。ブーストした時点で読めていたので、[viewer] が今読めるかは問わない
     *
     * @param viewer いいねやブーストの有無を見るアカウント。ログインしていなければ null
     * @param before この位置より古いものを返す。null なら最新から
     */
    fun accountBoosts(booster: AccountId, viewer: AccountId?, before: TimelinePosition?, limit: Int): List<TimelinePost>

    /**
     * 保存済みの他のサーバーの投稿のうち、id が [objectUris] のもの。[viewer] が読めるものに加えて、公開と、公開タイムラインに載せない公開のものは、
     * フォローしていなくても返す
     */
    fun storedRemotePosts(viewer: AccountId, objectUris: Set<String>): List<TimelinePost>

    /**
     * 保存済みの投稿のうち、[objectUri] の投稿への返信を古い順に返す。このサーバーの投稿は誰でも、他のサーバーの投稿は [viewablePost] と同じ範囲で返す。
     * 消された投稿は出さない
     *
     * @param objectUri 返信先の投稿の id
     * @param hidesAuthor 他のサーバーの投稿者の URI を渡すと、その投稿を出さないなら true を返す。出さない投稿は [limit] に数えない
     */
    fun replies(viewer: AccountId, objectUri: String, limit: Int, hidesAuthor: (actorUri: String) -> Boolean): List<TimelinePost>

    /**
     * 他のサーバーの投稿を、いいねやブーストを付けたときに保存する。タイムラインには載せない。
     * 公開と、公開タイムラインに載せない公開の投稿だけを保存する。投稿の id が投稿者と別のホストにあるものは保存しない。投稿者のアクターは記録済みであること
     *
     * @param viewer いいねやブーストの有無を見るアカウント
     * @return 投稿の id ごとの、保存した投稿。保存できなかった投稿は含まない
     */
    fun storeFetchedNotes(viewer: AccountId, notes: List<RemoteNoteInput>): Map<String, TimelinePost>

    /**
     * [liker] がいいねした投稿のうち [viewer] が開けるものを、いいねした新しい順に返す。消された投稿は出さない
     *
     * @param before この位置より古いいいねを返す。null なら最新から
     */
    fun likedPosts(liker: AccountId, viewer: AccountId, before: LikedNotePosition?, limit: Int): List<LikedTimelinePost>

    /**
     * 保存済みの投稿のうち本文が [matchesContent] に合うものを、投稿した新しい順に返す。このサーバーの投稿は誰でも、
     * 他のサーバーの投稿は [viewer] が読めるものと公開のものを返す。公開タイムラインに載せない公開の投稿は、探されたくない投稿なので読めるものに限る。
     * 消された投稿は出さない
     *
     * @param before この位置より古い投稿を返す。null なら最新から
     * @param hidesAuthor 他のサーバーの投稿者の URI を渡すと、その投稿を出さないなら true を返す。出さない投稿は [limit] に数えない
     * @param matchesContent 本文の HTML を渡すと、出す投稿なら true を返す。合わない投稿は [limit] に数えない
     */
    fun searchPosts(
        viewer: AccountId,
        before: PublishedNotePosition?,
        limit: Int,
        hidesAuthor: (actorUri: String) -> Boolean,
        matchesContent: (contentHtml: String) -> Boolean,
    ): List<TimelinePost>
}

/**
 * 投稿した時刻で並べた投稿の続きを指す位置
 *
 * @param member 投稿の member。同じ時刻の投稿を並べる
 */
data class PublishedNotePosition(val publishedAt: Instant, val member: String)

data class LikedTimelinePost(val post: TimelinePost, val position: LikedNotePosition)

data class RemoteNoteReceptionInterest(val followingAccountCount: Long, val localRecipientCount: Long) {
    val shouldReceive: Boolean get() = followingAccountCount > 0 || localRecipientCount > 0
}

data class TimelinePosition(val score: Long, val member: String)

/**
 * @param groupId 投稿を載せる購読先。メインなら null
 */
data class TimelineRecipient(val accountId: AccountId, val groupId: FollowingGroupId?)

/**
 * @param actorIconUrl 他のサーバーの投稿者のプロフィール画像の URL。このサーバーの投稿や、画像の無い相手は null
 * @param actorUpdatedAt 他のサーバーの投稿者の情報を最後に受け取った時刻。このサーバーの投稿は null
 * @param favouriteCount 他のサーバーから届いたお気に入りと、このサーバーのアカウントのいいねを合わせた数。他のサーバーの投稿の数は受け取らないので null
 * @param stamps 他のサーバーから届いたスタンプ。他のサーバーの投稿は受け取らないので空
 * @param likedByMe 読んでいるアカウントがいいねしているか。ブーストで載った投稿は、ブーストされた元の投稿へのいいね
 * @param boostedByMe 読んでいるアカウントがブーストしているか。ブーストできない投稿（フォロワー限定など、公開でも公開タイムラインに載せないものでもない投稿）は null。
 *   ブーストで載った投稿は、ブーストされた元の投稿のブースト
 * @param reactionMember いいねやブーストを付け外しするときに指す member。ブーストで載った投稿は、ブーストされた元の投稿を指す。
 *   受け取ったブーストが取り消されても同じ投稿を指し続ける
 * @param noteRef 投稿を外から指すための元。URL はこのサーバーのドメインで決まるので、ここでは作らない
 * @param boostCount 他のサーバーから届いたブーストの数。他のサーバーの投稿の数は受け取らないので null
 * @param emojis 本文と注意書きに入るカスタム絵文字。このサーバーの投稿は空
 * @param actorEmojis 他のサーバーの投稿者の表示名に入るカスタム絵文字。このサーバーの投稿は空
 * @param boostedBy フォローしている相手か自分のブーストとして載った投稿なら、ブーストした相手。それ以外は null。
 *   [position] はブーストの位置で、本文や投稿者はブーストされた投稿のもの
 * @param deleted 投稿者が投稿を消したか。ほかの項目は消される前のまま
 * @param boostUndone ブーストした相手がブーストを取り消したか。ほかの項目は取り消される前のまま
 * @param inReplyTo 返信先の投稿の id。返信でなければ null。ブーストで載った投稿は、ブーストされた元の投稿の返信先
 * @param quoteUri 引用した投稿の id。引用でなければ null。ブーストで載った投稿は、ブーストされた元の投稿の引用先
 * @param editedAt 投稿者が最後に編集した時刻。編集を受け取っていなければ null。ブーストで載った投稿は、ブーストされた元の投稿の編集
 */
data class TimelinePost(
    val position: TimelinePosition,
    val contentHtml: String,
    val publishedAt: Instant,
    val actorUri: String?,
    val username: String,
    val displayName: String?,
    val summary: String?,
    val sensitive: Boolean,
    val actorIconUrl: String?,
    val actorUpdatedAt: Instant?,
    val images: List<TimelinePostImage>,
    val emojis: List<CustomEmojiRecord>,
    val actorEmojis: List<CustomEmojiRecord>,
    val favouriteCount: Long?,
    val stamps: List<StampCount>,
    val likedByMe: Boolean,
    val boostedByMe: Boolean?,
    val reactionMember: String,
    val noteRef: TimelineNoteRef,
    val boostCount: Long?,
    val boostedBy: TimelineBooster?,
    val deleted: Boolean,
    val boostUndone: Boolean,
    val inReplyTo: String?,
    val quoteUri: String?,
    val editedAt: Instant?,
) {
    /**
     * このサーバーの投稿の公開 id。他のサーバーの投稿は null
     */
    val localPublicId: PublicNoteId? get() = (noteRef as? TimelineNoteRef.Local)?.publicId
}

/**
 * 投稿の版の並びの中での位置
 */
sealed interface PostRevisionPosition {
    data object Current : PostRevisionPosition

    /**
     * @param revisionId 編集される前の版の id。大きいほど新しい
     */
    data class Past(val revisionId: Long) : PostRevisionPosition
}

/**
 * 投稿のある時点の中身
 *
 * @param revisedAt この版になった時刻。最初の版は投稿した時刻
 */
data class PostRevision(
    val position: PostRevisionPosition,
    val contentHtml: String,
    val summary: String?,
    val sensitive: Boolean,
    val images: List<TimelinePostImage>,
    val revisedAt: Instant,
)

sealed interface TimelineNoteRef {
    data class Local(val publicId: PublicNoteId) : TimelineNoteRef

    data class Remote(val objectUri: String) : TimelineNoteRef
}

/**
 * @param actorUri ブーストした他のサーバーのアクター。このサーバーのアカウントのブーストは null
 * @param username 相手の preferredUsername。無ければアクターの URI
 * @param iconUrl 他のサーバーのアクターのプロフィール画像の URL。このサーバーのアカウントは null
 * @param updatedAt 他のサーバーのアクターの情報を最後に受け取った時刻。このサーバーのアカウントは null
 */
data class TimelineBooster(
    val actorUri: String?,
    val username: String,
    val displayName: String?,
    val iconUrl: String?,
    val emojis: List<CustomEmojiRecord>,
    val updatedAt: Instant?,
)

sealed interface TimelinePostImage {
    /**
     * 画像の説明文。無ければ null
     */
    val description: String?

    /**
     * @param url 他のサーバーにある画像の URL
     */
    data class Remote(val url: String, override val description: String?) : TimelinePostImage

    /**
     * このサーバーの投稿の画像。URL はこのサーバーのドメインで決まるので、識別子だけ持つ
     */
    data class Local(val publicId: NoteImagePublicId, override val description: String?) : TimelinePostImage
}

data class RemoteNoteInput(
    val objectUri: String,
    val actorUri: String,
    val contentHtml: String,
    val visibility: String,
    val localRecipients: Set<String>,
    val inReplyTo: String?,
    val quoteUri: String?,
    val summary: String?,
    val sensitive: Boolean,
    val publishedAt: Instant,
    val receivedAt: Instant,
    val editedAt: Instant?,
    val images: List<TimelinePostImage.Remote>,
    val emojis: List<CustomEmojiRecord>,
)

/**
 * @param boostedAt `Announce` の日時。無ければ受け取った時刻
 * @param note ブーストされた投稿。投稿者のアクターは記録済みであること
 */
data class RemoteBoostInput(
    val activityUri: String,
    val boosterUri: String,
    val boostedAt: Instant,
    val receivedAt: Instant,
    val note: RemoteNoteInput,
)
