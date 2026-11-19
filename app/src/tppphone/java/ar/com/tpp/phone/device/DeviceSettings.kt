// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri

/** Intents that open the Android settings each readiness check needs. */
object DeviceSettings {
    // Vendor "autostart" screens; package visibility is declared in the tppphone manifest <queries>.
    private val AUTOSTART_SCREENS = listOf(
        ComponentName("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
        ComponentName("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.oplus.safecenter", "com.oplus.safecenter.permission.startup.StartupAppListActivity"),
        ComponentName("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
        ComponentName("com.huawei.systemmanager", "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity")
    )

    // Opens the system battery-optimization list (no permission needed). The user picks
    // "Unrestricted" there; the app never asks for REQUEST_IGNORE_BATTERY_OPTIMIZATIONS.
    // ProblemFragment.open() falls back to the app details screen if this one is not available.
    fun batteryUnrestricted() = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun appNotifications(context: Context) =
        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)

    fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

    fun network() = Intent(Settings.ACTION_WIRELESS_SETTINGS)

    /** The vendor autostart screen if this phone has one (Xiaomi, Oppo, Vivo, Huawei). */
    fun manufacturerAutostart(context: Context): Intent? =
        AUTOSTART_SCREENS.map { Intent().setComponent(it) }
            .firstOrNull { context.packageManager.resolveActivity(it, 0) != null }
}
