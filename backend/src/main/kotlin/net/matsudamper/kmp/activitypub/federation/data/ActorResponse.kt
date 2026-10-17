package net.matsudamper.kmp.activitypub.federation.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ActorResponse(
    @SerialName("@context")
    val context: List<String>,
    @SerialName("id")
    val id: String,
    @SerialName("type")
    val type: String,
    @SerialName("preferredUsername")
    val preferredUsername: String,
    @SerialName("name")
    val name: String,
    @SerialName("url")
    val url: String,
    @SerialName("inbox")
    val inbox: String,
    /**
     * 鍵を作る前に登録したアカウントには無い。Mastodon は無くても Actor として取り込む。
     * 既定値と同じ値は出力されないので、null のときは項目ごと出ない
     */
    @SerialName("publicKey")
    val publicKey: PublicKey? = null,
) {
    @Serializable
    data class PublicKey(
        @SerialName("id")
        val id: String,
        @SerialName("owner")
        val owner: String,
        @SerialName("publicKeyPem")
        val publicKeyPem: String,
    )
}
