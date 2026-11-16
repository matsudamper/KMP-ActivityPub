package net.matsudamper.kmp.activitypub.android.language

import android.content.Context
import androidx.datastore.core.CorruptionException
import androidx.datastore.core.DataStore
import androidx.datastore.core.Serializer
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.dataStore
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.Locale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import com.google.protobuf.InvalidProtocolBufferException
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageSetting
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageSettingStore
import net.matsudamper.kmp.activitypub.frontend.logic.language.LanguageTag
import org.koin.core.annotation.Singleton

@Singleton
internal class AndroidLanguageSettingStore(
    context: Context,
) : LanguageSettingStore {
    private val dataStore: DataStore<LanguageSettingProto> = context.languageSettingDataStore

    override val languageSettingFlow: Flow<LanguageSetting> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(LanguageSettingProto.getDefaultInstance()) else throw e
        }
        .map { setting ->
            LanguageSetting(
                languageTag = LanguageTag.parse(setting.languageTag.ifEmpty { Locale.getDefault().toLanguageSubtags() }),
                translateButtonVisible = setting.translateButtonVisible,
            )
        }

    override suspend fun save(languageTag: LanguageTag, translateButtonVisible: Boolean): Boolean =
        try {
            dataStore.updateData { setting ->
                setting.toBuilder()
                    .setLanguageTag(languageTag.value)
                    .setTranslateButtonVisible(translateButtonVisible)
                    .build()
            }
            true
        } catch (_: IOException) {
            false
        }
}

/**
 * toLanguageTag は `-u-ca-japanese` のような拡張も付けるので、言語・文字体系・地域だけを取り出す
 */
private fun Locale.toLanguageSubtags(): String =
    listOf(language, script, country).filter { it.isNotEmpty() }.joinToString("-")

private val Context.languageSettingDataStore: DataStore<LanguageSettingProto> by dataStore(
    fileName = "language_setting.pb",
    serializer = LanguageSettingSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { LanguageSettingProto.getDefaultInstance() },
)

private object LanguageSettingSerializer : Serializer<LanguageSettingProto> {
    override val defaultValue: LanguageSettingProto = LanguageSettingProto.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): LanguageSettingProto {
        return try {
            LanguageSettingProto.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("LanguageSettingProto を読めない", e)
        }
    }

    override suspend fun writeTo(t: LanguageSettingProto, output: OutputStream) {
        t.writeTo(output)
    }
}
