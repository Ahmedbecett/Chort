package com.example.data.remote

import android.app.Activity
import com.example.R
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * Official Facebook Login (facebook-android-sdk).
 *
 * Returns the user access token; the ZEVORA backend verifies it with
 * Facebook (debug_token) before creating/linking the account.
 * Requires R.string.facebook_app_id to be set to a real App ID.
 */
object FacebookAuth {

    const val CANCELLED = "cancelled"

    val callbackManager: CallbackManager by lazy { CallbackManager.Factory.create() }

    fun isConfigured(activity: Activity): Boolean {
        val id = try {
            activity.getString(R.string.facebook_app_id)
        } catch (_: Exception) {
            ""
        }
        return id.isNotBlank() && !id.startsWith("SET_") && id.all { it.isDigit() }
    }

    suspend fun login(activity: Activity): Result<String> =
        suspendCancellableCoroutine { cont ->
            if (!isConfigured(activity)) {
                cont.resume(
                    Result.failure(Exception("Facebook sign-in is not configured on this build."))
                )
                return@suspendCancellableCoroutine
            }
            try {
                LoginManager.getInstance().registerCallback(
                    callbackManager,
                    object : FacebookCallback<LoginResult> {
                        override fun onSuccess(result: LoginResult) {
                            if (!cont.isActive) return
                            val token = result.accessToken?.token
                            if (token.isNullOrBlank()) {
                                cont.resume(Result.failure(Exception("Facebook returned an empty token.")))
                            } else {
                                cont.resume(Result.success(token))
                            }
                        }

                        override fun onCancel() {
                            if (cont.isActive) cont.resume(Result.failure(Exception(CANCELLED)))
                        }

                        override fun onError(error: FacebookException) {
                            if (cont.isActive) {
                                cont.resume(
                                    Result.failure(Exception(error.message ?: "Facebook sign-in failed."))
                                )
                            }
                        }
                    }
                )
                LoginManager.getInstance()
                    .logInWithReadPermissions(activity, listOf("email", "public_profile"))
            } catch (e: Exception) {
                if (cont.isActive) {
                    cont.resume(Result.failure(Exception(e.message ?: "Facebook sign-in failed.")))
                }
            }
        }

    fun logout() {
        try {
            LoginManager.getInstance().logOut()
        } catch (_: Exception) {
        }
    }
}
