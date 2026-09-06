package com.slowie.atvLauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_QUICKBOOT_POWERON,
            ACTION_HTC_QUICKBOOT_POWERON,
            -> scheduleLauncherLaunch(context.applicationContext)
        }
    }

    private fun scheduleLauncherLaunch(context: Context) {
        val pendingResult = goAsync()
        Handler(Looper.getMainLooper()).postDelayed({
            try {
                if (!MainActivity.isInForeground) {
                    launchLauncherHome(context, fromBoot = true)
                }
            } finally {
                pendingResult.finish()
            }
        }, BOOT_LAUNCH_DELAY_MS)
    }

    companion object {
        private const val BOOT_LAUNCH_DELAY_MS = 2_000L
        private const val ACTION_QUICKBOOT_POWERON = "android.intent.action.QUICKBOOT_POWERON"
        private const val ACTION_HTC_QUICKBOOT_POWERON = "com.htc.intent.action.QUICKBOOT_POWERON"
    }
}
