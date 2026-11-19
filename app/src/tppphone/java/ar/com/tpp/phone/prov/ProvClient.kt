// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.prov

import android.util.Base64
import androidx.annotation.WorkerThread
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import org.json.JSONObject
import org.linphone.core.tools.Log

/** Blocking calls to the provisioning server (the provisioning server): run them on Dispatchers.IO. */
class ProvClient(private val baseUrl: String) {
    companion object {
        private const val TAG = "[TPP Prov]"
        private const val CONNECT_TIMEOUT_MS = 10_000

        // Creating the line on the telephony platform may take up to 25 s.
        private const val READ_TIMEOUT_MS = 40_000
        private const val LEGAL_TIMEOUT_MS = 6_000

        fun mask(e164: String) = "*".repeat(maxOf(e164.length - 4, 0)) + e164.takeLast(4)
    }

    @WorkerThread
    fun startVerification(e164: String, installId: String): StartResult {
        val (http, body) = post("/v1/verificaciones", JSONObject().put("numero_e164", e164).put("id_instalacion", installId))
        Log.i("$TAG Code requested for [${mask(e164)}]: HTTP $http")
        return parseStart(http, body)
    }

    @WorkerThread
    fun confirm(verificationId: String, code: String, installId: String): ActivationResult {
        val (http, body) = post(
            "/v1/verificaciones/$verificationId/confirmar",
            JSONObject().put("codigo", code).put("id_instalacion", installId)
        )
        Log.i("$TAG Code confirmation: HTTP $http")
        return parseActivation(http, body)
    }

    @WorkerThread
    fun newHelpKey(installId: String): HelpKeyResult {
        val (http, body) = post("/v1/ayuda", JSONObject().put("id_instalacion", installId))
        Log.i("$TAG Help key requested: HTTP $http")
        return parseHelpKey(http, body)
    }

    @WorkerThread
    fun claimHelpKey(key: String, installId: String): ActivationResult {
        val (http, body) = post("/v1/ayuda/activar", JSONObject().put("clave", key).put("id_instalacion", installId))
        if (http != 202) Log.i("$TAG Help key claim: HTTP $http")
        return parseClaim(http, body)
    }

    @WorkerThread
    fun lineStatus(sipUser: String, password: String): LineStatus {
        val auth = Base64.encodeToString("$sipUser:$password".toByteArray(), Base64.NO_WRAP)
        val (http, body) = request("GET", "/v1/linea/estado", null, "Basic $auth")
        Log.i("$TAG Line status for [${mask(sipUser)}]: HTTP $http")
        return parseLineStatus(http, body)
    }

    @WorkerThread
    fun requestTestCall(sipUser: String, password: String): TestCallResult {
        val auth = Base64.encodeToString("$sipUser:$password".toByteArray(), Base64.NO_WRAP)
        val (http, body) = request("POST", "/v1/prueba-llamada", "{}", "Basic $auth")
        Log.i("$TAG Test call for [${mask(sipUser)}]: HTTP $http")
        return parseTestCall(http, body)
    }

    @WorkerThread
    fun deleteAccount(sipUser: String, password: String): DeleteResult {
        val auth = Base64.encodeToString("$sipUser:$password".toByteArray(), Base64.NO_WRAP)
        val (http, _) = request("POST", "/v1/linea/baja", "{}", "Basic $auth")
        Log.i("$TAG Delete account for [${mask(sipUser)}]: HTTP $http")
        return parseDeleteAccount(http)
    }

    @WorkerThread
    fun listMessages(sipUser: String, password: String): MessagesResult {
        val (http, body) = request("GET", "/v1/mensajes", null, basic(sipUser, password))
        Log.i("$TAG List messages for [${mask(sipUser)}]: HTTP $http")
        return parseMessages(http, body)
    }

    /** Downloads the audio into [dest] via dest.tmp + rename. Returns false on any failure. */
    @WorkerThread
    fun downloadAudio(sipUser: String, password: String, id: String, dest: File): Boolean {
        val tmp = File(dest.path + ".tmp")
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(baseUrl.trimEnd('/') + "/v1/mensajes/" + java.net.URLEncoder.encode(id, "UTF-8") + "/audio")
                .openConnection() as HttpURLConnection
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Authorization", basic(sipUser, password))
            val http = conn.responseCode
            Log.i("$TAG Download audio for [${mask(sipUser)}]: HTTP $http")
            if (http != 200) return false
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            dest.delete()
            tmp.renameTo(dest)
        } catch (e: IOException) {
            Log.w("$TAG Download audio failed: ${e.javaClass.simpleName}")
            false
        } finally {
            conn?.disconnect()
            // After a successful rename tmp no longer exists; otherwise drop the leftover.
            if (tmp.exists()) tmp.delete()
        }
    }

    /** True also when the message is already gone (404). */
    @WorkerThread
    fun deleteMessage(sipUser: String, password: String, id: String): Boolean {
        val (http, _) = request(
            "DELETE",
            "/v1/mensajes/" + java.net.URLEncoder.encode(id, "UTF-8"),
            null,
            basic(sipUser, password)
        )
        Log.i("$TAG Delete message for [${mask(sipUser)}]: HTTP $http")
        return http == 200 || http == 204 || http == 404
    }

    @WorkerThread
    fun putPushToken(sipUser: String, password: String, token: String): Boolean {
        val (http, _) = request(
            "PUT",
            "/v1/linea/push-token",
            JSONObject().put("token", token).toString(),
            basic(sipUser, password)
        )
        Log.i("$TAG Push token for [${mask(sipUser)}]: HTTP $http")
        return http == 200
    }

    private fun basic(sipUser: String, password: String) =
        "Basic " + Base64.encodeToString("$sipUser:$password".toByteArray(), Base64.NO_WRAP)

    /**
     * a legal text published by prov (doc = "terminos" or "privacidad"), as an HTML
     * fragment, or null when there is no usable answer. Short timeouts: the screen already
     * shows the bundled copy and only swaps it if this arrives.
     */
    @WorkerThread
    fun legalFragment(doc: String): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(baseUrl.trimEnd('/') + "/tpp-phone/legal/$doc.html").openConnection() as HttpURLConnection
            conn.connectTimeout = LEGAL_TIMEOUT_MS
            conn.readTimeout = LEGAL_TIMEOUT_MS
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("Accept", "text/html")
            if (conn.responseCode != 200) return null
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (e: IOException) {
            Log.w("$TAG Legal text [$doc] not loaded: ${e.javaClass.simpleName}")
            null
        } finally {
            conn?.disconnect()
        }
    }

    private fun post(path: String, json: JSONObject) = request("POST", path, json.toString(), null)

    /** (HTTP status, body), or (-1, null) when there was no answer. Bodies are never logged. */
    private fun request(method: String, path: String, json: String?, authorization: String?): Pair<Int, String?> {
        var conn: HttpURLConnection? = null
        return try {
            conn = URL(baseUrl.trimEnd('/') + path).openConnection() as HttpURLConnection
            conn.requestMethod = method
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.setRequestProperty("Accept", "application/json")
            authorization?.let { conn.setRequestProperty("Authorization", it) }
            if (json != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(json.toByteArray()) }
            }
            val http = conn.responseCode
            val stream = if (http >= 400) conn.errorStream else conn.inputStream
            http to stream?.bufferedReader()?.use { it.readText() }
        } catch (e: IOException) {
            Log.w("$TAG $method $path failed: ${e.javaClass.simpleName}")
            -1 to null
        } finally {
            conn?.disconnect()
        }
    }
}
