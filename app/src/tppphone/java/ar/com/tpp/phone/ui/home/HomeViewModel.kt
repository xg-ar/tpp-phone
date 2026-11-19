// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui.home

import android.app.Application
import android.content.Context
import androidx.annotation.WorkerThread
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import ar.com.tpp.phone.account.ProvCredentials
import ar.com.tpp.phone.account.TppAccount
import ar.com.tpp.phone.account.provCredentials
import ar.com.tpp.phone.account.provSecret
import ar.com.tpp.phone.device.ContactNames
import ar.com.tpp.phone.device.buildReadiness
import ar.com.tpp.phone.logic.ReceivedCallRow
import ar.com.tpp.phone.logic.UnauthorizedAction
import ar.com.tpp.phone.logic.onUnauthorized
import ar.com.tpp.phone.logic.Status
import ar.com.tpp.phone.logic.inmateDialNumber
import ar.com.tpp.phone.logic.problemCount
import ar.com.tpp.phone.logic.receivedCallRow
import ar.com.tpp.phone.logic.status
import ar.com.tpp.phone.logic.voiceMessageRow
import ar.com.tpp.phone.prov.MessagesResult
import ar.com.tpp.phone.prov.ProvClient
import ar.com.tpp.phone.prov.VOICE_AUDIO_DIR
import ar.com.tpp.phone.prov.VOICE_PREFS
import ar.com.tpp.phone.prov.VoiceMessage
import ar.com.tpp.phone.prov.freshCachedMessages
import ar.com.tpp.phone.prov.markHeard
import ar.com.tpp.phone.prov.messagesFromCache
import ar.com.tpp.phone.prov.messagesToCache
import ar.com.tpp.phone.prov.sendPushToken
import ar.com.tpp.phone.prov.staleAudioFiles
import ar.com.tpp.phone.prov.without
import java.io.File
import java.time.ZoneId
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Account
import org.linphone.core.Call
import org.linphone.core.CallLog
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Reason
import org.linphone.core.RegistrationState
import org.linphone.core.tools.Log

