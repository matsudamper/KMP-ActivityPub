package net.matsudamper.kmp.activitypub.frontend.ui.layout

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout

/**
 * 候補を先頭から順に試し、横幅に収まった最初の 1 つだけを出す。どれも収まらなければ最後の候補を出す。
 *
 * 収まるかは各候補の maxIntrinsicWidth で見る。一度 measure した子は同じ測定の中で測り直せないため、
 * 幅を無制限にして measure して比べると、選んだ候補を本来の幅で並べ直せない。
 * また weight を使う Row は無制限の幅では weight の子を 0 にしてしまい、収まるように見えてしまう。
 * そのため候補に SubcomposeLayout（BoxWithConstraints など）は使えない。
 *
 * @param candidates 広い幅向けのものから順に並べる。空にはできない
 */
@Composable
fun FirstFitWidthLayout(
    candidates: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
) {
    require(candidates.isNotEmpty())
    Layout(
        contents = candidates,
        modifier = modifier,
    ) { candidateMeasurables, constraints ->
        val chosenMeasurables = candidateMeasurables.firstOrNull { measurables ->
            val requiredWidth = measurables.maxOfOrNull { it.maxIntrinsicWidth(constraints.maxHeight) } ?: 0
            requiredWidth <= constraints.maxWidth
        } ?: candidateMeasurables.last()
        val placeables = chosenMeasurables.map { it.measure(constraints) }
        val width = (placeables.maxOfOrNull { it.width } ?: 0).coerceIn(constraints.minWidth, constraints.maxWidth)
        val height = (placeables.maxOfOrNull { it.height } ?: 0).coerceIn(constraints.minHeight, constraints.maxHeight)
        layout(width = width, height = height) {
            placeables.forEach { it.place(x = 0, y = 0) }
        }
    }
}
