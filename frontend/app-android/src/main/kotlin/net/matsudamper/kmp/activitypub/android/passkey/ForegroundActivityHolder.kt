package net.matsudamper.kmp.activitypub.android.passkey

import android.app.Activity
import java.lang.ref.WeakReference
import org.koin.core.annotation.Singleton

/**
 * Credential Manager は画面を出すので Activity が要る。ViewModel から呼ぶため、表示中の Activity をここに置く
 */
@Singleton
internal class ForegroundActivityHolder {
    private var reference: WeakReference<Activity>? = null

    val current: Activity? get() = reference?.get()

    fun attach(activity: Activity) {
        reference = WeakReference(activity)
    }

    fun detach(activity: Activity) {
        if (reference?.get() === activity) reference = null
    }
}
