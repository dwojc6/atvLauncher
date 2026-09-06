package com.slowie.atvLauncher

import android.os.Bundle
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.tv.material3.ExperimentalTvMaterial3Api
import com.slowie.atvLauncher.ui.theme.ATLauncherTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val ACTION_HOME_TAKEOVER = "com.slowie.atvLauncher.action.HOME_TAKEOVER"
        const val EXTRA_FROM_BOOT = "com.slowie.atvLauncher.extra.FROM_BOOT"
        const val EXTRA_FROM_LAUNCHER = "com.slowie.atvLauncher.extra.FROM_LAUNCHER"

        @Volatile
        var isInForeground: Boolean = false
    }

    private val homeResetTokenState = mutableLongStateOf(0L)
    private val homeResetFromBootState = mutableStateOf(false)
    private val homeResetFromLauncherState = mutableStateOf(false)

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_RUNNING_LOW) {
            trimBitmapCaches()
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        trimBitmapCaches()
    }

    @OptIn(ExperimentalTvMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        setTheme(R.style.Theme_ATLauncher)
        super.onCreate(savedInstanceState)
        applyHomeIntent(intent)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.decorView.clipToOutline = false
        window.setBackgroundDrawable(ColorDrawable(Color.Black.toArgb()))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        setContent {
            val prefs = remember { getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
            var themeMode by remember { mutableStateOf(loadThemeMode(prefs)) }
            val estimatedSystemDarkMode = rememberEstimatedSystemDarkMode()
            val darkModeEnabled = when (themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> estimatedSystemDarkMode
            }
            SideEffect {
                val background = if (darkModeEnabled) Color(0xFF1C1C1C) else Color(0xFFC3C3C5)
                window.setBackgroundDrawable(ColorDrawable(background.toArgb()))
            }
            ATLauncherTheme(isInDarkTheme = darkModeEnabled) {
                Box(modifier = Modifier.fillMaxSize()) {
                    LauncherScreen(
                        darkModeEnabled = darkModeEnabled,
                        themeMode = themeMode,
                        homeResetToken = homeResetTokenState.longValue,
                        homeResetFromBoot = homeResetFromBootState.value,
                        homeResetFromLauncher = homeResetFromLauncherState.value,
                        onThemeModeChange = { mode ->
                            themeMode = mode
                            saveThemeMode(prefs, mode)
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyHomeIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        isInForeground = true
    }

    override fun onStop() {
        isInForeground = false
        super.onStop()
    }

    private fun applyHomeIntent(intent: android.content.Intent?) {
        homeResetTokenState.longValue = homeResetTokenFromIntent(intent)
        homeResetFromBootState.value = intent?.getBooleanExtra(EXTRA_FROM_BOOT, false) == true
        homeResetFromLauncherState.value =
            intent?.getBooleanExtra(EXTRA_FROM_LAUNCHER, false) == true
    }

    private fun homeResetTokenFromIntent(intent: android.content.Intent?): Long {
        return if (intent?.action == ACTION_HOME_TAKEOVER) {
            System.currentTimeMillis()
        } else {
            0L
        }
    }
}
