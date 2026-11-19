// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.ui

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.navigation.fragment.NavHostFragment
import ar.com.tpp.phone.account.TppAccount
import ar.com.tpp.phone.work.ReRegisterWorker
import org.linphone.LinphoneApplication.Companion.coreContext
import org.linphone.LinphoneApplication.Companion.corePreferences
import org.linphone.R
import org.linphone.compatibility.Compatibility
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.tools.Log

class TppMainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "[TPP Main Activity]"
        const val VOICE_MESSAGES_CHANNEL = "tpp_voice_messages"
    }

    // Linphone asks for these in its welcome screens, which TPP skips. Without them an
    // incoming call rings with no answer screen, or is answered with no audio (seen 2026-10-24).
    // Camera too, although TPP has no video yet: video calls from the units are planned, and the
    // call screen warns about it otherwise. No contacts: TPP has no contact list.
    private val requiredPermissions = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(Manifest.permission.POST_NOTIFICATIONS)
        add(Manifest.permission.RECORD_AUDIO)
        add(Manifest.permission.CAMERA)
        add(Manifest.permission.MANAGE_OWN_CALLS)
    }

    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        Log.i("$TAG Permissions result $results")
        askFullScreenIntentPermissionIfNeeded()
    }

    private val coreListener = object : CoreListenerStub() {
        override fun onCallStateChanged(core: Core, call: Call, state: Call.State?, message: String) {
            if (state == Call.State.IncomingReceived) {
                Log.i("$TAG Incoming call while TPP is in foreground, showing call activity")
                runOnUiThread { coreContext.showCallActivity() }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.tpp_main_activity)
        createVoiceMessagesChannel()
        // Android 15+ draws apps edge to edge: keep content clear of the status and navigation bars,
        // and of the keyboard, so the bottom buttons rise above it.
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.tpp_nav_host)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            val keyboard = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, maxOf(bars.bottom, keyboard.bottom))
            WindowInsetsCompat.CONSUMED
        }
        // Work posted before the core thread exists is dropped (CoreContext.postOnCoreThread):
        // wait for it, as Linphone's MainActivity does.
        while (!coreContext.isReady()) {
            Thread.sleep(50)
        }

        // The graph is set in code, so it is set again after a fold, rotation or process restore;
        // the NavController then rebuilds the saved back stack.
        coreContext.postOnCoreThread { core ->
            // Phones set up with older versions may have Linphone's keep-alive service on for good:
            // a TLS connection and a keep-alive every 30 s, all day.
            if (corePreferences.keepServiceAlive) {
                Log.w("$TAG Keep-alive service was enabled, turning it off: TPP relies on push")
                corePreferences.keepServiceAlive = false
                coreContext.stopKeepAliveService()
            }
            val hasAccount = TppAccount.current(core) != null
            if (hasAccount) {
                TppAccount.applyCoreSettings(core)
                ReRegisterWorker.schedule(applicationContext)
            }
            Log.i("$TAG Starting, account configured [$hasAccount]")
            runOnUiThread { setStartDestination(hasAccount) }
        }
        if (savedInstanceState != null) return

        val missing = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) {
            askFullScreenIntentPermissionIfNeeded()
        } else {
            permissionsLauncher.launch(missing.toTypedArray())
        }
    }

    override fun onResume() {
        super.onResume()
        coreContext.postOnCoreThread { core ->
            core.addListener(coreListener)
            if (core.calls.any { it.state == Call.State.IncomingReceived }) {
                runOnUiThread { coreContext.showCallActivity() }
            }
        }
    }

    override fun onPause() {
        coreContext.postOnCoreThread { core -> core.removeListener(coreListener) }
        super.onPause()
    }

    /** For prov's "Nuevo mensaje de voz" push. Creating an existing channel is a no-op. */
    private fun createVoiceMessagesChannel() {
        val channel = NotificationChannel(
            VOICE_MESSAGES_CHANNEL,
            getString(R.string.tpp_voice_channel_name),
            NotificationManager.IMPORTANCE_HIGH
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun askFullScreenIntentPermissionIfNeeded() {
        if (!Compatibility.hasFullScreenIntentPermission(this)) {
            Log.w("$TAG Full screen intent permission missing, opening its settings")
            Compatibility.requestFullScreenIntentPermission(this)
        }
    }

    private fun setStartDestination(hasAccount: Boolean) {
        val navHost = supportFragmentManager.findFragmentById(R.id.tpp_nav_host) as NavHostFragment
        val navController = navHost.navController
        val graph = navController.navInflater.inflate(R.navigation.tpp_nav_graph)
        graph.setStartDestination(if (hasAccount) R.id.homeFragment else R.id.firstRunFragment)
        navController.graph = graph
    }
}
