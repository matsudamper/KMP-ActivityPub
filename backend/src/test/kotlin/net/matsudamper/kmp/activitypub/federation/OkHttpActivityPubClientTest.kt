package net.matsudamper.kmp.activitypub.federation

import kotlin.test.Test
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import net.matsudamper.kotpub.http.NonPublicAddressException

class OkHttpActivityPubClientTest {
    @Test
    fun IPアドレスで書かれた内側の宛先には取りに行かない() = runTest {
        OkHttpActivityPubClient().use { client ->
            val failure = runCatching {
                client.get(url = "https://127.0.0.1/.well-known/webfinger", headers = mapOf(), maxBodyBytes = 1024) { _, _ -> true }
            }.exceptionOrNull()

            assertIs<NonPublicAddressException>(failure)
        }
    }

    @Test
    fun IPアドレスで書かれた内側の宛先には送らない() = runTest {
        OkHttpActivityPubClient().use { client ->
            val failure = runCatching {
                client.post(url = "https://10.0.0.1/inbox", headers = mapOf(), body = byteArrayOf())
            }.exceptionOrNull()

            assertIs<NonPublicAddressException>(failure)
        }
    }
}
