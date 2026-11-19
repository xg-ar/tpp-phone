// SPDX-License-Identifier: GPL-3.0-or-later
package ar.com.tpp.phone.logic

/**
 * The green check on the home screen pulses only while the app is ready for calls and the
 * screen is visible (no battery spent in the background), and never when the user turned
 * animations off in Android's accessibility settings.
 */
fun shouldPulse(status: Status?, visible: Boolean, animationsEnabled: Boolean): Boolean =
    status == Status.READY && visible && animationsEnabled
