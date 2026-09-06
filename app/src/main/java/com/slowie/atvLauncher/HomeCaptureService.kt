package com.slowie.atvLauncher

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

class HomeCaptureService : AccessibilityService() {
    companion object {
        private var instanceRef: WeakReference<HomeCaptureService>? = null
        private const val WAKE_LAUNCH_DEBOUNCE_MS = 3_000L

        fun showPowerDialog(): Boolean {
            val service = instanceRef?.get() ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        }

        fun showSystemMenu(): Boolean {
            val service = instanceRef?.get() ?: return false
            return service.performGlobalAction(GLOBAL_ACTION_MENU)
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var screenReceiver: BroadcastReceiver? = null
    private var lastWakeLaunchElapsed = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceInfo = (serviceInfo ?: AccessibilityServiceInfo()).apply {
            eventTypes = 0
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS
            notificationTimeout = 100
        }
        instanceRef = WeakReference(this)
        registerScreenReceiver()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode != KeyEvent.KEYCODE_HOME) return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                return true
            }
            KeyEvent.ACTION_UP -> {
                launchLauncherHome(this, fromLauncher = MainActivity.isInForeground)
                return true
            }
        }
        return true
    }

    override fun onUnbind(intent: Intent?): Boolean {
        unregisterScreenReceiver()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        unregisterScreenReceiver()
        if (instanceRef?.get() === this) {
            instanceRef = null
        }
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    private fun registerScreenReceiver() {
        unregisterScreenReceiver()
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_ON,
                    Intent.ACTION_USER_PRESENT,
                    -> scheduleWakeLauncherLaunch()
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
        screenReceiver = receiver
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: IllegalArgumentException) {
                // Already unregistered.
            }
        }
        screenReceiver = null
    }

    private fun scheduleWakeLauncherLaunch() {
        val now = SystemClock.elapsedRealtime()
        if (now - lastWakeLaunchElapsed < WAKE_LAUNCH_DEBOUNCE_MS) return
        lastWakeLaunchElapsed = now
        mainHandler.removeCallbacksAndMessages(TOKEN_WAKE_LAUNCH)
        mainHandler.postDelayed({
            if (!MainActivity.isInForeground) {
                launchLauncherHome(applicationContext, fromBoot = true)
            }
        }, TOKEN_WAKE_LAUNCH, 1_500L)
    }
}

private val TOKEN_WAKE_LAUNCH = Any()
