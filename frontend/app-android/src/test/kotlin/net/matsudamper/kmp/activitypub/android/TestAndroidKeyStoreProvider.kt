package net.matsudamper.kmp.activitypub.android

import java.io.InputStream
import java.io.OutputStream
import java.security.Key
import java.security.KeyStore
import java.security.KeyStoreSpi
import java.security.Provider
import java.security.cert.Certificate
import java.util.Date
import java.util.Enumeration
import javax.crypto.KeyGenerator

internal class TestAndroidKeyStoreProvider : Provider("AndroidKeyStore", 1.0, "Robolectric 用の鍵保存先") {
    init {
        put("KeyStore.AndroidKeyStore", Store::class.java.name)
    }

    // Robolectric にハードウェア Keystore がないため、鍵の保管だけを JCEKS に置き換える。
    class Store : KeyStoreSpi() {
        companion object {
            private val delegate = KeyStore.getInstance("JCEKS").apply {
                load(null, null)
                val generator = KeyGenerator.getInstance("AES")
                generator.init(256)
                setKeyEntry("user-session-v1", generator.generateKey(), charArrayOf(), null)
            }
        }

        override fun engineGetKey(alias: String, password: CharArray?): Key = delegate.getKey(alias, charArrayOf())
        override fun engineGetCertificateChain(alias: String): Array<Certificate>? = delegate.getCertificateChain(alias)
        override fun engineGetCertificate(alias: String): Certificate? = delegate.getCertificate(alias)
        override fun engineGetCreationDate(alias: String): Date = delegate.getCreationDate(alias)
        override fun engineSetKeyEntry(alias: String, key: Key, password: CharArray?, chain: Array<Certificate>?) =
            delegate.setKeyEntry(alias, key, charArrayOf(), chain)
        override fun engineSetKeyEntry(alias: String, key: ByteArray, chain: Array<Certificate>?) = delegate.setKeyEntry(alias, key, chain)
        override fun engineSetCertificateEntry(alias: String, cert: Certificate) = delegate.setCertificateEntry(alias, cert)
        override fun engineDeleteEntry(alias: String) = delegate.deleteEntry(alias)
        override fun engineAliases(): Enumeration<String> = delegate.aliases()
        override fun engineContainsAlias(alias: String): Boolean = delegate.containsAlias(alias)
        override fun engineSize(): Int = delegate.size()
        override fun engineIsKeyEntry(alias: String): Boolean = delegate.isKeyEntry(alias)
        override fun engineIsCertificateEntry(alias: String): Boolean = delegate.isCertificateEntry(alias)
        override fun engineGetCertificateAlias(cert: Certificate): String? = delegate.getCertificateAlias(cert)
        override fun engineStore(stream: OutputStream?, password: CharArray?) = Unit
        override fun engineLoad(stream: InputStream?, password: CharArray?) = Unit
    }
}
