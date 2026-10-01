package io.github.ckdgus6068.jellycalendar

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/**
 * "구글로 시작하기" inside the app. Google does not let its sign-in run in a web view, so the app
 * shows the phone's own Google account sheet and hands the ID token to the shared page, which signs
 * in to Firebase with it.
 */
object GoogleSignIn {
    sealed interface Outcome {
        class Token(val idToken: String) : Outcome
        /** "cancelled" when the person closed the sheet, otherwise "unavailable" or "failed". */
        class Failed(val reason: String) : Outcome
    }

    fun isAvailable(activity: Activity): Boolean = activity.getString(R.string.google_web_client_id).isNotBlank()

    suspend fun idToken(activity: Activity): Outcome {
        val clientId = activity.getString(R.string.google_web_client_id)
        if (clientId.isBlank()) return Outcome.Failed("unavailable")
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(clientId).build())
            .build()
        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                Outcome.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Outcome.Failed("failed")
            }
        } catch (e: GetCredentialCancellationException) {
            Outcome.Failed("cancelled")
        } catch (e: GetCredentialException) {
            Outcome.Failed("failed")
        } catch (e: GoogleIdTokenParsingException) {
            Outcome.Failed("failed")
        }
    }
}
