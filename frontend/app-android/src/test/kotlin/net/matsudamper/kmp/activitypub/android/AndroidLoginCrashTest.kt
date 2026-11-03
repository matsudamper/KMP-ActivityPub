package net.matsudamper.kmp.activitypub.android

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.KeyStore
import java.security.Security
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking
import net.matsudamper.kmp.activitypub.android.server.ServerSettingStore
import net.matsudamper.kmp.activitypub.android.session.EncryptedSessionStore
import net.matsudamper.kmp.activitypub.android.session.SessionCookie
import net.matsudamper.kmp.activitypub.android.session.SessionState
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.koin.core.context.stopKoin
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AndroidLoginCrashTest {
    @get:Rule
    val compose = createEmptyComposeRule()
    private lateinit var server: LoginTestServer
    private var scenario: ActivityScenario<MainActivity>? = null
    private val previousTrustStore = System.getProperty("javax.net.ssl.trustStore")
    private val previousTrustStorePassword = System.getProperty("javax.net.ssl.trustStorePassword")

    @Before
    fun prepareServer() {
        Security.addProvider(TestAndroidKeyStoreProvider())
        server = LoginTestServer()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val trustStoreFile = File(context.cacheDir, "login-test-trust.p12")
        val trustStore = KeyStore.getInstance("PKCS12").apply {
            load(null, null)
            setCertificateEntry("localhost", server.certificate.certificate)
        }
        trustStoreFile.outputStream().use { trustStore.store(it, "password".toCharArray()) }
        System.setProperty("javax.net.ssl.trustStore", trustStoreFile.absolutePath)
        System.setProperty("javax.net.ssl.trustStorePassword", "password")
        runBlocking { ServerSettingStore(context).save(server.origin) }
    }

    @After
    fun closeServer() {
        scenario?.close()
        stopKoin()
        server.close()
        Security.removeProvider("AndroidKeyStore")
        restoreProperty("javax.net.ssl.trustStore", previousTrustStore)
        restoreProperty("javax.net.ssl.trustStorePassword", previousTrustStorePassword)
    }

    @Test
    fun loginFromLoggedOutStateRendersHome() {
        saveSession(null)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitForText("ユーザー名")
        compose.onNodeWithText("ユーザー名").performTextInput("alice")
        compose.onNodeWithText("パスワード").performTextInput("password")
        compose.onNode(hasText("ログイン") and hasClickAction()).performClick()
        assertHomeRendered()
        assertEquals(1, server.logins.get())
        val context = ApplicationProvider.getApplicationContext<Context>()
        val saved = runBlocking { EncryptedSessionStore(context).read() }
        assertNotNull(saved?.session?.forRequest("${server.origin}/graphql".toHttpUrl(), System.currentTimeMillis()))
        scenario?.recreate()
        assertHomeRendered()
    }

    @Test
    fun savedSessionRendersHomeWithoutLoggingIn() {
        saveSession(SessionCookie.fromResponse("${server.origin}/graphql".toHttpUrl(), "user_session=test-session; Path=/; Max-Age=3600; Secure; HttpOnly"))
        scenario = ActivityScenario.launch(MainActivity::class.java)
        assertHomeRendered()
        assertEquals(0, server.logins.get())
        scenario?.recreate()
        assertHomeRendered()
    }

    private fun saveSession(session: SessionCookie?) {
        val context = ApplicationProvider.getApplicationContext<Context>()
        runBlocking { EncryptedSessionStore(context).write(SessionState(session = session, backupPending = false)) }
    }

    private fun assertHomeRendered() {
        waitForText("ログイン後のテスト投稿 https://example.com/")
        compose.waitUntil(timeoutMillis = 15_000) { server.subscriptions.get() > 0 }
    }

    private fun waitForText(text: String) {
        compose.waitUntil(timeoutMillis = 15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText(text).assertIsDisplayed()
    }

    private fun restoreProperty(name: String, value: String?) {
        if (value == null) System.clearProperty(name) else System.setProperty(name, value)
    }
}
