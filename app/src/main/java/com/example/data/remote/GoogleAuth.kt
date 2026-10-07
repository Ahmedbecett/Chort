package com.example.data.remote

import android.app.Activity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.example.R
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential

/**
 * Official Google sign-in via Android Credential Manager.
 *
 * Returns the raw Google ID token; the Rivo backend verifies it with
 * Google (tokeninfo) before creating/linking the account, so the app
 * never invents identity. The backend GOOGLE_CLIENT_ID must equal the
 * same web client id used here (R.string.default_web_client_id).
 */
object GoogleAuth {

    const val CANCELLED = "cancelled"

    suspend fun getIdToken(activity: Activity): Result<String> {
        return try {
            val serverClientId = activity.getString(R.string.default_web_client_id)
            if (serverClientId.isBlank()) {
                return Result.failure(Exception("Google sign-in is not configured on this build."))
            }
            val option = GetSignInWithGoogleOption.Builder(serverClientId).build()
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(option)
                .build()
            val manager = CredentialManager.create(activity)
            val result = manager.getCredential(context = activity, request = request)
            val credential = result.credential
            if (credential is CustomCredential &&
                credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
            ) {
                val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                Result.success(googleCredential.idToken)
            } else {
                Result.failure(Exception("Unexpected credential type returned by Google."))
            }
        } catch (e: GetCredentialCancellationException) {
            Result.failure(Exception(CANCELLED))
        } catch (e: Exception) {
            Result.failure(Exception(e.message ?: "Google sign-in failed."))
        }
    }
}
