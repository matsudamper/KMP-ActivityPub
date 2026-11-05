package net.matsudamper.kmp.activitypub.android.theme

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import com.google.protobuf.InvalidProtocolBufferException
import net.matsudamper.kmp.activitypub.frontend.logic.theme.ThemeMode
import net.matsudamper.kmp.activitypub.frontend.logic.theme.ThemeSettingStore
import org.koin.core.annotation.Singleton

@Singleton
internal class AndroidThemeSettingStore(
    context: Context,
) : ThemeSettingStore {
    private val dataStore: DataStore<ThemeSetting> = context.themeSettingDataStore

    override val themeModeFlow: Flow<ThemeMode> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(ThemeSetting.getDefaultInstance()) else throw e
        }
        .map { setting -> setting.themeMode.toThemeMode() }

    override suspend fun save(themeMode: ThemeMode): Boolean =
        try {
            dataStore.updateData { setting ->
                setting.toBuilder().setThemeMode(themeMode.toProto()).build()
            }
            true
        } catch (_: IOException) {
            false
        }
}

private fun ThemeModeProto.toThemeMode(): ThemeMode =
    when (this) {
        ThemeModeProto.THEME_MODE_PROTO_LIGHT -> ThemeMode.Light
        ThemeModeProto.THEME_MODE_PROTO_DARK -> ThemeMode.Dark
        ThemeModeProto.THEME_MODE_PROTO_SYSTEM, ThemeModeProto.UNRECOGNIZED -> ThemeMode.System
    }

private fun ThemeMode.toProto(): ThemeModeProto =
    when (this) {
        ThemeMode.System -> ThemeModeProto.THEME_MODE_PROTO_SYSTEM
        ThemeMode.Light -> ThemeModeProto.THEME_MODE_PROTO_LIGHT
        ThemeMode.Dark -> ThemeModeProto.THEME_MODE_PROTO_DARK
    }

private val Context.themeSettingDataStore: DataStore<ThemeSetting> by dataStore(
    fileName = "theme_setting.pb",
    serializer = ThemeSettingSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { ThemeSetting.getDefaultInstance() },
)

private object ThemeSettingSerializer : Serializer<ThemeSetting> {
    override val defaultValue: ThemeSetting = ThemeSetting.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): ThemeSetting {
        return try {
            ThemeSetting.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("ThemeSetting を読めない", e)
        }
    }

    override suspend fun writeTo(t: ThemeSetting, output: OutputStream) {
        t.writeTo(output)
    }
}
