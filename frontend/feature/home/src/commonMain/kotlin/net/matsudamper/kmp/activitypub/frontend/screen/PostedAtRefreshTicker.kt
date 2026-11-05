package net.matsudamper.kmp.activitypub.frontend.screen

import kotlin.time.Duration.Companion.minutes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import org.koin.core.annotation.Singleton

/**
 * 投稿日時の相対表記は時間が経つだけで古くなるので、表示を作り直す合図を流す
 */
internal interface PostedAtRefreshTicker {
    val ticks: Flow<Unit>
}

@Singleton
internal class MinutePostedAtRefreshTicker : PostedAtRefreshTicker {
    override val ticks: Flow<Unit> = flow {
        while (true) {
            delay(1.minutes)
            emit(Unit)
        }
    }
}
