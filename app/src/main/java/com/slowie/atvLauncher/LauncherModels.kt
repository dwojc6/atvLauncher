package com.slowie.atvLauncher

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.geometry.Rect

internal data class InputSource(
    val id: String,
    val label: String,
)

internal data class DownloadResult(
    val uri: Uri?,
    val errorMessage: String?,
)

internal data class LauncherApp(
    val label: String,
    val packageName: String,
    val componentName: ComponentName?,
    val launchIntent: Intent,
)

internal data class LauncherFolder(
    val id: String,
    val name: String,
    val appPackageNames: List<String>,
)

internal sealed interface RowItem {
    data class App(val app: LauncherApp) : RowItem
    data class Folder(val folder: LauncherFolder, val apps: List<LauncherApp>) : RowItem
    data class Input(val source: InputSource) : RowItem
}

internal sealed interface DockItem {
    val id: String

    data class App(val app: LauncherApp) : DockItem {
        override val id: String = app.packageName
    }

    data class Folder(val folder: LauncherFolder, val apps: List<LauncherApp>) : DockItem {
        override val id: String = folder.id
    }
}

internal sealed interface MoveTarget {
    data class Grid(val index: Int, val itemId: String) : MoveTarget
    data class Dock(val index: Int, val itemId: String?) : MoveTarget
}

internal data class AppMoveRequest(
    val app: LauncherApp,
    val sourceFolderId: String? = null,
    val anchor: HomeMenuAnchor? = null,
)

internal data class AppOptionsTarget(
    val app: LauncherApp,
    val showMoveTo: Boolean,
    val anchor: HomeMenuAnchor? = null,
    val fromDock: Boolean = false,
)

internal data class HomeMenuAnchor(
    val row: Int,
    val boundsInWindow: Rect,
)

internal enum class MoveDirection {
    Left,
    Right,
    Up,
    Down,
}

internal enum class TransportType {
    WIFI,
    ETHERNET,
    NONE,
}

internal enum class ThemeMode {
    LIGHT,
    DARK,
    SYSTEM,
}

internal data class NetworkStatus(
    val hasInternet: Boolean,
    val transport: TransportType,
)

internal data class HomeResumeTarget(
    val itemId: String,
    val folderId: String? = null,
)

internal sealed interface AppsSettingsEntry {
    val id: String
    val label: String

    data class AppEntry(val app: LauncherApp) : AppsSettingsEntry {
        override val id: String = "app:${app.packageName}"
        override val label: String = app.label
    }

    data class InputEntry(val source: InputSource) : AppsSettingsEntry {
        override val id: String = "input:${source.id}"
        override val label: String = "Input: ${source.label}"
    }
}
