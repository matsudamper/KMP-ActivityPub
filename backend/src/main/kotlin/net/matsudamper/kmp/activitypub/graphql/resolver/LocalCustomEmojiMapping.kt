package net.matsudamper.kmp.activitypub.graphql.resolver

import net.matsudamper.kmp.activitypub.graphql.model.QlLocalCustomEmoji
import net.matsudamper.kmp.activitypub.logic.CustomEmojiUrls
import net.matsudamper.kmp.activitypub.repository.LocalCustomEmoji
import net.matsudamper.kmp.activitypub.shared.AccountId

internal fun LocalCustomEmoji.toQlLocalCustomEmoji(viewer: AccountId, urls: CustomEmojiUrls): QlLocalCustomEmoji = QlLocalCustomEmoji(
    id = id,
    shortcode = shortcode,
    imageUrl = urls.imageUrl(publicId),
    deletable = uploadedBy == viewer,
)
