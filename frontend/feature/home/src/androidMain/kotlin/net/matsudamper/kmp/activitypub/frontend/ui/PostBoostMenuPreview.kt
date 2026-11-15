package net.matsudamper.kmp.activitypub.frontend.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

internal fun previewBoostButton(boosted: Boolean): PostItemUiState.BoostButton {
    return PostItemUiState.BoostButton(
        iconHighlighted = boosted,
        menuItems = listOf(
            PostItemUiState.MenuItem(label = if (boosted) "ブーストを取り消す" else "ブースト", listener = AndroidPreviewBoostMenuItemListener),
            PostItemUiState.MenuItem(label = "引用", listener = AndroidPreviewBoostMenuItemListener),
        ),
    )
}

@Preview
@Composable
private fun PostBoostMenuPreview() {
    MaterialTheme {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            listOf(false, true).forEach { boosted ->
                Surface(shape = MaterialTheme.shapes.extraSmall, tonalElevation = 3.dp, shadowElevation = 3.dp) {
                    Column(modifier = Modifier.padding(vertical = 8.dp).width(IntrinsicSize.Max)) {
                        PostMenuItems(menuItems = previewBoostButton(boosted = boosted).menuItems, onItemClick = {})
                    }
                }
            }
        }
    }
}

private object AndroidPreviewBoostMenuItemListener : PostItemUiState.MenuItemListener {
    override fun onClick() = Unit
}
