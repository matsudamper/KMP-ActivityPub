package net.matsudamper.kmp.activitypub.frontend.screen.admin

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import net.matsudamper.kmp.activitypub.frontend.ui.SectionCard
import net.matsudamper.kmp.activitypub.frontend.ui.TextLink

@Composable
internal fun RequireLoginCard(
    onClickAdmin: () -> Unit,
) {
    SectionCard(title = "ログインが要る") {
        Text("管理画面のトップでログインしてから開く。")
        TextLink(
            text = "管理画面のトップへ",
            onClick = onClickAdmin,
        )
    }
}
