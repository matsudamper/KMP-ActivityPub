package net.matsudamper.kmp.activitypub.android.passkey

import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.CreateCredentialException
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import androidx.credentials.exceptions.domerrors.InvalidStateError
import androidx.credentials.exceptions.publickeycredential.CreatePublicKeyCredentialDomException
import net.matsudamper.kmp.activitypub.frontend.logic.passkey.PasskeyAuthenticator
import org.koin.core.annotation.Singleton

@Singleton
internal class AndroidPasskeyAuthenticator(
    private val activityHolder: ForegroundActivityHolder,
) : PasskeyAuthenticator {
    /**
     * Credential Manager のパスキーは Android 9 から
     */
    override val available: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    override suspend fun create(request: PasskeyAuthenticator.CreationRequest): PasskeyAuthenticator.Result<PasskeyAuthenticator.Creation> {
        val activity = activityHolder.current ?: return PasskeyAuthenticator.Result.Failure(NO_ACTIVITY_MESSAGE)
        val response =
            try {
                CredentialManager.create(activity).createCredential(activity, CreatePublicKeyCredentialRequest(request.optionsJson))
            } catch (_: CreateCredentialCancellationException) {
                return PasskeyAuthenticator.Result.Cancelled
            } catch (e: CreatePublicKeyCredentialDomException) {
                // excludeCredentials に当たった。この端末には追加済み
                if (e.domError is InvalidStateError) return PasskeyAuthenticator.Result.Failure("この端末のパスキーは追加済み")
                return PasskeyAuthenticator.Result.Failure(e.message ?: e.type)
            } catch (e: CreateCredentialException) {
                return PasskeyAuthenticator.Result.Failure(e.message ?: e.type)
            }
        if (response !is CreatePublicKeyCredentialResponse) return PasskeyAuthenticator.Result.Failure(UNEXPECTED_RESPONSE_MESSAGE)

        return PasskeyAuthenticator.Result.Success(PasskeyAuthenticator.Creation(credentialJson = response.registrationResponseJson))
    }

    override suspend fun get(request: PasskeyAuthenticator.AssertionRequest): PasskeyAuthenticator.Result<PasskeyAuthenticator.Assertion> {
        val activity = activityHolder.current ?: return PasskeyAuthenticator.Result.Failure(NO_ACTIVITY_MESSAGE)
        val response =
            try {
                CredentialManager.create(activity).getCredential(
                    activity,
                    GetCredentialRequest(listOf(GetPublicKeyCredentialOption(request.optionsJson))),
                )
            } catch (_: GetCredentialCancellationException) {
                return PasskeyAuthenticator.Result.Cancelled
            } catch (_: NoCredentialException) {
                return PasskeyAuthenticator.Result.Failure("この端末に使えるパスキーが無い")
            } catch (e: GetCredentialException) {
                return PasskeyAuthenticator.Result.Failure(e.message ?: e.type)
            }
        val credential = response.credential as? PublicKeyCredential ?: return PasskeyAuthenticator.Result.Failure(UNEXPECTED_RESPONSE_MESSAGE)

        return PasskeyAuthenticator.Result.Success(PasskeyAuthenticator.Assertion(credentialJson = credential.authenticationResponseJson))
    }

    private companion object {
        const val NO_ACTIVITY_MESSAGE = "画面が開いていない"
        const val UNEXPECTED_RESPONSE_MESSAGE = "パスキーの応答を読めない"
    }
}
