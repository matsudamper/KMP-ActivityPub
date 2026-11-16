package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

/**
 * プロフィールの一番上に出すヘッダー画像。
 * 読み込みの前後で下の要素が動かないよう、比率で高さを決めて先に場所を取る
 */
@Composable
internal fun ProfileHeaderImage(headerUrl: String) {
    AsyncImage(
        model = headerUrl,
        contentDescription = "ヘッダー画像",
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(PROFILE_HEADER_ASPECT_RATIO)
            .background(MaterialTheme.colorScheme.surfaceVariant),
    )
}

private const val PROFILE_HEADER_ASPECT_RATIO = 3f
