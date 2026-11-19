// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import ar.com.tpp.phone.account.ProvCredentials
import ar.com.tpp.phone.account.TppAccount
import ar.com.tpp.phone.account.provCredentials
import ar.com.tpp.phone.prov.ProvClient
import ar.com.tpp.phone.prov.sendPushToken
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.R
import org.linphone.core.Account
import org.linphone.core.AccountListenerStub
import org.linphone.core.RegistrationState
import org.linphone.core.tools.Log

/**
 * Re-registers once a day so the 7-day registration (TppAccount.EXPIRES_SECONDS) never
 * lapses on a phone where the app stays closed. The process is started by
 * WorkManager, and LinphoneApplication.onCreate creates and starts the core before this runs.
 */
class ReRegisterWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    companion object {
        private const val TAG = "[TPP ReRegister]"
        private const val WORK_NAME = "tpp-daily-reregister"
        private const val WAIT_SECONDS = 30L

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
        }

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReRegisterWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }

    override fun doWork(): Result {
        // In a process started by WorkManager the core may still be starting, and work posted
        // before its thread exists is dropped.
        val readyBy = System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(WAIT_SECONDS)
        while (!coreContext.isReady() && System.currentTimeMillis() < readyBy) {
            Thread.sleep(50)
        }
        if (!coreContext.isReady()) {
            Log.w("$TAG Core not ready, skipping this run")
            return Result.success()
        }

        val done = CountDownLatch(1)
        var registered = false
        val listener = object : AccountListenerStub() {
            override fun onRegistrationStateChanged(account: Account, state: RegistrationState?, message: String) {
                if (state == RegistrationState.Ok) {
                    registered = true
                    done.countDown()
                } else if (state == RegistrationState.Failed) {
                    done.countDown()
                }
            }
        }

        var account: Account? = null
        var credentials: ProvCredentials? = null
        coreContext.postOnCoreThread { core ->
            account = TppAccount.current(core)
            credentials = provCredentials(core)
            val current = account
            if (current == null) {
                Log.w("$TAG No account, nothing to refresh")
                done.countDown()
                return@postOnCoreThread
            }
            current.addListener(listener)
            Log.i("$TAG Refreshing registration")
            core.refreshRegisters()
        }

        done.await(WAIT_SECONDS, TimeUnit.SECONDS)
        coreContext.postOnCoreThread { account?.removeListener(listener) }
        Log.i("$TAG Done, registered [$registered]")
        // Keeps prov's FCM token fresh for the voice message push; the token can rotate.
        credentials?.let { sendPushToken(ProvClient(applicationContext.getString(R.string.tpp_prov_url)), it) }
        // A failure is retried by the next daily run; the 7-day expiry leaves room for it.
        return Result.success()
    }
}
