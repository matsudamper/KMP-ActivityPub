package net.matsudamper.kmp.activitypub.logic

import net.matsudamper.kotpub.emoji.EmojiObject

/**
 * このサーバーの投稿の本文に入っているカスタム絵文字を、他のサーバーへ配る形で引く
 */
fun interface NoteEmojiObjects {
    fun find(contentHtml: String): List<EmojiObject>
}

class LocalNoteEmojiObjects(
    private val customEmojis: CustomEmojiService,
    private val customEmojiUrls: CustomEmojiUrls,
) : NoteEmojiObjects {
    override fun find(contentHtml: String): List<EmojiObject> = customEmojis.findInNoteContent(contentHtml).map(customEmojiUrls::emojiObjectOf)
}
