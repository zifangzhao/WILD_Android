package com.wild.android

import android.content.Context
import com.google.firebase.appdistribution.FirebaseAppDistribution

/**
 * Uses Firebase App Distribution for private WILD beta updates.
 *
 * Invited testers receive Google's sign-in and installation prompts. The APK
 * never has to be exposed from the public dashboard, and Android continues to
 * require the operator's approval before replacing an installed build.
 */
class AppUpdateChecker(
    context: Context,
) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesName,
        Context.MODE_PRIVATE,
    )
    @Volatile
    private var checkInFlight = false

    fun checkForUpdate(force: Boolean = false) {
        val nowMs = System.currentTimeMillis()
        val lastCheckAtMs = preferences.getLong(LastCheckAtMsKey, 0L)
        if (checkInFlight || (!force && nowMs - lastCheckAtMs < CheckIntervalMs)) {
            return
        }
        checkInFlight = true
        FirebaseAppDistribution.getInstance()
            .updateIfNewReleaseAvailable()
            .addOnCompleteListener {
                preferences.edit().putLong(LastCheckAtMsKey, System.currentTimeMillis()).apply()
                checkInFlight = false
            }
    }

    private companion object {
        const val PreferencesName = "wild_app_update"
        const val LastCheckAtMsKey = "last_check_at_ms"
        const val CheckIntervalMs = 6 * 60 * 60 * 1_000L
    }
}
