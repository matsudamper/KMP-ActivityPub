package net.matsudamper.kmp.activitypub.logic

/**
 * Pillow で書き出した画像。ファイル名の寸法と中身の寸法が一致している
 */
internal object TestProfileIcons {
    fun read(fileName: String): ByteArray =
        checkNotNull(TestProfileIcons::class.java.getResourceAsStream("/profile-icon/$fileName")) { "無いテスト画像: $fileName" }
            .use { it.readBytes() }
}
