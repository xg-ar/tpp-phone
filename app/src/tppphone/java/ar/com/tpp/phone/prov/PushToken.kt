// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.prov

import androidx.annotation.WorkerThread
import ar.com.tpp.phone.account.ProvCredentials
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import java.util.concurrent.TimeUnit
import org.linphone.core.tools.Log

private const val TAG = "[TPP Push Token]"
private const val TOKEN_WAIT_SECONDS = 20L

/**
 * Hands prov this phone's FCM token, for the "Nuevo mensaje de voz" push. The token itself is never logged.
 * Without Firebase (no google-services.json, no Play services) it just gives up.
 */
@WorkerThread
fun sendPushToken(client: ProvClient, credentials: ProvCredentials): Boolean {
    val token = try {
        Tasks.await(FirebaseMessaging.getInstance().token, TOKEN_WAIT_SECONDS, TimeUnit.SECONDS)
    } catch (e: Exception) {
        Log.w("$TAG No FCM token: ${e.javaClass.simpleName}")
        return false
    }
    if (token.isNullOrEmpty()) return false
    return client.putPushToken(credentials.user, credentials.secret, token)
}
