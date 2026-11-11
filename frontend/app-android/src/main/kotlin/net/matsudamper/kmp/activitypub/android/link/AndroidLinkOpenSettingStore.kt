package net.matsudamper.kmp.activitypub.android.link

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
import net.matsudamper.kmp.activitypub.frontend.logic.link.LinkOpenMode
import net.matsudamper.kmp.activitypub.frontend.logic.link.LinkOpenSettingStore
import org.koin.core.annotation.Singleton

@Singleton
internal class AndroidLinkOpenSettingStore(
    context: Context,
) : LinkOpenSettingStore {
    private val dataStore: DataStore<LinkOpenSetting> = context.linkOpenSettingDataStore

    override val selectableModes: List<LinkOpenMode> = LinkOpenMode.entries

    override val linkOpenModeFlow: Flow<LinkOpenMode> = dataStore.data
        .catch { e ->
            if (e is IOException) emit(LinkOpenSetting.getDefaultInstance()) else throw e
        }
        .map { setting -> setting.linkOpenMode.toLinkOpenMode() }

    override suspend fun save(linkOpenMode: LinkOpenMode): Boolean =
        try {
            dataStore.updateData { setting ->
                setting.toBuilder().setLinkOpenMode(linkOpenMode.toProto()).build()
            }
            true
        } catch (_: IOException) {
            false
        }
}

private val Context.linkOpenSettingDataStore: DataStore<LinkOpenSetting> by dataStore(
    fileName = "link_open_setting.pb",
    serializer = LinkOpenSettingSerializer,
    corruptionHandler = ReplaceFileCorruptionHandler { LinkOpenSetting.getDefaultInstance() },
)

private fun LinkOpenModeProto.toLinkOpenMode(): LinkOpenMode =
    when (this) {
        LinkOpenModeProto.LINK_OPEN_MODE_PROTO_EXTERNAL_BROWSER -> LinkOpenMode.ExternalBrowser
        LinkOpenModeProto.LINK_OPEN_MODE_PROTO_CUSTOM_TAB, LinkOpenModeProto.UNRECOGNIZED -> LinkOpenMode.CustomTab
    }

private fun LinkOpenMode.toProto(): LinkOpenModeProto =
    when (this) {
        LinkOpenMode.ExternalBrowser -> LinkOpenModeProto.LINK_OPEN_MODE_PROTO_EXTERNAL_BROWSER
        LinkOpenMode.CustomTab -> LinkOpenModeProto.LINK_OPEN_MODE_PROTO_CUSTOM_TAB
    }

private object LinkOpenSettingSerializer : Serializer<LinkOpenSetting> {
    override val defaultValue: LinkOpenSetting = LinkOpenSetting.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): LinkOpenSetting {
        return try {
            LinkOpenSetting.parseFrom(input)
        } catch (e: InvalidProtocolBufferException) {
            throw CorruptionException("LinkOpenSetting を読めない", e)
        }
    }

    override suspend fun writeTo(t: LinkOpenSetting, output: OutputStream) {
        t.writeTo(output)
    }
}
