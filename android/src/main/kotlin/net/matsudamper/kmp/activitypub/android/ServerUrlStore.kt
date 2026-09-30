package net.matsudamper.kmp.activitypub.android

import android.content.Context
import androidx.core.content.edit

/**
 * 接続先のサーバー。ブラウザと違って画面の配信元が無いので、使う人が決める
 */
internal class ServerUrlStore(
    context: Context,
) {
    private val preferences = context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun load(): String? = preferences.getString(KEY_SERVER_URL, null)

    fun save(serverUrl: String) {
        preferences.edit { putString(KEY_SERVER_URL, serverUrl) }
    }

    fun clear() {
        preferences.edit { remove(KEY_SERVER_URL) }
    }

    private companion object {
        const val PREFERENCES_NAME = "server"
        const val KEY_SERVER_URL = "server_url"
    }
}
