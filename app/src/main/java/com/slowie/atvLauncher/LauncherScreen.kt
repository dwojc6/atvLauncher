@file:OptIn(ExperimentalFoundationApi::class, ExperimentalComposeUiApi::class)

package com.slowie.atvLauncher

import android.content.Context
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshots.Snapshot
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

private const val APP_REFRESH_MIN_INTERVAL_MS = 30_000L
private const val VIEWPORT_SNAP_EPSILON_PX = 0.5f
private const val VIEWPORT_SLIDE_DURATION_MS = 300

/** Matches the dock-to-app-row slide from tvOSLauncher. */
private val ViewportSlideEasing = FastOutSlowInEasing

@Composable
internal fun LauncherScreen(
    darkModeEnabled: Boolean,
    themeMode: ThemeMode,
    homeResetToken: Long,
    homeResetFromBoot: Boolean,
    homeResetFromLauncher: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit,
) {
    fun inputIconOverrideKey(inputId: String): String = "input:$inputId"

    val context = LocalContext.current
    val hazeState = rememberHazeState()
    val focusManager = LocalFocusManager.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }
    val configuration = LocalConfiguration.current
    val gridState = rememberLazyGridState()
    var dockHasFocus by remember { mutableStateOf(false) }
    var dockInteractionToken by remember { mutableStateOf(0) }
    var showDockChevron by remember { mutableStateOf(false) }
    var drawerHasFocus by remember { mutableStateOf(false) }
    var focusedGridRow by remember { mutableStateOf<Int?>(null) }
    var viewportOffsetPx by remember { mutableStateOf(0f) }
    val viewportOffsetAnimation = remember { Animatable(0f) }
    var viewportGliding by remember { mutableStateOf(false) }
    var lastViewportRowCount by remember { mutableStateOf(-1) }
    val statusFocusRequesters = remember { List(1) { FocusRequester() } }
    val dockItemRequesters = remember { List(DOCK_SIZE) { FocusRequester() } }
    val headerHeight = (configuration.screenHeightDp.dp - DrawerPeekHeight - GridSpacing)
        .coerceAtLeast(0.dp)
    val homeTileWidth = remember(configuration.screenWidthDp) {
        ((configuration.screenWidthDp.dp - GridHorizontalPadding * 2 - AppHorizontalSpacing * 5) / 6)
            .coerceAtMost(DrawerCardWidth)
    }
    val gridHorizontalPadding = remember(configuration.screenWidthDp, homeTileWidth) {
        (configuration.screenWidthDp.dp -
                (homeTileWidth * 6 + AppHorizontalSpacing * 5)) / 2
    }
    val dockTopOffsetPxBase = with(LocalDensity.current) {
        (headerHeight - DockRowHeight - DockRowBottomPadding - DockRowTopInsetInApps)
            .coerceAtLeast(0.dp)
            .toPx()
    }
    val gridBottomInsetPx = with(LocalDensity.current) {
        (GridBottomPadding + DrawerLabelSpacing + 12.dp).toPx()
    }
    val appRowBlockHeight = DrawerCardHeight + DrawerLabelSpacing + DrawerLabelHeight + GridSpacing
    val previewDockTopInset = (
            configuration.screenHeightDp.dp -
                    DockRowHeight -
                    DockRowBottomPadding -
                    (appRowBlockHeight * 3)
            ).div(2f).coerceAtLeast(20.dp)
    val previewPeekHeight: Dp = minOf(previewDockTopInset * 0.92f, appRowBlockHeight * 0.56f)
    val previewSettleTrim = minOf(18.dp, appRowBlockHeight * 0.12f)
    val thirdRowPeekBoost = minOf(14.dp, appRowBlockHeight * 0.1f)
    val previewScrollOffsetPx = (dockTopOffsetPxBase - with(LocalDensity.current) {
        previewDockTopInset.toPx()
    }).coerceAtLeast(0f)
    val previewSettleTrimPx = with(LocalDensity.current) { previewSettleTrim.toPx() }
    val previewPeekHeightPx = with(LocalDensity.current) { previewPeekHeight.toPx() }
    val thirdRowPeekBoostPx = with(LocalDensity.current) { thirdRowPeekBoost.toPx() }
    val appRowBlockHeightPx = with(LocalDensity.current) { appRowBlockHeight.toPx() }
    val previewBaseOffsetPx = (previewScrollOffsetPx - previewSettleTrimPx).coerceAtLeast(0f)
    val previewPeekAnchorPx = previewScrollOffsetPx + previewPeekHeightPx + thirdRowPeekBoostPx
    val previewPeekExtraPx = (previewPeekAnchorPx - previewBaseOffsetPx).coerceAtLeast(0f)

    var allApps by remember { mutableStateOf(loadCachedApps(prefs)) }
    var hiddenPackages by remember { mutableStateOf(loadHiddenPackages(prefs)) }
    var hiddenInputs by remember { mutableStateOf(loadHiddenInputs(prefs)) }
    var dockSlots by remember { mutableStateOf(loadDockSlots(prefs)) }
    var folders by remember { mutableStateOf(loadFolders(prefs)) }
    var backgroundUri by remember { mutableStateOf(loadBackgroundUri(prefs)) }
    var lightModeBackgroundUri by remember { mutableStateOf(loadLightModeBackgroundUri(prefs)) }
    var darkModeBackgroundUri by remember { mutableStateOf(loadDarkModeBackgroundUri(prefs)) }
    var backgroundPresetId by remember { mutableStateOf(loadBackgroundPresetId(prefs)) }
    var lightModePresetId by remember { mutableStateOf(loadLightModePresetId(prefs)) }
    var darkModePresetId by remember { mutableStateOf(loadDarkModePresetId(prefs)) }
    val activeBackgroundUri = if (darkModeEnabled) {
        darkModeBackgroundUri ?: backgroundUri
    } else {
        lightModeBackgroundUri ?: backgroundUri
    }
    var backgroundBitmap by remember(activeBackgroundUri) { mutableStateOf<ImageBitmap?>(null) }
    var iconOverrides by remember { mutableStateOf(loadIconOverrides(prefs)) }
    var labelOverrides by remember { mutableStateOf(loadLabelOverrides(prefs)) }
    var inputSources by remember { mutableStateOf<List<InputSource>>(emptyList()) }
    var gridOrder by remember { mutableStateOf(loadGridOrder(prefs)) }
    var deferredArtworkLoadEnabled by remember { mutableStateOf(false) }
    var backgroundCycleStep by remember { mutableLongStateOf(0L) }
    var lastAppsRefreshElapsed by remember { mutableLongStateOf(0L) }
    var lastLaunchedTarget by remember { mutableStateOf(loadHomeResumeTarget(prefs)) }

    var showLauncherSettings by rememberSaveable { mutableStateOf(false) }
    var showAppearanceSettings by rememberSaveable { mutableStateOf(false) }
    var showHomeButtonTakeoverSettings by rememberSaveable { mutableStateOf(false) }
    var showBackgroundMenu by rememberSaveable { mutableStateOf(false) }
    var showBackgroundPresets by rememberSaveable { mutableStateOf(false) }
    var showAppsSettings by rememberSaveable { mutableStateOf(false) }
    var showBackgroundUrlDialog by rememberSaveable { mutableStateOf(false) }
    var backgroundUrlLightInput by rememberSaveable { mutableStateOf("") }
    var backgroundUrlDarkInput by rememberSaveable { mutableStateOf("") }
    var backgroundUrlBothInput by rememberSaveable { mutableStateOf("") }
    var backgroundUrlError by rememberSaveable { mutableStateOf<String?>(null) }
    var backgroundUrlLoading by remember { mutableStateOf(false) }
    var backgroundModePresetTarget by remember { mutableStateOf<BackgroundPreset?>(null) }
    var backgroundModeMenuAnchor by remember { mutableStateOf<HomeMenuAnchor?>(null) }
    var appOptionsTarget by remember { mutableStateOf<AppOptionsTarget?>(null) }
    var folderAppOptionsTarget by remember { mutableStateOf<AppMoveRequest?>(null) }
    var moveAppRequest by remember { mutableStateOf<AppMoveRequest?>(null) }
    var folderOptionsTargetId by remember { mutableStateOf<String?>(null) }
    var openFolderId by remember { mutableStateOf<String?>(null) }
    var requestInlineRename by remember { mutableStateOf(false) }
    var openFolderFocusTarget by remember { mutableStateOf<String?>(null) }
    var openFolderFocusToken by remember { mutableStateOf(0) }
    var inputOptionsTarget by remember { mutableStateOf<InputSource?>(null) }
    var focusRestoreTarget by remember { mutableStateOf<String?>(null) }
    var focusRestoreToken by remember { mutableStateOf(0) }
    var moveDockTarget by remember { mutableStateOf<LauncherApp?>(null) }
    var iconPickerTarget by remember { mutableStateOf<LauncherApp?>(null) }
    var inputIconPickerTarget by remember { mutableStateOf<InputSource?>(null) }
    var renameDialogTarget by remember { mutableStateOf<LauncherApp?>(null) }
    var renameDialogInput by remember { mutableStateOf("") }
    var initialDockFocusSet by remember { mutableStateOf(false) }
    var moveModeItemId by remember { mutableStateOf<String?>(null) }
    var folderMoveModePackage by remember { mutableStateOf<String?>(null) }
    var pendingDockFocusIndex by remember { mutableStateOf<Int?>(null) }
    var lastMoveItemId by remember { mutableStateOf<String?>(null) }
    var suppressDockFocus by remember { mutableStateOf(false) }
    var dockSuppressToken by remember { mutableStateOf(0) }
    var moveGridIndexOverride by remember { mutableStateOf<Int?>(null) }
    var homeMenuAnchors by remember { mutableStateOf<Map<String, HomeMenuAnchor>>(emptyMap()) }
    var dockMenuAnchors by remember { mutableStateOf<Map<String, HomeMenuAnchor>>(emptyMap()) }
    var folderMenuAnchors by remember { mutableStateOf<Map<String, HomeMenuAnchor>>(emptyMap()) }
    var settingsFocusIndex by rememberSaveable { mutableStateOf(0) }
    var settingsScrollIndex by rememberSaveable { mutableStateOf(0) }
    var settingsScrollOffset by rememberSaveable { mutableStateOf(0) }
    var appearanceFocusIndex by rememberSaveable { mutableStateOf(0) }
    var homeButtonTakeoverFocusIndex by rememberSaveable { mutableStateOf(0) }
    var backgroundMenuFocusIndex by rememberSaveable { mutableStateOf(0) }
    var backgroundPresetFocusIndex by rememberSaveable { mutableStateOf(0) }
    val coroutineScope = rememberCoroutineScope()
    val handleMoveDrop = {
        val moving = moveModeItemId
        if (moving != null) {
            focusRestoreTarget = moving
            focusRestoreToken++
            lastMoveItemId = moving
            val shouldSuppress = !dockSlots.contains(moving)
            suppressDockFocus = shouldSuppress
            if (shouldSuppress) {
                dockSuppressToken++
            }
        }
        pendingDockFocusIndex = null
        moveModeItemId = null
    }
    val persistFolders: (List<LauncherFolder>) -> Unit = { updated ->
        folders = updated
        saveFolders(prefs, updated)
    }
    val saveLastLaunchedTarget: (String, String?) -> Unit = { itemId, folderId ->
        val target = HomeResumeTarget(itemId = itemId, folderId = folderId)
        lastLaunchedTarget = target
        saveHomeResumeTarget(prefs, itemId = itemId, folderId = folderId)
    }
    val launchHomeApp: (LauncherApp) -> Unit = { app ->
        saveLastLaunchedTarget(app.packageName, null)
        launchApp(context, app)
    }
    val launchFolderApp: (LauncherApp, String) -> Unit = { app, folderId ->
        saveLastLaunchedTarget(app.packageName, folderId)
        launchApp(context, app)
    }
    val launchHomeInput: (InputSource) -> Unit = { input ->
        saveLastLaunchedTarget(inputIconOverrideKey(input.id), null)
        openTvInput(context, input.id)
    }
    val visibleGridTargetsForState: (List<String>, List<LauncherFolder>, List<String?>) -> List<String> =
        { order, currentFolders, currentDock ->
            val docked = currentDock.filterNotNull().toSet()
            val packagesInFolders = folderPackages(currentFolders)
            order.filter { itemId ->
                if (isFolderId(itemId)) {
                    currentFolders.any { it.id == itemId } && !docked.contains(itemId)
                } else {
                    !docked.contains(itemId) && !packagesInFolders.contains(itemId)
                }
            }
        }
    val rebalanceDockAfterRemovingApp: (String, List<String>) -> List<String?> = { removedPackage, currentGrid ->
        if (dockSlots.contains(removedPackage)) {
            val updatedDock = dockSlots.filterNot { it == removedPackage }.toMutableList()
            while (updatedDock.size < DOCK_SIZE) {
                updatedDock.add(null)
            }
            val refillApp = currentGrid.firstOrNull { itemId ->
                !isFolderId(itemId) && itemId != removedPackage && !updatedDock.contains(itemId)
            }
            if (refillApp != null) {
                updatedDock[DOCK_SIZE - 1] = refillApp
            }
            dockSlots = updatedDock.take(DOCK_SIZE)
            saveDockSlots(prefs, dockSlots)
            dockSlots
        } else {
            dockSlots
        }
    }
    val removeFolderFromGridOrder: (List<String>, String) -> List<String> = { order, folderId ->
        order.filterNot { it == folderId }
    }
    val cleanupEmptyFolders: (List<LauncherFolder>, List<String>) -> Pair<List<LauncherFolder>, List<String>> = { currentFolders, currentGrid ->
        val remainingFolders = currentFolders.filter { it.appPackageNames.isNotEmpty() }
        val remainingIds = remainingFolders.map { it.id }.toSet()
        remainingFolders to currentGrid.filterNot { isFolderId(it) && !remainingIds.contains(it) }
    }
    val updateHomeMenuAnchor: (String, Int, androidx.compose.ui.geometry.Rect) -> Unit = { itemId, row, bounds ->
        val current = homeMenuAnchors[itemId]
        if (current != null && current.row == row && current.boundsInWindow == bounds) {
            Unit
        } else {
            homeMenuAnchors = homeMenuAnchors.toMutableMap().apply {
                put(itemId, HomeMenuAnchor(row = row, boundsInWindow = bounds))
            }
        }
    }
    val updateDockMenuAnchor: (String, androidx.compose.ui.geometry.Rect) -> Unit = { itemId, bounds ->
        val current = dockMenuAnchors[itemId]
        if (current != null && current.boundsInWindow == bounds) {
            Unit
        } else {
            dockMenuAnchors = dockMenuAnchors.toMutableMap().apply {
                put(itemId, HomeMenuAnchor(row = 0, boundsInWindow = bounds))
            }
        }
    }
    val updateFolderMenuAnchor: (String, androidx.compose.ui.geometry.Rect) -> Unit = { itemId, bounds ->
        val current = folderMenuAnchors[itemId]
        if (current != null && current.boundsInWindow == bounds) {
            Unit
        } else {
            folderMenuAnchors = folderMenuAnchors.toMutableMap().apply {
                put(itemId, HomeMenuAnchor(row = 0, boundsInWindow = bounds))
            }
        }
    }
    val moveAppIntoFolder: (LauncherApp, String, String?) -> Unit = { app, destinationFolderId, sourceFolderId ->
        val sourceDockIndex = dockSlots.indexOf(app.packageName)
        val sourceVisibleGridIndex = if (sourceFolderId == null && sourceDockIndex < 0) {
            visibleGridTargetsForState(gridOrder, folders, dockSlots).indexOf(app.packageName)
        } else {
            -1
        }
        val updatedFolders = folders.map { folder ->
            when {
                folder.id == sourceFolderId -> folder.copy(
                    appPackageNames = folder.appPackageNames.filterNot { it == app.packageName }
                )
                folder.id == destinationFolderId -> folder.copy(
                    appPackageNames = (folder.appPackageNames + app.packageName).distinct()
                )
                else -> folder
            }
        }
        val gridWithoutApp = gridOrder.filterNot { it == app.packageName }
        val (cleanFolders, cleanGrid) = cleanupEmptyFolders(updatedFolders, gridWithoutApp)
        persistFolders(cleanFolders)
        val updatedDock = rebalanceDockAfterRemovingApp(app.packageName, cleanGrid)
        if (cleanGrid != gridOrder) {
            gridOrder = cleanGrid
            saveGridOrder(prefs, cleanGrid)
        }
        if (sourceFolderId != null && openFolderId == sourceFolderId) {
            val nextFocus = cleanFolders.firstOrNull { it.id == sourceFolderId }
                ?.appPackageNames
                ?.lastOrNull()
            openFolderFocusTarget = nextFocus
            openFolderFocusToken++
            if (nextFocus == null) {
                openFolderId = null
            }
        } else {
            val replacementFocusTarget = when {
                sourceDockIndex >= 0 -> {
                    updatedDock.getOrNull(sourceDockIndex)
                        ?: updatedDock.drop(sourceDockIndex + 1).firstOrNull { it != null }
                        ?: updatedDock.take(sourceDockIndex).lastOrNull { it != null }
                }
                sourceVisibleGridIndex >= 0 -> {
                    val updatedVisibleTargets = visibleGridTargetsForState(cleanGrid, cleanFolders, updatedDock)
                    when {
                        sourceVisibleGridIndex < updatedVisibleTargets.size -> updatedVisibleTargets[sourceVisibleGridIndex]
                        updatedVisibleTargets.isNotEmpty() -> updatedVisibleTargets.last()
                        else -> null
                    }
                }
                else -> null
            }
            focusRestoreTarget = replacementFocusTarget ?: destinationFolderId
            focusRestoreToken++
            val targetInDock = focusRestoreTarget != null && updatedDock.contains(focusRestoreTarget)
            suppressDockFocus = !targetInDock
            if (suppressDockFocus) {
                dockSuppressToken++
            }
        }
    }
    val moveFolderAppToHomeScreen: (LauncherApp, String) -> Unit = { app, sourceFolderId ->
        val sourceFolder = folders.firstOrNull { it.id == sourceFolderId }
        val removedAppIndex = sourceFolder?.appPackageNames?.indexOf(app.packageName) ?: -1
        val updatedFolders = folders.map { folder ->
            if (folder.id == sourceFolderId) {
                folder.copy(appPackageNames = folder.appPackageNames.filterNot { it == app.packageName })
            } else {
                folder
            }
        }
        val updatedGrid = gridOrder.toMutableList().apply {
            removeAll { it == app.packageName }
            add(app.packageName)
        }
        val (cleanFolders, cleanGrid) = cleanupEmptyFolders(updatedFolders, updatedGrid)
        persistFolders(cleanFolders)
        gridOrder = cleanGrid
        saveGridOrder(prefs, cleanGrid)
        val remainingFolderPackages = cleanFolders.firstOrNull { it.id == sourceFolderId }
            ?.appPackageNames
            .orEmpty()
        val remainingFolderFocusTarget = when {
            remainingFolderPackages.isEmpty() -> null
            removedAppIndex < 0 -> remainingFolderPackages.lastOrNull()
            removedAppIndex < remainingFolderPackages.size -> remainingFolderPackages[removedAppIndex]
            else -> remainingFolderPackages.lastOrNull()
        }
        if (remainingFolderFocusTarget == null) {
            openFolderId = null
            focusRestoreTarget = app.packageName
            focusRestoreToken++
            suppressDockFocus = true
            dockSuppressToken++
        } else {
            openFolderFocusTarget = remainingFolderFocusTarget
            openFolderFocusToken++
        }
    }
    val createFolderFromApp: (LauncherApp, String?) -> String = { app, sourceFolderId ->
        val newFolderId = generateFolderId()
        val sourceIndex = when {
            sourceFolderId != null -> gridOrder.indexOf(sourceFolderId).takeIf { it >= 0 } ?: gridOrder.size
            else -> gridOrder.indexOf(app.packageName).takeIf { it >= 0 } ?: gridOrder.size
        }
        val updatedFolders = buildList {
            folders.forEach { folder ->
                add(
                    if (folder.id == sourceFolderId) {
                        folder.copy(appPackageNames = folder.appPackageNames.filterNot { it == app.packageName })
                    } else {
                        folder
                    }
                )
            }
            add(
                LauncherFolder(
                    id = newFolderId,
                    name = DEFAULT_FOLDER_NAME,
                    appPackageNames = listOf(app.packageName),
                )
            )
        }
        val updatedGrid = removeFolderFromGridOrder(gridOrder.filterNot { it == app.packageName }, newFolderId).toMutableList().apply {
            val insertIndex = sourceIndex.coerceAtMost(size)
            add(insertIndex, newFolderId)
        }
        val (cleanFolders, cleanGrid) = cleanupEmptyFolders(updatedFolders, updatedGrid)
        persistFolders(cleanFolders)
        rebalanceDockAfterRemovingApp(app.packageName, cleanGrid)
        gridOrder = cleanGrid
        saveGridOrder(prefs, cleanGrid)
        newFolderId
    }
    val moveAppWithinFolder: (String, String, Int) -> Unit = { folderId, appPackageName, targetIndex ->
        val updatedFolders = folders.map { folder ->
            if (folder.id == folderId) {
                val reorderedPackages = folder.appPackageNames.toMutableList()
                val currentIndex = reorderedPackages.indexOf(appPackageName)
                if (currentIndex >= 0) {
                    reorderedPackages.removeAt(currentIndex)
                    val insertIndex = targetIndex.coerceIn(0, reorderedPackages.size)
                    reorderedPackages.add(insertIndex, appPackageName)
                }
                folder.copy(appPackageNames = reorderedPackages)
            } else {
                folder
            }
        }
        folders = updatedFolders
        saveFolders(prefs, updatedFolders)
        openFolderFocusTarget = appPackageName
        openFolderFocusToken++
    }
    val refreshAppsState = rememberUpdatedState<(Boolean) -> Job> { force ->
        coroutineScope.launch {
            val now = SystemClock.elapsedRealtime()
            val shouldSkipRefresh = !force &&
                    allApps.isNotEmpty() &&
                    now - lastAppsRefreshElapsed < APP_REFRESH_MIN_INTERVAL_MS
            if (shouldSkipRefresh) {
                return@launch
            }
            val loadedApps = withContext(Dispatchers.IO) { loadInstalledApps(context) }
            allApps = loadedApps
            lastAppsRefreshElapsed = now
            withContext(Dispatchers.IO) {
                saveCachedApps(prefs, loadedApps)
            }
        }
    }
    val packageRefreshJob = remember { mutableStateOf<Job?>(null) }

    val backgroundPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            persistReadPermission(context, uri)
            coroutineScope.launch {
                val importedUri = withContext(Dispatchers.IO) {
                    importBackgroundUri(context, uri)
                } ?: uri
                prefs.edit().putString(KEY_BACKGROUND_URI, importedUri.toString()).apply()
                prefs.edit().remove(KEY_BACKGROUND_PRESET).apply()
                backgroundUri = importedUri.toString()
                backgroundPresetId = null
            }
        }
    }

    val iconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val appTarget = iconPickerTarget
        val inputTarget = inputIconPickerTarget
        if (uri != null && appTarget != null) {
            persistReadPermission(context, uri)
            prefs.edit().putString(KEY_ICON_OVERRIDE_PREFIX + appTarget.packageName, uri.toString()).apply()
            iconOverrides = iconOverrides + (appTarget.packageName to uri.toString())
        } else if (uri != null && inputTarget != null) {
            persistReadPermission(context, uri)
            val key = inputIconOverrideKey(inputTarget.id)
            prefs.edit().putString(KEY_ICON_OVERRIDE_PREFIX + key, uri.toString()).apply()
            iconOverrides = iconOverrides + (key to uri.toString())
        }
        iconPickerTarget = null
        inputIconPickerTarget = null
    }

    LaunchedEffect(Unit) {
        refreshAppsState.value(true).join()
    }
    LaunchedEffect(Unit) {
        withFrameNanos { }
        delay(300)
        inputSources = withContext(Dispatchers.IO) { loadDeviceInputs(context) }
    }
    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context, intent: Intent) {
                if (intent.data?.scheme != "package") return

                packageRefreshJob.value?.cancel()
                packageRefreshJob.value = coroutineScope.launch {
                    // Package managers commonly emit multiple events for one install/update.
                    delay(500)
                    refreshAppsState.value(true).join()
                    packageRefreshJob.value = null
                }
            }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }

        onDispose {
            packageRefreshJob.value?.cancel()
            context.unregisterReceiver(receiver)
        }
    }

    LaunchedEffect(activeBackgroundUri) {
        backgroundBitmap = withContext(Dispatchers.IO) {
            activeBackgroundUri?.let { uriString ->
                loadBackgroundBitmapFromUri(context, Uri.parse(uriString))?.asImageBitmap()
            }
        }
    }

    LaunchedEffect(allApps, hiddenPackages, folders) {
        if (allApps.isEmpty()) return@LaunchedEffect
        val isFirstLaunch = !prefs.contains(KEY_DOCK) && !prefs.contains(KEY_GRID_ORDER)
        if (isFirstLaunch) {
            val visible = allApps.filter { !hiddenPackages.contains(it.packageName) }
            val ordered = visible.sortedWith(
                compareBy<LauncherApp> { it.label.lowercase() }
                    .thenBy { it.packageName }
            )
            val dockSeed = ordered.take(DOCK_SIZE).map { it.packageName }
            val dock = if (dockSeed.size < DOCK_SIZE) {
                dockSeed + List(DOCK_SIZE - dockSeed.size) { null }
            } else {
                dockSeed
            }
            val grid = ordered.map { it.packageName }
            dockSlots = dock
            gridOrder = grid
            folders = emptyList()
            saveDockSlots(prefs, dock)
            saveGridOrder(prefs, grid)
            saveFolders(prefs, emptyList())
        } else {
            val normalizedFolders = normalizeFolders(
                current = folders,
                apps = allApps,
                hiddenPackages = hiddenPackages,
            )
            if (normalizedFolders != folders) {
                folders = normalizedFolders
                saveFolders(prefs, normalizedFolders)
            }
            val normalized = normalizeDockSlots(
                currentDock = dockSlots,
                apps = allApps,
                hiddenPackages = hiddenPackages,
                folders = normalizedFolders,
            )
            if (normalized != dockSlots) {
                dockSlots = normalized
                saveDockSlots(prefs, normalized)
            }

            val normalizedGrid = normalizeGridOrder(
                current = gridOrder,
                apps = allApps,
                hiddenPackages = hiddenPackages,
                folders = normalizedFolders,
            )
            if (normalizedGrid != gridOrder) {
                gridOrder = normalizedGrid
                saveGridOrder(prefs, normalizedGrid)
            }
        }
    }

    val allVisibleApps = remember(allApps, hiddenPackages) {
        allApps.filter { !hiddenPackages.contains(it.packageName) }
    }
    val appsByPackage = remember(allVisibleApps) {
        allVisibleApps.associateBy { it.packageName }
    }
    val foldersById = remember(folders) {
        folders.associateBy { it.id }
    }
    val packagesInFolders = remember(folders) {
        folderPackages(folders)
    }
    val backgroundPreset = remember(
        backgroundPresetId,
        backgroundCycleStep,
        lightModePresetId,
        darkModePresetId,
        darkModeEnabled,
    ) {
        val perModeId = if (darkModeEnabled) darkModePresetId else lightModePresetId
        val effectiveId = perModeId ?: backgroundPresetId
        if (effectiveId == BACKGROUND_PRESET_CYCLE_ALL) {
            val cyclePresets = cycleableBackgroundPresets()
            cyclePresets.getOrNull((backgroundCycleStep % cyclePresets.size.coerceAtLeast(1)).toInt())
        } else {
            findBackgroundPreset(effectiveId)
        }
    }
    val visibleGridItemIds = remember(gridOrder, dockSlots, appsByPackage, foldersById, packagesInFolders) {
        val docked = dockSlots.filterNotNull().toSet()
        gridOrder.filter { itemId ->
            if (isFolderId(itemId)) {
                foldersById.containsKey(itemId) && !docked.contains(itemId)
            } else {
                appsByPackage.containsKey(itemId) &&
                        !docked.contains(itemId) &&
                        !packagesInFolders.contains(itemId)
            }
        }
    }
    val columnByItemId = remember(visibleGridItemIds) {
        visibleGridItemIds.withIndex().associate { it.value to (it.index % 6) }
    }
    val visibleApps = remember(visibleGridItemIds, appsByPackage) {
        visibleGridItemIds.mapNotNull { itemId ->
            itemId.takeUnless(::isFolderId)?.let { appsByPackage[it] }
        }
    }
    val eagerArtworkPackages = remember(
        dockSlots,
        visibleGridItemIds,
        foldersById,
        appsByPackage,
        focusedGridRow,
    ) {
        buildSet {
            dockSlots.filterNotNullTo(this)
            val centerRow = focusedGridRow ?: 0
            gridItemIdsForRowRange(visibleGridItemIds, centerRow, rowRadius = 2).forEach { itemId ->
                if (isFolderId(itemId)) {
                    foldersById[itemId]
                        ?.appPackageNames
                        ?.take(4)
                        ?.forEach { add(it) }
                } else {
                    add(itemId)
                }
            }
        }
    }
    val prefetchArtworkForRow: (Int) -> Unit = { row ->
        coroutineScope.launch(Dispatchers.IO) {
            prefetchArtworkForGridItemIds(
                context = context,
                itemIds = gridItemIdsForRowRange(visibleGridItemIds, row, rowRadius = 2),
                appsByPackage = appsByPackage,
                foldersById = foldersById,
            )
        }
    }
    val previewPrefetchApps = remember(visibleGridItemIds, foldersById, appsByPackage) {
        visibleGridItemIds.take(18)
            .flatMap { itemId ->
                if (isFolderId(itemId)) {
                    foldersById[itemId]
                        ?.appPackageNames
                        ?.take(4)
                        .orEmpty()
                } else {
                    listOf(itemId)
                }
            }
            .mapNotNull { appsByPackage[it] }
    }
    val inputSourcesById = remember(inputSources) {
        inputSources.associateBy { it.id }
    }
    val visibleInputSources = remember(inputSources, hiddenInputs) {
        inputSources.filterNot { hiddenInputs.contains(it.id) }
    }
    val visibleRowFocusTargets = remember(visibleGridItemIds, visibleInputSources) {
        buildList {
            addAll(visibleGridItemIds)
            addAll(visibleInputSources.map { inputIconOverrideKey(it.id) })
        }
    }
    val fallbackFocusTargetForHiddenItem: (String) -> String? =
        remember(dockSlots, visibleGridItemIds, visibleRowFocusTargets) {
            { itemKey ->
                val dockIndex = dockSlots.indexOf(itemKey)
                if (dockIndex >= 0) {
                    (dockIndex - 1 downTo 0)
                        .mapNotNull { index -> dockSlots.getOrNull(index) }
                        .firstOrNull()
                        ?: ((dockIndex + 1) until dockSlots.size)
                            .mapNotNull { index -> dockSlots.getOrNull(index) }
                            .firstOrNull()
                        ?: visibleRowFocusTargets.lastOrNull()
                } else {
                    val rowIndex = visibleRowFocusTargets.indexOf(itemKey)
                    if (rowIndex >= 0) {
                        visibleRowFocusTargets.getOrNull(rowIndex - 1)
                            ?: visibleRowFocusTargets.getOrNull(rowIndex + 1)
                            ?: dockSlots.filterNotNull().lastOrNull()
                    } else {
                        null
                    }
                }
            }
        }
    LaunchedEffect(focusRestoreTarget, visibleRowFocusTargets, dockSlots, openFolderId) {
        val target = focusRestoreTarget ?: return@LaunchedEffect
        if (openFolderId != null) return@LaunchedEffect
        if (dockSlots.contains(target)) return@LaunchedEffect
        val targetIndex = visibleRowFocusTargets.indexOf(target)
        if (targetIndex < 0) return@LaunchedEffect
        focusedGridRow = targetIndex / 6
        dockHasFocus = false
        drawerHasFocus = true
    }
    val rowItems = remember(visibleGridItemIds, appsByPackage, foldersById, visibleInputSources) {
        buildList<RowItem> {
            visibleGridItemIds.forEach { itemId ->
                if (isFolderId(itemId)) {
                    foldersById[itemId]?.let { folder ->
                        add(
                            RowItem.Folder(
                                folder = folder,
                                apps = folder.appPackageNames.mapNotNull { appsByPackage[it] },
                            )
                        )
                    }
                } else {
                    appsByPackage[itemId]?.let { add(RowItem.App(it)) }
                }
            }
            visibleInputSources.forEach { add(RowItem.Input(it)) }
        }
    }
    val totalGridRows = remember(rowItems) {
        if (rowItems.isEmpty()) 0 else ((rowItems.size - 1) / 6) + 1
    }
    val minimumViewportRows = 3
    val extraViewportRows = (minimumViewportRows - totalGridRows).coerceAtLeast(0)
    val effectiveGridBottomPadding = GridBottomPadding + (appRowBlockHeight * extraViewportRows)
    LaunchedEffect(totalGridRows, dockHasFocus, openFolderId) {
        if (dockHasFocus || openFolderId != null) return@LaunchedEffect
        val currentRow = focusedGridRow ?: return@LaunchedEffect
        val maxValidRow = (totalGridRows - 1).coerceAtLeast(0)
        focusedGridRow = if (totalGridRows == 0) null else currentRow.coerceAtMost(maxValidRow)
    }
    LaunchedEffect(rowItems) {
        val validIds = rowItems.mapTo(mutableSetOf()) { item ->
            when (item) {
                is RowItem.App -> item.app.packageName
                is RowItem.Folder -> item.folder.id
                is RowItem.Input -> inputIconOverrideKey(item.source.id)
            }
        }
        homeMenuAnchors = homeMenuAnchors.filterKeys(validIds::contains)
    }
    LaunchedEffect(openFolderId, foldersById, allApps) {
        val validIds = openFolderId
            ?.let(foldersById::get)
            ?.appPackageNames
            ?.toSet()
            .orEmpty()
        folderMenuAnchors = folderMenuAnchors.filterKeys(validIds::contains)
    }
    val installedPackages = remember(allApps) {
        allApps.map { it.packageName }.toSet()
    }

    val moveModeActive = moveModeItemId != null
    LaunchedEffect(backgroundPresetId) {
        if (backgroundPresetId != BACKGROUND_PRESET_CYCLE_ALL) return@LaunchedEffect
        while (backgroundPresetId == BACKGROUND_PRESET_CYCLE_ALL) {
            delay(30_000)
            backgroundCycleStep += 1L
        }
    }
    LaunchedEffect(installedPackages, foldersById, openFolderId) {
        val validHiddenPackages = hiddenPackages.filterTo(mutableSetOf()) { installedPackages.contains(it) }
        if (validHiddenPackages != hiddenPackages) {
            hiddenPackages = validHiddenPackages
            saveHiddenPackages(prefs, validHiddenPackages)
        }

        if (appOptionsTarget?.app?.packageName?.let { !installedPackages.contains(it) } == true) {
            appOptionsTarget = null
        }
        if (folderAppOptionsTarget?.app?.packageName?.let { !installedPackages.contains(it) } == true) {
            folderAppOptionsTarget = null
        }
        if (moveAppRequest?.app?.packageName?.let { !installedPackages.contains(it) } == true) {
            moveAppRequest = null
        }
        if (moveDockTarget?.packageName?.let { !installedPackages.contains(it) } == true) {
            moveDockTarget = null
        }
        if (iconPickerTarget?.packageName?.let { !installedPackages.contains(it) } == true) {
            iconPickerTarget = null
        }
        if (renameDialogTarget?.packageName?.let { !installedPackages.contains(it) } == true) {
            renameDialogTarget = null
            renameDialogInput = ""
        }
        val activeMoveItemId = moveModeItemId
        if (activeMoveItemId != null &&
            ((isFolderId(activeMoveItemId) && !foldersById.containsKey(activeMoveItemId)) ||
                    (!isFolderId(activeMoveItemId) && !installedPackages.contains(activeMoveItemId)))
        ) {
            moveModeItemId = null
            pendingDockFocusIndex = null
            moveGridIndexOverride = null
        }
        val previousMoveItemId = lastMoveItemId
        if (previousMoveItemId != null &&
            ((isFolderId(previousMoveItemId) && !foldersById.containsKey(previousMoveItemId)) ||
                    (!isFolderId(previousMoveItemId) && !installedPackages.contains(previousMoveItemId)))
        ) {
            lastMoveItemId = null
        }
        if (openFolderId != null && !foldersById.containsKey(openFolderId)) {
            openFolderId = null
            folderOptionsTargetId = null
        }
        val activeFolderMovePackage = folderMoveModePackage
        if (
            activeFolderMovePackage != null &&
            openFolderId?.let(foldersById::get)?.appPackageNames?.contains(activeFolderMovePackage) != true
        ) {
            folderMoveModePackage = null
        }
        if (focusRestoreTarget?.startsWith("input:") == false &&
            focusRestoreTarget?.let {
                if (isFolderId(it)) {
                    !foldersById.containsKey(it)
                } else {
                    !installedPackages.contains(it)
                }
            } == true
        ) {
            focusRestoreTarget = null
        }
    }

    LaunchedEffect(allApps) {
        if (allApps.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        delay(350)
        deferredArtworkLoadEnabled = true
    }

    LaunchedEffect(drawerHasFocus) {
        if (drawerHasFocus) {
            deferredArtworkLoadEnabled = true
        }
    }

    LaunchedEffect(focusedGridRow, drawerHasFocus) {
        if (!drawerHasFocus) return@LaunchedEffect
        prefetchArtworkForRow(focusedGridRow ?: 0)
    }

    LaunchedEffect(previewPrefetchApps) {
        if (previewPrefetchApps.isEmpty()) return@LaunchedEffect
        withFrameNanos { }
        delay(120)
        withContext(Dispatchers.IO) {
            previewPrefetchApps.forEach { app ->
                if (getCachedAppArtwork(app) == null) {
                    loadAppArtwork(context, app)
                }
            }
        }
    }

    LaunchedEffect(moveModeActive, moveModeItemId) {
        if (!moveModeActive) return@LaunchedEffect
        pendingDockFocusIndex = null
        if (moveModeItemId != null) {
            focusRestoreTarget = moveModeItemId
            focusRestoreToken++
        }
    }

    var moveFocusToken by remember { mutableStateOf(0) }
    LaunchedEffect(moveModeActive, moveModeItemId, moveFocusToken) {
        if (!moveModeActive) return@LaunchedEffect
        moveModeItemId?.let {
            focusRestoreTarget = it
            focusRestoreToken++
        }
    }

    LaunchedEffect(moveModeItemId, visibleGridItemIds) {
        val moving = moveModeItemId ?: return@LaunchedEffect
        moveGridIndexOverride = visibleGridItemIds.indexOf(moving).takeIf { it >= 0 }
    }

    val handleMoveFocus: (MoveTarget) -> Unit = moveFocus@{ target ->
        val movingItemId = moveModeItemId ?: return@moveFocus
        when (target) {
            is MoveTarget.Grid -> {
                val movingDockIndex = dockSlots.indexOf(movingItemId)
                if (movingDockIndex >= 0 && pendingDockFocusIndex != null) {
                    // Ignore grid focus changes while we're pending a dock focus jump.
                    return@moveFocus
                }
                if (movingDockIndex < 0 && target.itemId == movingItemId) return@moveFocus
                val movingIndex = gridOrder.indexOf(movingItemId)
                val newGrid = gridOrder.toMutableList()
                if (movingDockIndex >= 0) {
                    // Move from dock into grid: shift dock left and pull first visible grid item into dock end.
                    val newDock = dockSlots.toMutableList()
                    for (i in movingDockIndex until newDock.lastIndex) {
                        newDock[i] = newDock[i + 1]
                    }
                    newDock[newDock.lastIndex] = null

                    newGrid.removeAll { it == movingItemId }
                    val nextDockItem = newGrid.firstOrNull { itemId ->
                        itemId != movingItemId && !newDock.contains(itemId)
                    }
                    if (nextDockItem != null) {
                        newGrid.removeAll { it == nextDockItem }
                        newDock[newDock.lastIndex] = nextDockItem
                    }
                    val insertAt = visibleIndexToOrderIndex(
                        order = newGrid,
                        docked = newDock.filterNotNull().toSet(),
                        visibleIndex = target.index,
                    )
                    newGrid.add(insertAt, movingItemId)

                    val deduped = dedupeGridOrder(newGrid)
                    gridOrder = deduped
                    saveGridOrder(prefs, deduped)
                    dockSlots = newDock
                    saveDockSlots(prefs, newDock)
                    moveGridIndexOverride = target.index
                    focusRestoreTarget = movingItemId
                    focusRestoreToken++
                    moveFocusToken++
                } else if (movingIndex >= 0) {
                    newGrid.removeAll { it == movingItemId }
                    val insertAt = visibleIndexToOrderIndex(
                        order = newGrid,
                        docked = dockSlots.filterNotNull().toSet(),
                        visibleIndex = target.index,
                    )
                    newGrid.add(insertAt, movingItemId)
                    val deduped = dedupeGridOrder(newGrid)
                    gridOrder = deduped
                    saveGridOrder(prefs, deduped)
                    moveGridIndexOverride = target.index
                    focusRestoreTarget = movingItemId
                    focusRestoreToken++
                    moveFocusToken++
                }
            }
            is MoveTarget.Dock -> {
                if (target.itemId == movingItemId) return@moveFocus
                val movingDockIndex = dockSlots.indexOf(movingItemId)
                val newDock = dockSlots.toMutableList()
                if (movingDockIndex >= 0) {
                    val pendingIndex = pendingDockFocusIndex
                    if (pendingIndex != null && target.index != pendingIndex) return@moveFocus
                    if (pendingIndex != null && target.index == pendingIndex) {
                        pendingDockFocusIndex = null
                    }
                    if (movingDockIndex == target.index) return@moveFocus
                    newDock[movingDockIndex] = target.itemId
                    newDock[target.index] = movingItemId
                    dockSlots = newDock
                    saveDockSlots(prefs, newDock)
                    focusRestoreTarget = movingItemId
                    focusRestoreToken++
                    moveFocusToken++
                } else {
                    val targetIndex = (columnByItemId[movingItemId] ?: target.index)
                        .coerceIn(0, DOCK_SIZE - 1)
                    val newGrid = gridOrder.toMutableList()
                    newGrid.removeAll { it == movingItemId }
                    moveGridIndexOverride = null

                    val lastDock = newDock.lastOrNull()
                    for (i in newDock.lastIndex downTo targetIndex + 1) {
                        newDock[i] = newDock[i - 1]
                    }
                    newDock[targetIndex] = movingItemId
                    dockSlots = newDock
                    saveDockSlots(prefs, newDock)
                    pendingDockFocusIndex = targetIndex

                    if (lastDock != null && lastDock != movingItemId) {
                        newGrid.removeAll { it == lastDock }
                        val insertAt = visibleIndexToOrderIndex(
                            order = newGrid,
                            docked = newDock.filterNotNull().toSet(),
                            visibleIndex = 0,
                        )
                        newGrid.add(insertAt, lastDock)
                    }

                    val deduped = dedupeGridOrder(newGrid)
                    gridOrder = deduped
                    saveGridOrder(prefs, deduped)
                    focusRestoreTarget = movingItemId
                    focusRestoreToken++
                    moveFocusToken++
                }
            }
        }
    }

    LaunchedEffect(moveModeActive, pendingDockFocusIndex) {
        if (!moveModeActive) return@LaunchedEffect
        val targetIndex = pendingDockFocusIndex ?: return@LaunchedEffect
        if (gridState.firstVisibleItemIndex != 0 || gridState.firstVisibleItemScrollOffset != 0) {
            gridState.animateScrollToItem(0, 0)
            viewportOffsetPx = 0f
            viewportOffsetAnimation.snapTo(0f)
        }
        dockItemRequesters.getOrNull(targetIndex)?.requestFocus()
    }

    LaunchedEffect(moveModeActive, lastMoveItemId, dockSlots) {
        if (moveModeActive) return@LaunchedEffect
        val itemId = lastMoveItemId ?: return@LaunchedEffect
        val dockIndex = dockSlots.indexOf(itemId)
        if (dockIndex >= 0) {
            dockItemRequesters.getOrNull(dockIndex)?.requestFocus()
        }
        lastMoveItemId = null
    }

    LaunchedEffect(dockSuppressToken) {
        if (!suppressDockFocus) return@LaunchedEffect
        // Avoid visible dock flashes but don't stall focus highlights for long.
        delay(140)
        if (!moveModeActive) {
            suppressDockFocus = false
        }
    }

    LaunchedEffect(moveModeActive) {
        if (moveModeActive) {
            suppressDockFocus = false
        }
    }

    val handleMoveNavigateFromDock: (Int, MoveDirection) -> Unit = moveNavigateDock@{ dockIndex, direction ->
        if (!moveModeActive) return@moveNavigateDock
        val movingItemId = moveModeItemId ?: return@moveNavigateDock
        if (dockIndex !in 0 until DOCK_SIZE) return@moveNavigateDock
        if (pendingDockFocusIndex != null) {
            // We've landed in the dock; allow lateral moves now.
            pendingDockFocusIndex = null
        }
        when (direction) {
            MoveDirection.Left -> {
                val targetIndex = dockIndex - 1
                if (targetIndex >= 0) {
                    handleMoveFocus(MoveTarget.Dock(targetIndex, dockSlots.getOrNull(targetIndex)))
                }
            }
            MoveDirection.Right -> {
                val targetIndex = dockIndex + 1
                if (targetIndex < DOCK_SIZE) {
                    handleMoveFocus(MoveTarget.Dock(targetIndex, dockSlots.getOrNull(targetIndex)))
                }
            }
            MoveDirection.Down -> {
                val targetItemId = visibleGridItemIds.getOrNull(dockIndex) ?: return@moveNavigateDock
                handleMoveFocus(MoveTarget.Grid(dockIndex, targetItemId))
            }
            MoveDirection.Up -> Unit
        }
    }

    val handleMoveNavigateFromGrid: (String, MoveDirection) -> Unit = moveNavigateGrid@{ itemId, direction ->
        if (!moveModeActive) return@moveNavigateGrid
        val index = if (itemId == moveModeItemId) {
            moveGridIndexOverride ?: visibleGridItemIds.indexOf(itemId)
        } else {
            visibleGridItemIds.indexOf(itemId)
        }
        if (index == -1) return@moveNavigateGrid
        val column = index % 6
        val row = index / 6
        val maybeScrollToMoveIndex: (Int, Int) -> Unit = { targetIndex, currentIndex ->
            coroutineScope.launch {
                val gridIndex = targetIndex + 1 // account for header item
                val layoutInfo = gridState.layoutInfo
                val viewportStart = layoutInfo.viewportStartOffset
                val viewportEnd = layoutInfo.viewportEndOffset - gridBottomInsetPx.toInt()
                val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == gridIndex }
                val needsScroll = itemInfo == null ||
                        itemInfo.offset.y < viewportStart ||
                        itemInfo.offset.y + itemInfo.size.height > viewportEnd
                if (needsScroll) {
                    if (targetIndex < currentIndex) {
                        gridState.scrollToItem(gridIndex)
                    } else {
                        gridState.animateScrollToItem(gridIndex)
                    }
                }
            }
        }
        when (direction) {
            MoveDirection.Left -> {
                if (column > 0) {
                    val targetIndex = index - 1
                    val targetItemId = visibleGridItemIds.getOrNull(targetIndex) ?: return@moveNavigateGrid
                    handleMoveFocus(MoveTarget.Grid(targetIndex, targetItemId))
                    maybeScrollToMoveIndex(targetIndex, index)
                }
            }
            MoveDirection.Right -> {
                if (column < 5) {
                    val targetIndex = index + 1
                    val targetItemId = visibleGridItemIds.getOrNull(targetIndex) ?: return@moveNavigateGrid
                    handleMoveFocus(MoveTarget.Grid(targetIndex, targetItemId))
                    maybeScrollToMoveIndex(targetIndex, index)
                }
            }
            MoveDirection.Up -> {
                if (row > 0) {
                    val layoutInfo = gridState.layoutInfo
                    val targetIndex = index - 6
                    val viewportStart = layoutInfo.viewportStartOffset
                    val viewportEnd = layoutInfo.viewportEndOffset - gridBottomInsetPx.toInt()
                    val targetInfo = layoutInfo.visibleItemsInfo
                        .firstOrNull { it.index == targetIndex + 1 }
                    val visibleRatio = targetInfo?.let { info ->
                        val top = maxOf(info.offset.y, viewportStart)
                        val bottom = minOf(info.offset.y + info.size.height, viewportEnd)
                        val visibleHeight = (bottom - top).coerceAtLeast(0)
                        visibleHeight.toFloat() / info.size.height.toFloat()
                    } ?: 0f
                    val dockVisible = gridState.firstVisibleItemIndex == 0
                    val shouldDockFromTopVisibleRow = dockVisible && visibleRatio < 0.5f
                    if (shouldDockFromTopVisibleRow) {
                        handleMoveFocus(MoveTarget.Dock(column, dockSlots.getOrNull(column)))
                    } else {
                        val targetItemId = visibleGridItemIds.getOrNull(targetIndex) ?: return@moveNavigateGrid
                        handleMoveFocus(MoveTarget.Grid(targetIndex, targetItemId))
                        maybeScrollToMoveIndex(targetIndex, index)
                    }
                } else {
                    handleMoveFocus(MoveTarget.Dock(column, dockSlots.getOrNull(column)))
                }
            }
            MoveDirection.Down -> {
                val targetIndex = index + 6
                val targetItemId = visibleGridItemIds.getOrNull(targetIndex) ?: return@moveNavigateGrid
                handleMoveFocus(MoveTarget.Grid(targetIndex, targetItemId))
                maybeScrollToMoveIndex(targetIndex, index)
            }
        }
    }

    val dockItems = remember(dockSlots, appsByPackage, foldersById) {
        dockSlots.map { itemId ->
            when {
                itemId == null -> null
                isFolderId(itemId) -> foldersById[itemId]?.let { folder ->
                    DockItem.Folder(
                        folder = folder,
                        apps = folder.appPackageNames.mapNotNull { appsByPackage[it] },
                    )
                }
                else -> appsByPackage[itemId]?.let { DockItem.App(it) }
            }
        }
    }
    LaunchedEffect(dockItems) {
        val validIds = dockItems.mapNotNullTo(mutableSetOf()) { it?.id }
        dockMenuAnchors = dockMenuAnchors.filterKeys(validIds::contains)
    }

    LaunchedEffect(homeResetToken, dockItems, homeResetFromBoot, homeResetFromLauncher) {
        if (homeResetToken == 0L) return@LaunchedEffect

        showLauncherSettings = false
        showAppearanceSettings = false
        showHomeButtonTakeoverSettings = false
        showBackgroundMenu = false
        showBackgroundPresets = false
        showAppsSettings = false
        showBackgroundUrlDialog = false
        backgroundUrlLoading = false
        backgroundModePresetTarget = null
        backgroundModeMenuAnchor = null
        appOptionsTarget = null
        folderAppOptionsTarget = null
        moveAppRequest = null
        folderOptionsTargetId = null
        openFolderId = null
        requestInlineRename = false
        inputOptionsTarget = null
        moveDockTarget = null
        moveModeItemId = null
        folderMoveModePackage = null
        pendingDockFocusIndex = null
        renameDialogTarget = null
        renameDialogInput = ""
        suppressDockFocus = false
        focusManager.clearFocus(force = true)
        dockHasFocus = true
        drawerHasFocus = false
        focusedGridRow = null
        showDockChevron = false

        if (gridState.firstVisibleItemIndex != 0 || gridState.firstVisibleItemScrollOffset != 0) {
            gridState.animateScrollToItem(0, 0)
        }
        viewportOffsetPx = 0f
        viewportOffsetAnimation.snapTo(0f)

        defaultDockFocusTarget(dockItems)?.let { targetId ->
            focusRestoreTarget = targetId
            focusRestoreToken++
            dockHasFocus = true
            drawerHasFocus = false
            val dockIndex = dockItems.indexOfFirst { it?.id == targetId }
            if (dockIndex >= 0) {
                withFrameNanos { }
                withFrameNanos { }
                dockItemRequesters.getOrNull(dockIndex)?.requestFocus()
                withFrameNanos { }
                dockItemRequesters.getOrNull(dockIndex)?.requestFocus()
            }
        }
        initialDockFocusSet = true
    }

    LaunchedEffect(dockItems, showLauncherSettings, appOptionsTarget, moveDockTarget, homeResetToken) {
        if (initialDockFocusSet) return@LaunchedEffect
        if (homeResetToken != 0L) return@LaunchedEffect
        if (showLauncherSettings || appOptionsTarget != null || moveDockTarget != null) return@LaunchedEffect
        defaultDockFocusTarget(dockItems)?.let { targetId ->
            focusRestoreTarget = targetId
            focusRestoreToken++
            initialDockFocusSet = true
        }
    }

    LaunchedEffect(dockHasFocus, dockInteractionToken, rowItems.size) {
        if (!dockHasFocus || rowItems.isEmpty()) {
            showDockChevron = false
            return@LaunchedEffect
        }
        showDockChevron = false
        val token = dockInteractionToken
        delay(5_000)
        if (dockHasFocus && dockInteractionToken == token && rowItems.isNotEmpty()) {
            showDockChevron = true
        }
    }
    val scrimAlpha = 0f

    val bringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(
                offset: Float,
                size: Float,
                containerSize: Float
            ): Float = 0f
        }
    }
    val targetViewportOffsetPx = when (val row = focusedGridRow) {
        null -> 0f
        0, 1 -> previewBaseOffsetPx
        else -> {
            val topVisibleRow = (row - 2).coerceAtLeast(0)
            val baseOffset = previewBaseOffsetPx + (topVisibleRow * appRowBlockHeightPx)
            val shouldPeekNextRow = totalGridRows > (topVisibleRow + 3)
            if (shouldPeekNextRow) {
                baseOffset + previewPeekExtraPx
            } else {
                baseOffset
            }
        }
    }

    LaunchedEffect(targetViewportOffsetPx, dockHasFocus, openFolderId) {
        if (openFolderId != null) return@LaunchedEffect

        val targetOffset = targetViewportOffsetPx.coerceAtLeast(0f)
        if (gridState.firstVisibleItemIndex != 0) {
            gridState.scrollToItem(0, targetOffset.toInt())
        }

        val currentOffset = if (gridState.firstVisibleItemIndex == 0) {
            gridState.firstVisibleItemScrollOffset.toFloat()
        } else {
            0f
        }

        if (kotlin.math.abs(targetOffset - currentOffset) <= VIEWPORT_SNAP_EPSILON_PX) {
            viewportOffsetPx = targetOffset
            viewportOffsetAnimation.snapTo(targetOffset)
            return@LaunchedEffect
        }

        val animationSpec: AnimationSpec<Float> = tween(
            durationMillis = VIEWPORT_SLIDE_DURATION_MS,
            easing = ViewportSlideEasing,
        )

        if (!dockHasFocus) {
            prefetchArtworkForRow(focusedGridRow ?: 0)
        }

        viewportGliding = true
        try {
            gridState.scroll {
                viewportOffsetAnimation.snapTo(currentOffset)
                viewportOffsetAnimation.animateTo(
                    targetValue = targetOffset,
                    animationSpec = animationSpec,
                ) {
                    val liveOffset = gridState.firstVisibleItemScrollOffset.toFloat()
                    val delta = value - liveOffset
                    if (kotlin.math.abs(delta) > VIEWPORT_SNAP_EPSILON_PX) {
                        scrollBy(delta)
                    }
                }
            }
        } finally {
            viewportGliding = false
            gridState.scrollToItem(0, targetOffset.toInt())
        }
        viewportOffsetPx = targetOffset
        viewportOffsetAnimation.snapTo(targetOffset)
    }

    LaunchedEffect(totalGridRows) {
        if (
            focusedGridRow != null &&
            !dockHasFocus &&
            openFolderId == null &&
            lastViewportRowCount != totalGridRows &&
            lastViewportRowCount > 0
        ) {
            gridState.scrollToItem(0, 0)
            viewportOffsetPx = 0f
            viewportOffsetAnimation.snapTo(0f)
        }
        lastViewportRowCount = totalGridRows
    }

    CompositionLocalProvider(
        LocalBringIntoViewSpec provides bringIntoViewSpec,
        LocalLauncherHazeState provides hazeState,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .hazeSource(hazeState),
            ) {
                BackgroundLayer(
                    backgroundBitmap = backgroundBitmap,
                    backgroundPreset = backgroundPreset,
                    scrimAlpha = scrimAlpha,
                )
            }

            LazyVerticalGrid(
                modifier = Modifier.fillMaxSize(),
                columns = GridCells.Fixed(6),
                state = gridState,
                contentPadding = PaddingValues(
                    bottom = effectiveGridBottomPadding,
                    start = gridHorizontalPadding,
                    end = gridHorizontalPadding
                ),
                verticalArrangement = Arrangement.spacedBy(GridSpacing),
                horizontalArrangement = Arrangement.spacedBy(AppHorizontalSpacing),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HomeHeader(
                            height = headerHeight,
                            fullScreenHeight = configuration.screenHeightDp.dp,
                            backgroundBitmap = backgroundBitmap,
                            backgroundPreset = backgroundPreset,
                            darkModeEnabled = darkModeEnabled,
                            dockItems = dockItems,
                            showDockChevron = showDockChevron && !drawerHasFocus,
                            onDockInteraction = { dockInteractionToken++ },
                            onDockItemClick = { item ->
                                when (item) {
                                    is DockItem.App -> launchHomeApp(item.app)
                                    is DockItem.Folder -> {
                                        openFolderId = item.folder.id
                                        openFolderFocusTarget = item.apps.firstOrNull()?.packageName
                                        openFolderFocusToken++
                                    }
                                }
                            },
                            onDockItemLongClick = { item ->
                                when (item) {
                                    is DockItem.App -> {
                                        focusRestoreTarget = item.id
                                        coroutineScope.launch {
                                            withFrameNanos { }
                                            withFrameNanos { }
                                            appOptionsTarget = AppOptionsTarget(
                                                app = item.app,
                                                showMoveTo = true,
                                                anchor = dockMenuAnchors[item.id],
                                                fromDock = true,
                                            )
                                        }
                                    }
                                    is DockItem.Folder -> {
                                        focusRestoreTarget = item.id
                                        folderOptionsTargetId = item.id
                                    }
                                }
                            },
                            onDockItemBoundsChanged = { itemId, bounds ->
                                updateDockMenuAnchor(itemId, bounds)
                            },
                            iconOverrides = iconOverrides,
                            onDockFocusChanged = {
                                dockHasFocus = it
                                if (it) {
                                    dockInteractionToken++
                                    focusedGridRow = null
                                }
                                if (it) {
                                    drawerHasFocus = false
                                }
                            },
                            focusRestoreTarget = if (
                                appOptionsTarget == null &&
                                folderOptionsTargetId == null &&
                                moveAppRequest == null
                            ) {
                                focusRestoreTarget
                            } else {
                                null
                            },
                            focusRestoreToken = focusRestoreToken,
                            onFocusRestored = {
                                focusRestoreTarget = null
                                suppressDockFocus = false
                            },
                            onOpenLauncherSettings = { showLauncherSettings = true },
                            networkStatusState = rememberNetworkStatus(),
                            statusFocusRequesters = statusFocusRequesters,
                            dockItemRequesters = dockItemRequesters,
                            suppressDockFocus = suppressDockFocus,
                            deferredArtworkLoadEnabled = deferredArtworkLoadEnabled,
                            moveModeActive = moveModeActive,
                            moveModePackage = moveModeItemId,
                            onMoveFocus = handleMoveFocus,
                            onMoveNavigateFromDock = handleMoveNavigateFromDock,
                            onMoveDrop = handleMoveDrop,
                            onDockNavigateDownStart = {
                                if (rowItems.isNotEmpty()) {
                                    Snapshot.withMutableSnapshot {
                                        focusedGridRow = 0
                                        deferredArtworkLoadEnabled = true
                                    }
                                    prefetchArtworkForRow(0)
                                }
                            },
                        )
                    }

                    itemsIndexed(rowItems, key = { _, item ->
                        when (item) {
                            is RowItem.App -> item.app.packageName
                            is RowItem.Folder -> item.folder.id
                            is RowItem.Input -> "input:${item.source.id}"
                        }
                    }) { index, item ->
                        val column = index % 6
                        val row = index / 6
                        val isLastItem = index == rowItems.lastIndex
                        val isRowEnd = column == 5 || isLastItem
                        val edgeLockModifier = Modifier.focusProperties {
                            if (column == 0) left = FocusRequester.Cancel
                            if (isRowEnd) right = FocusRequester.Cancel
                            if (row == 0) {
                                dockItemRequesters.getOrNull(column)?.let { up = it }
                            }
                        }
                        when (item) {
                            is RowItem.App -> {
                                val app = item.app
                                DrawerAppCard(
                                    app = app,
                                    iconOverride = iconOverrides[app.packageName],
                                    width = homeTileWidth,
                                    height = DrawerCardHeight,
                                    prioritizeArtwork = eagerArtworkPackages.contains(app.packageName),
                                    deferredArtworkLoadEnabled = deferredArtworkLoadEnabled || drawerHasFocus,
                                    onClick = { launchHomeApp(app) },
                                    onLongClick = {
                                        focusRestoreTarget = app.packageName
                                        appOptionsTarget = AppOptionsTarget(
                                            app = app,
                                            showMoveTo = true,
                                            anchor = homeMenuAnchors[app.packageName],
                                        )
                                    },
                                    labelOverrides = labelOverrides,
                                    onFocus = {
                                        Snapshot.withMutableSnapshot {
                                            dockHasFocus = false
                                            drawerHasFocus = true
                                            focusedGridRow = row
                                        }
                                    },
                                    modifier = edgeLockModifier,
                                    shouldRestoreFocus = appOptionsTarget == null &&
                                            moveAppRequest == null &&
                                            folderOptionsTargetId == null &&
                                            focusRestoreTarget == app.packageName,
                                    focusRestoreToken = focusRestoreToken,
                                    onFocusRestored = {
                                        focusRestoreTarget = null
                                        suppressDockFocus = false
                                    },
                                    moveModeActive = moveModeActive,
                                    isMoving = moveModeItemId == app.packageName,
                                    moveTarget = MoveTarget.Grid(index, app.packageName),
                                    onMoveFocus = handleMoveFocus,
                                    onMoveNavigate = handleMoveNavigateFromGrid,
                                    onMoveDrop = handleMoveDrop,
                                    onDirectionalKeyDown = { key ->
                                        if (row == 0 && key == androidx.compose.ui.input.key.Key.DirectionUp) {
                                        }
                                    },
                                    onBoundsChanged = { bounds ->
                                        updateHomeMenuAnchor(app.packageName, row, bounds)
                                    },
                                )
                            }
                            is RowItem.Folder -> {
                                FolderRowCard(
                                    folder = item.folder,
                                    apps = item.apps,
                                    iconOverrides = iconOverrides,
                                    darkModeEnabled = darkModeEnabled,
                                    width = homeTileWidth,
                                    height = DrawerCardHeight,
                                    onClick = {
                                        openFolderId = item.folder.id
                                        openFolderFocusTarget = item.apps.firstOrNull()?.packageName
                                        openFolderFocusToken++
                                    },
                                    onLongClick = {
                                        focusRestoreTarget = item.folder.id
                                        folderOptionsTargetId = item.folder.id
                                    },
                                    onFocus = {
                                        Snapshot.withMutableSnapshot {
                                            dockHasFocus = false
                                            drawerHasFocus = true
                                            focusedGridRow = row
                                        }
                                    },
                                    modifier = edgeLockModifier,
                                    shouldRestoreFocus = folderOptionsTargetId == null &&
                                            focusRestoreTarget == item.folder.id,
                                    focusRestoreToken = focusRestoreToken,
                                    onFocusRestored = {
                                        focusRestoreTarget = null
                                        suppressDockFocus = false
                                    },
                                    moveModeActive = moveModeActive,
                                    isMoving = moveModeItemId == item.folder.id,
                                    moveTarget = MoveTarget.Grid(index, item.folder.id),
                                    onMoveFocus = handleMoveFocus,
                                    onMoveNavigate = handleMoveNavigateFromGrid,
                                    onMoveDrop = handleMoveDrop,
                                    onDirectionalKeyDown = { key ->
                                        if (row == 0 && key == androidx.compose.ui.input.key.Key.DirectionUp) {
                                        }
                                    },
                                    onBoundsChanged = { bounds ->
                                        updateHomeMenuAnchor(item.folder.id, row, bounds)
                                    },
                                )
                            }
                            is RowItem.Input -> {
                                val inputAnchorId = inputIconOverrideKey(item.source.id)
                                InputRowCard(
                                    source = item.source,
                                    iconOverride = iconOverrides[inputIconOverrideKey(item.source.id)],
                                    darkModeEnabled = darkModeEnabled,
                                    width = homeTileWidth,
                                    height = DrawerCardHeight,
                                    modifier = edgeLockModifier,
                                    onClick = { launchHomeInput(item.source) },
                                    onLongClick = {
                                        focusRestoreTarget = inputIconOverrideKey(item.source.id)
                                        inputOptionsTarget = item.source
                                    },
                                    onFocus = {
                                        Snapshot.withMutableSnapshot {
                                            dockHasFocus = false
                                            drawerHasFocus = true
                                            focusedGridRow = row
                                        }
                                    },
                                    shouldRestoreFocus = inputOptionsTarget == null &&
                                            focusRestoreTarget == inputIconOverrideKey(item.source.id),
                                    focusRestoreToken = focusRestoreToken,
                                    onFocusRestored = {
                                        focusRestoreTarget = null
                                        suppressDockFocus = false
                                    },
                                    onDirectionalKeyDown = { key ->
                                        if (row == 0 && key == androidx.compose.ui.input.key.Key.DirectionUp) {
                                        }
                                    },
                                    onBoundsChanged = { bounds ->
                                        updateHomeMenuAnchor(inputAnchorId, row, bounds)
                                    },
                                )
            }
        }
    }
                }
        }

        openFolderId?.let { folderId ->
            foldersById[folderId]?.let { folder ->
                FolderOverlay(
                    folder = folder,
                    apps = folder.appPackageNames.mapNotNull { appsByPackage[it] },
                    iconOverrides = iconOverrides,
                    darkModeEnabled = darkModeEnabled,
                    focusTargetPackage = openFolderFocusTarget,
                    focusRestoreToken = openFolderFocusToken,
                    onFocusRestored = { openFolderFocusTarget = null },
                    moveModePackage = folderMoveModePackage,
                    onMoveDrop = {
                        openFolderFocusTarget = folderMoveModePackage
                        openFolderFocusToken++
                        folderMoveModePackage = null
                    },
                    onMoveApp = { packageName, targetIndex ->
                        moveAppWithinFolder(folder.id, packageName, targetIndex)
                    },
                    onDismiss = {
                        folderMoveModePackage = null
                        openFolderId = null
                        focusRestoreTarget = folder.id
                        focusRestoreToken++
                    },
                    onRenameCommit = { newName ->
                        val updatedName = newName.trim().ifBlank { DEFAULT_FOLDER_NAME }
                        if (updatedName != folder.name) {
                            val updatedFolders = folders.map { current ->
                                if (current.id == folder.id) current.copy(name = updatedName) else current
                            }
                            folders = updatedFolders
                            saveFolders(prefs, updatedFolders)
                        }
                        openFolderFocusTarget = null
                        openFolderFocusToken++
                    },
                    requestInlineRename = requestInlineRename,
                    onInlineRenameHandled = { requestInlineRename = false },
                    onAppClick = { launchFolderApp(it, folder.id) },
                    onAppLongClick = { app ->
                        openFolderFocusTarget = app.packageName
                        openFolderFocusToken++
                        folderAppOptionsTarget = AppMoveRequest(
                            app = app,
                            sourceFolderId = folder.id,
                            anchor = folderMenuAnchors[app.packageName],
                        )
                    },
                    onAppBoundsChanged = { app, bounds ->
                        updateFolderMenuAnchor(app.packageName, bounds)
                    },
                    labelOverrides = labelOverrides,
                )
            }
        }

        if (showLauncherSettings) {
            LauncherSettingsScreen(
                onDismiss = { showLauncherSettings = false },
                onAppearance = {
                    settingsFocusIndex = 0
                    showAppearanceSettings = true
                    showLauncherSettings = false
                },
                onApps = {
                    settingsFocusIndex = 1
                    showAppsSettings = true
                    showLauncherSettings = false
                },
                onHomeButtonTakeover = {
                    settingsFocusIndex = 2
                    showHomeButtonTakeoverSettings = true
                    showLauncherSettings = false
                },
                onSystem = {
                    settingsFocusIndex = 3
                    openSystemSettings(context)
                },
                onNetwork = {
                    settingsFocusIndex = 4
                    openNetworkSettings(context)
                },
                onDevicePreferences = {
                    settingsFocusIndex = 5
                    openDevicePreferencesSettings(context)
                },
                onDisplaySound = {
                    settingsFocusIndex = 6
                    openDisplaySoundSettings(context)
                },
                onDeveloper = {
                    settingsFocusIndex = 7
                    openDeveloperOptions(context)
                },
                onDateTime = {
                    settingsFocusIndex = 8
                    openDateTimeSettings(context)
                },
                onAbout = {
                    settingsFocusIndex = 9
                    openAboutSettings(context)
                },
                onPowerOff = {
                    settingsFocusIndex = 10
                    requestPowerOff(context)
                },
                initialFocusIndex = settingsFocusIndex,
                initialScrollIndex = settingsScrollIndex,
                initialScrollOffset = settingsScrollOffset,
                onScrollPositionChanged = { index, offset ->
                    settingsScrollIndex = index
                    settingsScrollOffset = offset
                },
                onOptionFocused = { settingsFocusIndex = it },
                darkModeEnabled = darkModeEnabled,
            )
        }

        if (showHomeButtonTakeoverSettings) {
            HomeButtonTakeoverSettingsScreen(
                onDismiss = {
                    showHomeButtonTakeoverSettings = false
                    showLauncherSettings = true
                },
                onOpenAccessibilitySettings = { openAccessibilitySettings(context) },
                onOpenAppInfo = { openAppDetails(context, context.packageName) },
                onLaunchNow = { launchLauncherHome(context) },
                darkModeEnabled = darkModeEnabled,
                initialFocusIndex = homeButtonTakeoverFocusIndex,
                onOptionFocused = { homeButtonTakeoverFocusIndex = it },
            )
        }

        if (showAppearanceSettings) {
            AppearanceSettingsScreen(
                onDismiss = {
                    showAppearanceSettings = false
                    showLauncherSettings = true
                },
                onBackgroundImage = {
                    showBackgroundMenu = true
                    showAppearanceSettings = false
                },
                darkModeEnabled = darkModeEnabled,
                themeMode = themeMode,
                onThemeModeChange = onThemeModeChange,
                initialFocusIndex = appearanceFocusIndex,
                onOptionFocused = { appearanceFocusIndex = it },
            )
        }

        if (showAppsSettings) {
            AppsSettingsScreen(
                apps = allApps,
                inputSources = inputSources,
                hiddenPackages = hiddenPackages,
                hiddenInputs = hiddenInputs,
                onToggleAppHidden = { packageName ->
                    val wasHidden = hiddenPackages.contains(packageName)
                    val updated = if (wasHidden) {
                        hiddenPackages - packageName
                    } else {
                        hiddenPackages + packageName
                    }
                    hiddenPackages = updated
                    saveHiddenPackages(prefs, updated)

                    // Keep the home order in sync immediately. An unhidden
                    // app is appended so it appears in the final available
                    // row rather than being restored into its old position.
                    val updatedGridOrder = if (wasHidden) {
                        gridOrder + packageName
                    } else {
                        gridOrder.filterNot { it == packageName }
                    }
                    if (updatedGridOrder != gridOrder) {
                        gridOrder = updatedGridOrder
                        saveGridOrder(prefs, updatedGridOrder)
                    }

                    if (!wasHidden) {
                        dockSlots = dockSlots.map { if (it == packageName) null else it }
                        saveDockSlots(prefs, dockSlots)
                    }
                },
                onToggleInputHidden = { inputId ->
                    val updated = if (hiddenInputs.contains(inputId)) {
                        hiddenInputs - inputId
                    } else {
                        hiddenInputs + inputId
                    }
                    hiddenInputs = updated
                    saveHiddenInputs(prefs, updated)
                },
                onDismiss = {
                    showAppsSettings = false
                    showLauncherSettings = true
                },
                darkModeEnabled = darkModeEnabled,
            )
        }

        if (showBackgroundMenu) {
            BackgroundImageSettingsScreen(
                onDismiss = {
                    showBackgroundMenu = false
                    showAppearanceSettings = true
                },
                onDefaultOptions = {
                    showBackgroundPresets = true
                },
                onChooseFromDevice = {
                    prefs.edit().remove(KEY_BACKGROUND_PRESET).apply()
                    backgroundPresetId = null
                    backgroundPicker.launch(arrayOf("image/*"))
                },
                onSetFromUrl = {
                    backgroundUrlLightInput = ""
                    backgroundUrlDarkInput = ""
                    backgroundUrlBothInput = ""
                    backgroundUrlError = null
                    showBackgroundUrlDialog = true
                },
                darkModeEnabled = darkModeEnabled,
                initialFocusIndex = backgroundMenuFocusIndex,
                onOptionFocused = { backgroundMenuFocusIndex = it },
            )
        }

        if (showBackgroundPresets) {
            val presets = remember { defaultBackgroundPresets() }
            val lightSelectedId = lightModePresetId ?: backgroundPresetId
            val darkSelectedId = darkModePresetId ?: backgroundPresetId
            val selectedIndex = presets.indexOfFirst { it.id == backgroundPresetId }
            val effectiveIndex = if (selectedIndex >= 0) selectedIndex else backgroundPresetFocusIndex
            BackgroundPresetGridScreen(
                presets = presets,
                lightModeSelectedId = lightSelectedId,
                darkModeSelectedId = darkSelectedId,
                onSelect = { preset ->
                    if (preset.id == BACKGROUND_PRESET_CYCLE_ALL) {
                        backgroundCycleStep = 0L
                    }
                    backgroundPresetId = preset.id
                    prefs.edit().putString(KEY_BACKGROUND_PRESET, preset.id).apply()
                    prefs.edit().remove(KEY_BACKGROUND_URI).apply()
                    prefs.edit().remove(KEY_BACKGROUND_PRESET_LIGHT).apply()
                    prefs.edit().remove(KEY_BACKGROUND_PRESET_DARK).apply()
                    prefs.edit().remove(KEY_BACKGROUND_URI_LIGHT).apply()
                    prefs.edit().remove(KEY_BACKGROUND_URI_DARK).apply()
                    lightModePresetId = null
                    darkModePresetId = null
                    lightModeBackgroundUri = null
                    darkModeBackgroundUri = null
                    backgroundUri = null
                    presets.indexOfFirst { it.id == preset.id }
                        .takeIf { it >= 0 }
                        ?.let { backgroundPresetFocusIndex = it }
                },
                onLongSelect = { preset, bounds ->
                    backgroundModePresetTarget = preset
                    backgroundModeMenuAnchor = HomeMenuAnchor(row = 0, boundsInWindow = bounds)
                },
                onDismiss = {
                    showBackgroundPresets = false
                    showBackgroundMenu = true
                },
                darkModeEnabled = darkModeEnabled,
                initialFocusIndex = effectiveIndex,
                onOptionFocused = { backgroundPresetFocusIndex = it },
            )
        }

        backgroundModePresetTarget?.let { preset ->
            val dismissBackgroundModeMenu: () -> Unit = {
                backgroundModePresetTarget = null
                backgroundModeMenuAnchor = null
            }
            val anchor = backgroundModeMenuAnchor
            if (anchor != null && anchor.boundsInWindow.width > 0f && anchor.boundsInWindow.height > 0f) {
                HomeBackgroundPresetModeMenu(
                    anchor = anchor,
                    darkModeEnabled = darkModeEnabled,
                    onDismiss = dismissBackgroundModeMenu,
                    onSetLight = {
                        if (preset.id == BACKGROUND_PRESET_CYCLE_ALL) {
                            backgroundCycleStep = 0L
                        }
                        lightModePresetId = preset.id
                        prefs.edit().putString(KEY_BACKGROUND_PRESET_LIGHT, preset.id).apply()
                        dismissBackgroundModeMenu()
                    },
                    onSetDark = {
                        if (preset.id == BACKGROUND_PRESET_CYCLE_ALL) {
                            backgroundCycleStep = 0L
                        }
                        darkModePresetId = preset.id
                        prefs.edit().putString(KEY_BACKGROUND_PRESET_DARK, preset.id).apply()
                        dismissBackgroundModeMenu()
                    },
                )
            } else {
                BackgroundPresetModeDialog(
                    presetName = preset.name,
                    onSetLight = {
                        if (preset.id == BACKGROUND_PRESET_CYCLE_ALL) {
                            backgroundCycleStep = 0L
                        }
                        lightModePresetId = preset.id
                        prefs.edit().putString(KEY_BACKGROUND_PRESET_LIGHT, preset.id).apply()
                        dismissBackgroundModeMenu()
                    },
                    onSetDark = {
                        if (preset.id == BACKGROUND_PRESET_CYCLE_ALL) {
                            backgroundCycleStep = 0L
                        }
                        darkModePresetId = preset.id
                        prefs.edit().putString(KEY_BACKGROUND_PRESET_DARK, preset.id).apply()
                        dismissBackgroundModeMenu()
                    },
                    onDismiss = dismissBackgroundModeMenu,
                )
            }
        }

        if (showBackgroundUrlDialog) {
            BackgroundUrlDialog(
                lightUrl = backgroundUrlLightInput,
                darkUrl = backgroundUrlDarkInput,
                bothUrl = backgroundUrlBothInput,
                error = backgroundUrlError,
                isLoading = backgroundUrlLoading,
                onLightUrlChange = { backgroundUrlLightInput = it },
                onDarkUrlChange = { backgroundUrlDarkInput = it },
                onBothUrlChange = { backgroundUrlBothInput = it },
                onApply = {
                    val entries = listOf(
                        Triple(backgroundUrlBothInput, "background_url.jpg", "both"),
                        Triple(backgroundUrlLightInput, "background_url_light.jpg", "light"),
                        Triple(backgroundUrlDarkInput, "background_url_dark.jpg", "dark"),
                    ).mapNotNull { (value, fileName, target) ->
                        value.trim().takeIf { it.isNotEmpty() }?.let { Triple(it, fileName, target) }
                    }
                    if (entries.isEmpty() || entries.any { (url, _, _) ->
                            !url.startsWith("http://") && !url.startsWith("https://")
                        }) {
                        backgroundUrlError = "Enter a valid http or https URL in each field you use."
                        return@BackgroundUrlDialog
                    }
                    backgroundUrlError = null
                    backgroundUrlLoading = true
                    coroutineScope.launch {
                        val results = withContext(Dispatchers.IO) {
                            entries.map { (url, fileName, target) ->
                                Triple(target, downloadBackgroundFromUrl(context, url, fileName), fileName)
                            }
                        }
                        backgroundUrlLoading = false
                        val failed = results.firstOrNull { (_, result, _) -> result.uri == null }
                        if (failed != null) {
                            backgroundUrlError = failed.second.errorMessage ?: "Failed to download image."
                        } else {
                            results.forEach { (target, result, _) ->
                                val storedUri = result.uri!!.toString()
                                when (target) {
                                    "both" -> {
                                        prefs.edit().putString(KEY_BACKGROUND_URI, storedUri)
                                            .remove(KEY_BACKGROUND_PRESET).apply()
                                        backgroundUri = storedUri
                                        backgroundPresetId = null
                                    }
                                    "light" -> {
                                        prefs.edit().putString(KEY_BACKGROUND_URI_LIGHT, storedUri)
                                            .remove(KEY_BACKGROUND_PRESET_LIGHT).apply()
                                        lightModeBackgroundUri = storedUri
                                        lightModePresetId = null
                                    }
                                    "dark" -> {
                                        prefs.edit().putString(KEY_BACKGROUND_URI_DARK, storedUri)
                                            .remove(KEY_BACKGROUND_PRESET_DARK).apply()
                                        darkModeBackgroundUri = storedUri
                                        darkModePresetId = null
                                    }
                                }
                            }
                            showBackgroundUrlDialog = false
                        }
                    }
                },
                onDismiss = { showBackgroundUrlDialog = false },
            )
        }

        appOptionsTarget?.let { target ->
            val app = target.app
            val appMenuAnchor = target.anchor ?: when {
                target.fromDock -> dockMenuAnchors[app.packageName]
                target.showMoveTo -> homeMenuAnchors[app.packageName]
                else -> null
            }
            val showingAnchoredMoveToSubmenu = moveAppRequest?.let { request ->
                request.sourceFolderId == null && request.app.packageName == app.packageName
            } == true

            val dismissAppOptions = {
                appOptionsTarget = null
                if (showingAnchoredMoveToSubmenu) {
                    moveAppRequest = null
                }
                if (focusRestoreTarget == app.packageName) {
                    focusRestoreToken++
                }
            }
            val hideApp = {
                val fallbackFocusTarget = fallbackFocusTargetForHiddenItem(app.packageName)
                hiddenPackages = hiddenPackages + app.packageName
                saveHiddenPackages(prefs, hiddenPackages)
                val updatedFolders = folders.map { folder ->
                    folder.copy(appPackageNames = folder.appPackageNames.filterNot { it == app.packageName })
                }.filter { it.appPackageNames.isNotEmpty() }
                val updatedGrid = gridOrder.filterNot {
                    it == app.packageName ||
                            (isFolderId(it) && updatedFolders.none { folder -> folder.id == it })
                }
                // Compact the dock first. Apps to the right slide left into the
                // removed slot; only the final slot receives a grid replacement.
                val updatedDockSlots = if (dockSlots.contains(app.packageName)) {
                    rebalanceDockAfterRemovingApp(app.packageName, updatedGrid)
                } else {
                    dockSlots
                }
                dockSlots = updatedDockSlots
                folders = updatedFolders
                saveDockSlots(prefs, updatedDockSlots)
                saveFolders(prefs, updatedFolders)
                if (updatedGrid != gridOrder) {
                    gridOrder = updatedGrid
                    saveGridOrder(prefs, updatedGrid)
                }
                appOptionsTarget = null
                moveAppRequest = null
                if (openFolderId != null && updatedFolders.none { it.id == openFolderId }) {
                    openFolderId = null
                }
                if (fallbackFocusTarget != null) {
                    focusRestoreTarget = fallbackFocusTarget
                    focusRestoreToken++
                    val shouldSuppress = !updatedDockSlots.contains(fallbackFocusTarget)
                    suppressDockFocus = shouldSuppress
                    if (shouldSuppress) {
                        dockSuppressToken++
                    }
                } else if (focusRestoreTarget == app.packageName) {
                    focusRestoreTarget = null
                }
            }
            val manageApp = {
                openAppDetails(context, app.packageName)
                appOptionsTarget = null
                moveAppRequest = null
                if (focusRestoreTarget == app.packageName) {
                    focusRestoreToken++
                }
            }
            val changeAppIcon = {
                iconPickerTarget = app
                iconPicker.launch(arrayOf("image/*"))
                appOptionsTarget = null
                moveAppRequest = null
                if (focusRestoreTarget == app.packageName) {
                    focusRestoreToken++
                }
            }
            val hasCustomIcon = iconOverrides.containsKey(app.packageName)
            val resetAppIcon = {
                if (hasCustomIcon) {
                    clearIconOverride(prefs, app.packageName)
                    iconOverrides = iconOverrides - app.packageName
                }
                appOptionsTarget = null
                moveAppRequest = null
                if (focusRestoreTarget == app.packageName) {
                    focusRestoreToken++
                }
            }
            val requestRename = {
                appOptionsTarget = null
                moveAppRequest = null
                if (focusRestoreTarget == app.packageName) {
                    focusRestoreToken++
                }
                renameDialogInput = displayLabelFor(app, labelOverrides)
                renameDialogTarget = app
            }

            if (appMenuAnchor != null) {
                AnchoredAppOptionsMenu(
                    anchor = appMenuAnchor,
                    darkModeEnabled = darkModeEnabled,
                    folders = folders,
                    showMoveToOption = target.showMoveTo,
                    showMoveToSubmenu = showingAnchoredMoveToSubmenu,
                    onDismiss = dismissAppOptions,
                    onDismissMoveToSubmenu = { moveAppRequest = null },
                    onMove = {
                        moveModeItemId = app.packageName
                        pendingDockFocusIndex = null
                        moveAppRequest = null
                        appOptionsTarget = null
                    },
                    onMoveTo = {
                        if (target.showMoveTo) {
                            moveAppRequest = AppMoveRequest(app = app)
                        }
                    },
                    onHide = hideApp,
                    onManage = manageApp,
                    onChangeIcon = changeAppIcon,
                    onResetIcon = if (hasCustomIcon) resetAppIcon else null,
                    onRename = requestRename,
                    onCreateFolder = {
                        val newFolderId = createFolderFromApp(app, null)
                        moveAppRequest = null
                        appOptionsTarget = null
                        openFolderId = newFolderId
                        openFolderFocusTarget = app.packageName
                        openFolderFocusToken++
                        focusRestoreTarget = newFolderId
                        focusRestoreToken++
                        suppressDockFocus = true
                        dockSuppressToken++
                    },
                    onMoveToFolder = { folderId ->
                        moveAppIntoFolder(app, folderId, null)
                        moveAppRequest = null
                        appOptionsTarget = null
                    },
                )
            } else {
                AppOptionsDialog(
                    appLabel = displayLabelFor(app, labelOverrides),
                    onDismiss = dismissAppOptions,
                    onMove = {
                        moveModeItemId = app.packageName
                        pendingDockFocusIndex = null
                        moveAppRequest = null
                        appOptionsTarget = null
                    },
                    onMoveTo = if (target.showMoveTo) {
                        {
                            moveAppRequest = AppMoveRequest(app = app)
                            appOptionsTarget = null
                        }
                    } else {
                        null
                    },
                    onHide = hideApp,
                    onManage = manageApp,
                    onChangeIcon = changeAppIcon,
                    onResetIcon = if (hasCustomIcon) resetAppIcon else null,
                    onRename = requestRename,
                )
            }
        }

        renameDialogTarget?.let { app ->
            RenameAppDialog(
                appLabel = app.label,
                initialName = renameDialogInput,
                onNameChange = { renameDialogInput = it },
                onApply = {
                    val trimmed = renameDialogInput.trim()
                    if (trimmed.isNotEmpty() && trimmed != app.label) {
                        saveLabelOverride(prefs, app.packageName, trimmed)
                        labelOverrides = labelOverrides + (app.packageName to trimmed)
                    } else if (trimmed.isEmpty() || trimmed == app.label) {
                        clearLabelOverride(prefs, app.packageName)
                        labelOverrides = labelOverrides - app.packageName
                    }
                    renameDialogTarget = null
                    renameDialogInput = ""
                    if (focusRestoreTarget == app.packageName) {
                        focusRestoreToken++
                    }
                },
                onReset = {
                    clearLabelOverride(prefs, app.packageName)
                    labelOverrides = labelOverrides - app.packageName
                    renameDialogTarget = null
                    renameDialogInput = ""
                    if (focusRestoreTarget == app.packageName) {
                        focusRestoreToken++
                    }
                },
                onDismiss = {
                    renameDialogTarget = null
                    renameDialogInput = ""
                    if (focusRestoreTarget == app.packageName) {
                        focusRestoreToken++
                    }
                },
            )
        }

        folderAppOptionsTarget?.let { request ->
            val app = request.app
            val folderAppAnchor = request.anchor ?: folderMenuAnchors[app.packageName]
            val showingAnchoredMoveToSubmenu = moveAppRequest?.let { moveRequest ->
                moveRequest.sourceFolderId == request.sourceFolderId &&
                        moveRequest.app.packageName == app.packageName
            } == true
            if (folderAppAnchor != null) {
                AnchoredFolderAppOptionsMenu(
                    anchor = folderAppAnchor,
                    darkModeEnabled = darkModeEnabled,
                    folders = folders.filter { it.id != request.sourceFolderId },
                    showMoveToSubmenu = showingAnchoredMoveToSubmenu,
                    onDismiss = {
                        folderAppOptionsTarget = null
                        moveAppRequest = null
                        openFolderFocusTarget = app.packageName
                        openFolderFocusToken++
                    },
                    onDismissMoveToSubmenu = { moveAppRequest = null },
                    onMove = {
                        folderMoveModePackage = app.packageName
                        moveAppRequest = null
                        folderAppOptionsTarget = null
                        openFolderFocusTarget = app.packageName
                        openFolderFocusToken++
                    },
                    onMoveTo = {
                        moveAppRequest = request
                    },
                    onManage = {
                        openAppDetails(context, app.packageName)
                        folderAppOptionsTarget = null
                        moveAppRequest = null
                        openFolderFocusTarget = app.packageName
                        openFolderFocusToken++
                    },
                    onMoveToHomeScreen = {
                        val sourceFolderId = request.sourceFolderId ?: return@AnchoredFolderAppOptionsMenu
                        moveFolderAppToHomeScreen(app, sourceFolderId)
                        moveAppRequest = null
                        folderAppOptionsTarget = null
                    },
                    onCreateFolder = {
                        val newFolderId = createFolderFromApp(app, request.sourceFolderId)
                        moveAppRequest = null
                        folderAppOptionsTarget = null
                        openFolderId = newFolderId
                        openFolderFocusTarget = app.packageName
                        openFolderFocusToken++
                        focusRestoreTarget = newFolderId
                        focusRestoreToken++
                        suppressDockFocus = true
                        dockSuppressToken++
                    },
                    onMoveToFolder = { folderId ->
                        moveAppIntoFolder(app, folderId, request.sourceFolderId)
                        moveAppRequest = null
                        folderAppOptionsTarget = null
                    },
                )
            } else {
                FolderAppOptionsDialog(
                    appLabel = displayLabelFor(app, labelOverrides),
                    onDismiss = {
                        folderAppOptionsTarget = null
                        openFolderFocusTarget = app.packageName
                        openFolderFocusToken++
                    },
                    onMove = {
                        folderMoveModePackage = app.packageName
                        folderAppOptionsTarget = null
                        openFolderFocusTarget = app.packageName
                        openFolderFocusToken++
                    },
                    onMoveTo = {
                        moveAppRequest = request
                        folderAppOptionsTarget = null
                    },
                    onManage = {
                        openAppDetails(context, app.packageName)
                        folderAppOptionsTarget = null
                        openFolderFocusTarget = app.packageName
                        openFolderFocusToken++
                    },
                )
            }
        }

        moveAppRequest?.let { request ->
            val showingAnchoredHomeSubmenu = request.sourceFolderId == null &&
                    appOptionsTarget?.showMoveTo == true &&
                    appOptionsTarget?.app?.packageName == request.app.packageName &&
                    (appOptionsTarget?.anchor ?: homeMenuAnchors[request.app.packageName]) != null
            val showingAnchoredFolderSubmenu =
                folderAppOptionsTarget?.app?.packageName == request.app.packageName &&
                        folderAppOptionsTarget?.sourceFolderId == request.sourceFolderId &&
                        (folderAppOptionsTarget?.anchor ?: folderMenuAnchors[request.app.packageName]) != null
            if (!showingAnchoredHomeSubmenu && !showingAnchoredFolderSubmenu) {
                MoveAppToDialog(
                    appLabel = displayLabelFor(request.app, labelOverrides),
                    showHomeScreenOption = request.sourceFolderId != null,
                    folders = folders.filter { it.id != request.sourceFolderId },
                    onDismiss = {
                        moveAppRequest = null
                        request.sourceFolderId?.let {
                            openFolderFocusTarget = request.app.packageName
                            openFolderFocusToken++
                        }
                    },
                    onMoveToHomeScreen = {
                        val sourceFolderId = request.sourceFolderId ?: return@MoveAppToDialog
                        moveFolderAppToHomeScreen(request.app, sourceFolderId)
                        moveAppRequest = null
                    },
                    onCreateFolder = {
                        val newFolderId = createFolderFromApp(request.app, request.sourceFolderId)
                        moveAppRequest = null
                        openFolderId = newFolderId
                        openFolderFocusTarget = request.app.packageName
                        openFolderFocusToken++
                        focusRestoreTarget = newFolderId
                        focusRestoreToken++
                        suppressDockFocus = true
                        dockSuppressToken++
                    },
                    onMoveToFolder = { folderId ->
                        moveAppIntoFolder(request.app, folderId, request.sourceFolderId)
                        moveAppRequest = null
                    },
                )
            }
        }

        folderOptionsTargetId?.let { folderId ->
            foldersById[folderId]?.let { folder ->
                val menuAnchor = homeMenuAnchors[folder.id] ?: dockMenuAnchors[folder.id]
                if (menuAnchor != null) {
                    HomeFolderOptionsMenu(
                        anchor = menuAnchor,
                        darkModeEnabled = darkModeEnabled,
                        onDismiss = {
                            folderOptionsTargetId = null
                            if (focusRestoreTarget == folder.id) {
                                focusRestoreToken++
                            }
                        },
                        onMove = {
                            moveModeItemId = folder.id
                            pendingDockFocusIndex = null
                            folderOptionsTargetId = null
                        },
                        onRename = {
                            folderOptionsTargetId = null
                            openFolderId = folder.id
                            requestInlineRename = true
                        },
                    )
                } else {
                    FolderOptionsDialog(
                        folderName = folder.name,
                        onDismiss = {
                            folderOptionsTargetId = null
                            if (focusRestoreTarget == folder.id) {
                                focusRestoreToken++
                            }
                        },
                        onMove = {
                            moveModeItemId = folder.id
                            pendingDockFocusIndex = null
                            folderOptionsTargetId = null
                        },
                        onRename = {
                            folderOptionsTargetId = null
                            openFolderId = folder.id
                            requestInlineRename = true
                        },
                    )
                }
            }
        }

        inputOptionsTarget?.let { input ->
            val hiddenInputKey = inputIconOverrideKey(input.id)
            val homeMenuAnchor = homeMenuAnchors[hiddenInputKey]
            val dismissInputOptions = {
                inputOptionsTarget = null
                if (focusRestoreTarget == hiddenInputKey) {
                    focusRestoreToken++
                }
            }
            val hideInput = {
                val fallbackFocusTarget = fallbackFocusTargetForHiddenItem(hiddenInputKey)
                hiddenInputs = hiddenInputs + input.id
                saveHiddenInputs(prefs, hiddenInputs)
                inputOptionsTarget = null
                if (fallbackFocusTarget != null) {
                    focusRestoreTarget = fallbackFocusTarget
                    focusRestoreToken++
                    val shouldSuppress = !dockSlots.contains(fallbackFocusTarget)
                    suppressDockFocus = shouldSuppress
                    if (shouldSuppress) {
                        dockSuppressToken++
                    }
                } else if (focusRestoreTarget == hiddenInputKey) {
                    focusRestoreTarget = null
                }
            }
            val changeInputIcon = {
                inputIconPickerTarget = input
                iconPicker.launch(arrayOf("image/*"))
                inputOptionsTarget = null
                if (focusRestoreTarget == hiddenInputKey) {
                    focusRestoreToken++
                }
            }
            if (homeMenuAnchor != null) {
                HomeInputOptionsMenu(
                    anchor = homeMenuAnchor,
                    darkModeEnabled = darkModeEnabled,
                    onDismiss = dismissInputOptions,
                    onHide = hideInput,
                    onChangeIcon = changeInputIcon,
                )
            } else {
                InputOptionsDialog(
                    inputLabel = input.label,
                    onDismiss = dismissInputOptions,
                    onHide = hideInput,
                    onChangeIcon = changeInputIcon,
                )
            }
        }

        moveDockTarget?.let { app ->
            MoveDockDialog(
                appLabel = displayLabelFor(app, labelOverrides),
                dockSlots = dockSlots,
                onDismiss = { moveDockTarget = null },
                onSlotSelected = { slotIndex ->
                    val updated = dockSlots.toMutableList()
                    val currentIndex = updated.indexOf(app.packageName)
                    val replaced = updated.getOrNull(slotIndex)
                    updated[slotIndex] = app.packageName
                    if (currentIndex >= 0 && currentIndex != slotIndex) {
                        updated[currentIndex] = replaced
                    }
                    dockSlots = updated
                    saveDockSlots(prefs, updated)
                    moveDockTarget = null
                },
            )
        }
    }
}
