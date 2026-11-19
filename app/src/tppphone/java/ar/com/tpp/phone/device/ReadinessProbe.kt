// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.PowerManager
import androidx.annotation.WorkerThread
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import ar.com.tpp.phone.account.TppAccount
import ar.com.tpp.phone.logic.Readiness
import ar.com.tpp.phone.logic.RegState
import org.linphone.compatibility.Compatibility
import org.linphone.core.Core
import org.linphone.core.RegistrationState

/** The single source of the home traffic light and the problem screen. */
@WorkerThread
fun buildReadiness(context: Context, core: Core): Readiness {
    val registered = when (TppAccount.current(core)?.state) {
        RegistrationState.Ok -> RegState.OK
        RegistrationState.Progress, RegistrationState.Refreshing -> RegState.CONNECTING
        RegistrationState.Failed -> RegState.FAILED
        else -> RegState.NONE
    }
    val power = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    return Readiness(
        registered = registered,
        internet = core.isNetworkReachable,
        notifications = Compatibility.isPostNotificationsPermissionGranted(context) &&
            NotificationManagerCompat.from(context).areNotificationsEnabled(),
        microphone = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED,
        fullScreenIntent = Compatibility.hasFullScreenIntentPermission(context),
        batteryUnrestricted = power.isIgnoringBatteryOptimizations(context.packageName)
    )
}
