// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.account

import androidx.annotation.WorkerThread
import ar.com.tpp.phone.prov.LineConfig
import org.linphone.core.Account
import org.linphone.core.Core
import org.linphone.core.Factory
import org.linphone.core.MediaEncryption
import org.linphone.core.TransportType
import org.linphone.core.tools.Log

/**
 * The single TPP Phone account and the core settings it needs.
 * Signup hands over only the user and password; everything else is fixed here.
 */
object TppAccount {
    private const val TAG = "[TPP Account]"

    const val DOMAIN = "fs.xg.net.ar"

    // 7 days, equal to Flexisip max-expires; a daily worker re-registers.
    const val EXPIRES_SECONDS = 604800

    private val ALLOWED_AUDIO_CODECS = setOf("PCMA", "telephone-event")

    @WorkerThread
    fun current(core: Core): Account? = core.defaultAccount ?: core.accountList.firstOrNull()

    @WorkerThread
    fun username(core: Core): String? = current(core)?.params?.identityAddress?.username

    /** Replaces any existing account: one account per app. */
    @WorkerThread
    fun create(core: Core, username: String, password: String) {
        Log.i("$TAG Creating account for [$username@$DOMAIN], removing [${core.accountList.size}] existing account(s)")
        core.clearAccounts()
        core.clearAllAuthInfo()
        applyCoreSettings(core)

        val identity = Factory.instance().createAddress("sip:$username@$DOMAIN")
            ?: return Log.e("$TAG Can't parse identity for username [$username]")
        val server = Factory.instance().createAddress("sip:$DOMAIN")!!
        server.transport = TransportType.Tls

        core.addAuthInfo(Factory.instance().createAuthInfo(username, null, password, null, null, DOMAIN))

        val params = core.createAccountParams()
        params.identityAddress = identity
        params.serverAddress = server
        params.isRegisterEnabled = true
        params.expires = EXPIRES_SECONDS
        params.pushNotificationAllowed = core.isPushNotificationAvailable
        // No STUN/ICE: Flexisip's MediaRelay latches on the phone's real source address.
        params.natPolicy = null

        val account = core.createAccount(params)
        core.addAccount(account)
        core.defaultAccount = account
        Log.i("$TAG Account added, push allowed [${params.pushNotificationAllowed}]")
    }

    /** Applies the line prov activated; false if it is for another SIP domain. */
    @WorkerThread
    fun applyLine(core: Core, config: LineConfig): Boolean {
        if (config.domain != DOMAIN) {
            Log.e("$TAG prov sent domain [${config.domain}], expected [$DOMAIN]")
            return false
        }
        create(core, config.sipUser, config.password)
        return true
    }

    /** Idempotent; also called at every app start in case a Linphone update changed them. */
    @WorkerThread
    fun applyCoreSettings(core: Core) {
        core.mediaEncryption = MediaEncryption.SRTP
        core.isMediaEncryptionMandatory = true
        core.isVideoCaptureEnabled = false
        core.isVideoDisplayEnabled = false
        for (payload in core.audioPayloadTypes) {
            payload.enable(payload.mimeType in ALLOWED_AUDIO_CODECS)
        }
        // The caller's sip:user@ip means nothing to a relative; the call screens show only the name.
        core.config.setBool("ui", "hide_sip_addresses", true)
    }
}

/**
 * What the phone shows prov to prove it owns its line: the SIP password, or its HA1 once Linphone
 * dropped the password after registering ([sip] store_ha1_passwd). prov accepts either.
 */
fun provSecret(password: String?, ha1: String?): String? = password?.takeIf { it.isNotEmpty() } ?: ha1

/** The line's user and the secret prov accepts (see [provSecret]). Never logged. */
class ProvCredentials(val user: String, val secret: String) {
    override fun toString() = "ProvCredentials(user=$user)"
}

/** Call on the core thread. Null when there is no account or no stored secret. */
@WorkerThread
fun provCredentials(core: Core): ProvCredentials? {
    val account = TppAccount.current(core) ?: return null
    val user = account.params.identityAddress?.username ?: return null
    val auth = account.findAuthInfo()
    val secret = provSecret(auth?.password, auth?.ha1) ?: return null
    return ProvCredentials(user, secret)
}