private class CallData(
    val displayName: String?,
    val username: String,
    val startSec: Long,
    val durationSec: Int,
    val answered: Boolean
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        private const val TAG = "[TPP Home]"
        private const val MAX_CALLS = 50
        private const val RETRY_DELAY_MS = 30_000L
        private const val CORE_WAIT_MS = 5_000L
        private const val KEY_LIST = "list"
        private const val KEY_USER = "user"
        private const val CONTACTS_PREFS = "tpp_contacts"
        private const val KEY_CONTACTS_ASKED = "tpp_contacts_asked"
        private val SAFE_ID = Regex("[A-Za-z0-9-]+")
    }

    val status = MutableLiveData<Status>()
    val problemCount = MutableLiveData<Int>()
    val dialNumber = MutableLiveData<String>()
    val calls = MutableLiveData<List<ReceivedCallRow>>()

    /** One-time event: the SIP user whose line was activated on another phone. */
    val lineMovedFor = MutableLiveData<String?>()

    // Voice messages. [messages] as prov lists them; [messageItems] adds the texts of each bubble.
    val messages = MutableLiveData<List<VoiceMessage>>()
    val messageItems = MutableLiveData<List<VoiceMessageItem>>(emptyList())

    /** Ids whose audio is being downloaded: the bubble shows a spinner instead of ▶. */
    val downloading = MutableLiveData<Set<String>>(emptySet())

    /** One-time event: "Sin conexión". */
    val offline = MutableLiveData<Boolean>()

    val player = VoicePlayer(application)

    /** Sender's name in the phone's contacts (null without permission), called off the main thread. */
    private val contactNames = ContactNames(application)
    private val contactNameFor: (String) -> String? = { contactNames.nameFor(it) }

    /** The discreet "Mostrar los nombres de mis contactos" link: no permission and never asked before (the layout also needs a call or a message). */
    val showContactsLink = MutableLiveData(false)

    private val prov = ProvClient(application.getString(R.string.tpp_prov_url))
    private val audioDir = File(application.cacheDir, VOICE_AUDIO_DIR)
    private val prefs = application.getSharedPreferences(VOICE_PREFS, Context.MODE_PRIVATE)
    private val contactsPrefs = application.getSharedPreferences(CONTACTS_PREFS, Context.MODE_PRIVATE)
    private var refreshingMessages = false

    /** Set once prov's list is shown: the cached list must never replace it. Main thread only. */
    private var freshListShown = false

    @Volatile private var checkingLine = false

    @Volatile private var unauthorizedRetries = 0

    private val coreListener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState?,
            message: String
        ) {
            if (state == RegistrationState.Ok) unauthorizedRetries = 0
            // Forbidden too: which of the two a rejected digest gives is still to confirm on a phone.
            if (state == RegistrationState.Failed && account.error in setOf(Reason.Unauthorized, Reason.Forbidden)) {
                checkLine(account)
            }
            refresh(core)
        }

        override fun onCallLogUpdated(core: Core, callLog: CallLog) = refresh(core)

        override fun onNetworkReachable(core: Core, reachable: Boolean) = refresh(core)
    }

    init {
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)
            refresh(core)
        }
        viewModelScope.launch {
            val credentials = credentials() ?: return@launch
            // The last list, until prov answers (or without network).
            val cached = withContext(Dispatchers.IO) { loadCachedMessages(credentials.user) }
            publishMessages(cached, fromCache = true)
            withContext(Dispatchers.IO) { sendPushToken(prov, credentials) }
        }
    }

    override fun onCleared() {
        coreContext.postOnCoreThread { core -> core.removeListener(coreListener) }
        player.release()
        super.onCleared()
    }

    /** On resume and every 30 s while the home screen is visible (the push goes to Linphone's service in foreground). */
    fun refreshMessages() {
        if (refreshingMessages) return
        refreshingMessages = true
        viewModelScope.launch {
            try {
                val credentials = credentials() ?: return@launch
                val result = withContext(Dispatchers.IO) { prov.listMessages(credentials.user, credentials.secret) }
                // 401 is left to the status card (replaced credential); offline or failed keeps the last list.
                if (result !is MessagesResult.Ok) return@launch
                val list = result.list
                withContext(Dispatchers.IO) {
                    saveCachedMessages(credentials.user, list)
                    removeStaleAudio(list)
                }
                val loaded = player.state.value?.id
                if (loaded != null && list.none { it.id == loaded }) player.stop()
                publishMessages(list)
            } finally {
                refreshingMessages = false
            }
        }
    }

    /** ▶/❚❚: pauses, or plays from the cache, downloading the audio first if needed. */
    fun playOrPause(message: VoiceMessage) {
        val state = player.state.value
        if (state?.id == message.id && state.playing) {
            player.pause()
            return
        }
        val busy = downloading.value.orEmpty()
        if (message.id in busy) return
        val file = audioFile(message.id) ?: return
        if (file.exists()) {
            play(message, file)
            return
        }
        downloading.value = busy + message.id
        viewModelScope.launch {
            val credentials = credentials()
            val ok = credentials != null && withContext(Dispatchers.IO) {
                audioDir.mkdirs()
                prov.downloadAudio(credentials.user, credentials.secret, message.id, file)
            }
            downloading.value = downloading.value.orEmpty() - message.id
            when {
                ok -> play(message, file)
                !isOnline() -> offline.value = true
                // Probably gone (expired or deleted on another phone): the list catches up.
                else -> refreshMessages()
            }
        }
    }

    /** Seeks the loaded message only. */
    fun seek(message: VoiceMessage, fraction: Float) {
        if (player.state.value?.id == message.id) player.seekTo(fraction)
    }

    fun pausePlayback() = player.pause()

    /** After "¿Borrar este mensaje de voz?": asks prov, then drops it from the list and the cache. */
    fun delete(id: String) {
        viewModelScope.launch {
            val credentials = credentials() ?: return@launch
            val ok = withContext(Dispatchers.IO) { prov.deleteMessage(credentials.user, credentials.secret, id) }
            if (!ok) {
                if (!isOnline()) offline.value = true
                return@launch
            }
            if (player.state.value?.id == id) player.stop()
            val list = without(messages.value.orEmpty(), id)
            withContext(Dispatchers.IO) {
                audioFile(id)?.delete()
                saveCachedMessages(credentials.user, list)
            }
            publishMessages(list)
        }
    }

    private fun play(message: VoiceMessage, file: File) {
        if (!player.play(message.id, file)) return
        // prov marks it heard when the audio is downloaded; show it right away.
        if (!message.heard) {
            viewModelScope.launch {
                val list = markHeard(messages.value.orEmpty(), message.id)
                publishMessages(list)
                val credentials = credentials() ?: return@launch
                withContext(Dispatchers.IO) { saveCachedMessages(credentials.user, list) }
            }
        }
    }

    private suspend fun publishMessages(list: List<VoiceMessage>, fromCache: Boolean = false) {
        if (fromCache && freshListShown) return
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val items = withContext(Dispatchers.IO) {
            list.map { m ->
                val name = if (m.caller.isEmpty()) null else contactNameFor(m.caller)
                VoiceMessageItem(m, voiceMessageRow(m, contactName = name, nowEpochMs = now, zone = zone))
            }
        }
        // Checked again: a refresh may have finished while the rows were built.
        if (fromCache && freshListShown) return
        if (!fromCache) freshListShown = true
        messages.value = list
        messageItems.value = items
    }

    /** Only UUID-like ids become file names. */
    private fun audioFile(id: String): File? = if (SAFE_ID.matches(id)) File(audioDir, "$id.webm") else null

    @WorkerThread
    private fun loadCachedMessages(user: String): List<VoiceMessage> =
        // Another line's list (account deleted and activated again) is never shown.
        // Offline the list ages too: expired messages go and the days left are counted again.
        if (prefs.getString(KEY_USER, null) == user) {
            freshCachedMessages(messagesFromCache(prefs.getString(KEY_LIST, null)), System.currentTimeMillis())
        } else {
            emptyList()
        }

    @WorkerThread
    private fun saveCachedMessages(user: String, list: List<VoiceMessage>) {
        prefs.edit().putString(KEY_USER, user).putString(KEY_LIST, messagesToCache(list)).apply()
    }

    @WorkerThread
    private fun removeStaleAudio(list: List<VoiceMessage>) {
        val names = audioDir.list()?.toList() ?: return
        staleAudioFiles(names, list.map { it.id }.toSet()).forEach { File(audioDir, it).delete() }
    }

    private suspend fun credentials(): ProvCredentials? = onCore { core -> provCredentials(core) }

    private suspend fun isOnline(): Boolean = onCore { core -> core.isNetworkReachable } == true

    /** Runs [block] on the core thread; null if it fails or the core does not answer in time. */
    private suspend fun <T> onCore(block: (Core) -> T): T? {
        val result = CompletableDeferred<T?>()
        coreContext.postOnCoreThread { core ->
            result.complete(
                try {
                    block(core)
                } catch (e: Exception) {
                    null
                }
            )
        }
        return withTimeoutOrNull(CORE_WAIT_MS) { result.await() }
    }

    /** Asked once, granted or not: the link never comes back (the permission is optional). */
    fun contactsAsked() {
        contactsPrefs.edit().putBoolean(KEY_CONTACTS_ASKED, true).apply()
        updateContactsLink()
        // Names are cached per number only when found with permission, so rebuild both lists.
        coreContext.postOnCoreThread { core -> refresh(core) }
        messages.value?.let { list -> viewModelScope.launch { publishMessages(list) } }
    }

    fun updateContactsLink() {
        showContactsLink.value = !contactNames.hasPermission() && !contactsPrefs.getBoolean(KEY_CONTACTS_ASKED, false)
    }

    /** Called on resume too: permissions and battery settings change outside the app. */
    fun refresh() {
        coreContext.postOnCoreThread { core -> refresh(core) }
    }

    /** Asks prov whether the line moved to another phone or the new password is not live yet. */
    @WorkerThread
    private fun checkLine(account: Account) {
        if (checkingLine) return
        val user = account.params.identityAddress?.username ?: return
        val auth = account.findAuthInfo()
        val password = provSecret(auth?.password, auth?.ha1) ?: return
        checkingLine = true
        val client = ProvClient(getApplication<Application>().getString(R.string.tpp_prov_url))
        viewModelScope.launch {
            val status = withContext(Dispatchers.IO) { client.lineStatus(user, password) }
            val action = onUnauthorized(status, unauthorizedRetries)
            Log.w("$TAG Registration refused, line status [$status], action [$action]")
            when (action) {
                UnauthorizedAction.SHOW_LINE_MOVED -> lineMovedFor.value = user
                UnauthorizedAction.RETRY_LATER -> {
                    unauthorizedRetries++
                    delay(RETRY_DELAY_MS)
                    coreContext.postOnCoreThread { core -> core.refreshRegisters() }
                }
                UnauthorizedAction.NOTHING -> {}
            }
            checkingLine = false
        }
    }

    @WorkerThread
    private fun refresh(core: Core) {
        val readiness = buildReadiness(getApplication(), core)
        status.postValue(readiness.status())
        problemCount.postValue(readiness.problemCount())
        dialNumber.postValue(TppAccount.username(core)?.let { inmateDialNumber(it) }.orEmpty())

        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val logs = TppAccount.current(core)?.callLogs ?: core.callLogs
        val incoming = logs.filter { it.dir == Call.Dir.Incoming }
            .sortedByDescending { it.startDate }
            .take(MAX_CALLS)
            .map {
                CallData(it.fromAddress.displayName, it.fromAddress.username.orEmpty(), it.startDate, it.duration, it.status == Call.Status.Success)
            }
        // The contacts lookup is a content provider query: not on the core thread.
        viewModelScope.launch(Dispatchers.IO) {
            val rows = incoming.map {
                val contactName = if (it.username.isEmpty()) null else contactNameFor(it.username)
                receivedCallRow(
                    displayName = contactName ?: it.displayName,
                    username = it.username,
                    startEpochMs = it.startSec * 1000, // Linphone timestamps are in seconds
                    durationSec = it.durationSec,
                    answered = it.answered,
                    nowEpochMs = now,
                    zone = zone
                )
            }
            calls.postValue(rows)
        }
    }
}
