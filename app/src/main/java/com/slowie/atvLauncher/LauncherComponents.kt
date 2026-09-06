@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalTvMaterial3Api::class,
)

package com.slowie.atvLauncher

import android.annotation.SuppressLint
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.BringIntoViewSpec
import androidx.compose.foundation.gestures.LocalBringIntoViewSpec
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.blur.materials.CupertinoMaterials
import androidx.compose.runtime.compositionLocalOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.first
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SettingsEthernet
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Wifi
import androidx.compose.material.icons.outlined.WifiOff
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

private const val ARC_PAGE_FORWARD_DURATION_MS = 150
private const val ARC_PAGE_REVERSE_DURATION_MS = 120
@Composable
internal fun BackgroundLayer(
    backgroundBitmap: ImageBitmap?,
    backgroundPreset: BackgroundPreset?,
    scrimAlpha: Float,
) {
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        if (backgroundBitmap != null) {
            Image(
                bitmap = backgroundBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Medium,
            )
        } else {
            val presetColor by animateColorAsState(
                targetValue = backgroundPreset?.color ?: Color(0xFF2A0F1F),
                animationSpec = tween(durationMillis = 700),
                label = "backgroundPresetColor",
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(presetColor)
            )
        }

        if (scrimAlpha > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = scrimAlpha))
            )
        }
    }
}

internal data class MenuPalette(
    val background: Color,
    val title: Color,
    val itemBackground: Color,
    val itemText: Color,
    val itemBackgroundFocused: Color,
    val itemTextFocused: Color,
    val iconTile: Color,
    val iconGlyph: Color,
)

internal fun menuPalette(darkModeEnabled: Boolean): MenuPalette {
    return if (darkModeEnabled) {
        MenuPalette(
            background = Color(0xFF1C1C1C),
            title = Color(0xFF545454),
            itemBackground = Color(0xFF313131),
            itemText = Color(0xFFD2D2D2),
            itemBackgroundFocused = Color(0xFFE5E5E5),
            itemTextFocused = Color(0xFF1A1A1A),
            iconTile = Color(0xFF545454),
            iconGlyph = Color(0xFF1A1A1A),
        )
    } else {
        MenuPalette(
            background = Color(0xFFC3C3C5),
            title = Color(0xFF6B6B6D),
            itemBackground = Color(0xFFCECED1),
            itemText = Color(0xFF2B2B2D),
            itemBackgroundFocused = Color.White,
            itemTextFocused = Color(0xFF141414),
            iconTile = Color(0xFF7D7D7F),
            iconGlyph = Color(0xFFBEBEC0),
        )
    }
}

private fun frostedGlassTint(darkModeEnabled: Boolean): Color =
    if (darkModeEnabled) Color(0xFF2A2A2A).copy(alpha = 0.95f) else GlassTint

internal val LocalLauncherHazeState = compositionLocalOf<HazeState?> { null }

@Composable
private fun launcherHazeStyle(): HazeBlurStyle {
    // CupertinoMaterials supplies the blur, tint, and material response. Keep
    // the explicit enable override so Android 11 and below can use Haze's
    // experimental RenderScript implementation.
    return CupertinoMaterials.regular().then {
        blurEnabled(true)
        noiseFactor(0f)
    }
}

private fun themedContentColor(darkModeEnabled: Boolean): Color =
    if (darkModeEnabled) Color.White else Color.Black

@Composable
internal fun HomeHeader(
    height: Dp,
    fullScreenHeight: Dp,
    backgroundBitmap: ImageBitmap?,
    backgroundPreset: BackgroundPreset?,
    darkModeEnabled: Boolean,
    dockItems: List<DockItem?>,
    showDockChevron: Boolean,
    onDockInteraction: () -> Unit,
    onDockItemClick: (DockItem) -> Unit,
    onDockItemLongClick: (DockItem) -> Unit,
    onDockItemBoundsChanged: (String, androidx.compose.ui.geometry.Rect) -> Unit,
    iconOverrides: Map<String, String>,
    onDockFocusChanged: (Boolean) -> Unit,
    focusRestoreTarget: String?,
    focusRestoreToken: Int,
    onFocusRestored: () -> Unit,
    onOpenLauncherSettings: () -> Unit,
    networkStatusState: MutableState<NetworkStatus>,
    statusFocusRequesters: List<FocusRequester>,
    dockItemRequesters: List<FocusRequester>,
    suppressDockFocus: Boolean,
    deferredArtworkLoadEnabled: Boolean,
    moveModeActive: Boolean,
    moveModePackage: String?,
    onMoveFocus: (MoveTarget) -> Unit,
    onMoveNavigateFromDock: (Int, MoveDirection) -> Unit,
    onMoveDrop: () -> Unit,
    onDockNavigateDownStart: () -> Unit,
) {
    val dockFocusableIndices = remember(dockItems, moveModeActive, suppressDockFocus, focusRestoreTarget) {
        when {
            moveModeActive -> dockItems.indices.toList()
            suppressDockFocus && focusRestoreTarget != null -> {
                dockItems.mapIndexedNotNull { index, item ->
                    if (item?.id == focusRestoreTarget) index else null
                }
            }
            else -> dockItems.mapIndexedNotNull { index, item -> if (item != null) index else null }
        }
    }
    var lastFocusedDockIndex by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(dockFocusableIndices) {
        val currentIndex = lastFocusedDockIndex
        if (currentIndex !in dockFocusableIndices) {
            lastFocusedDockIndex = dockFocusableIndices.firstOrNull()
        }
    }

    val dockReturnRequester = remember(dockFocusableIndices, lastFocusedDockIndex, dockItemRequesters) {
        val preferredIndex = lastFocusedDockIndex?.takeIf { it in dockFocusableIndices }
            ?: dockFocusableIndices.firstOrNull()
        preferredIndex?.let { dockItemRequesters.getOrNull(it) }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height)
    ) {
        StatusBar(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 32.dp, end = 0.dp)
                .offset(x = 16.dp),
            darkModeEnabled = darkModeEnabled,
            networkStatusState = networkStatusState,
            onOpenLauncherSettings = onOpenLauncherSettings,
            focusRequesters = statusFocusRequesters,
            dockDownRequester = dockReturnRequester,
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = DockRowBottomPadding)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (showDockChevron) {
                Box(
                    modifier = Modifier
                        .height(26.dp)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.KeyboardArrowUp,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.6f),
                        modifier = Modifier
                            .size(28.dp)
                            .offset(y = (-2).dp)
                            .graphicsLayer(scaleX = 2.5f, scaleY = 1.15f),
                    )
                }
            }
            DockRow(
                darkModeEnabled = darkModeEnabled,
                fullScreenHeight = fullScreenHeight,
                backgroundBitmap = backgroundBitmap,
                backgroundPreset = backgroundPreset,
                backdropOffsetY = -(height - DockRowBottomPadding - DockRowHeight),
                dockItems = dockItems,
                onItemClick = onDockItemClick,
                onItemLongClick = onDockItemLongClick,
                onItemBoundsChanged = onDockItemBoundsChanged,
                iconOverrides = iconOverrides,
                onDockFocusChanged = onDockFocusChanged,
                focusRestoreTarget = focusRestoreTarget,
                focusRestoreToken = focusRestoreToken,
                onFocusRestored = onFocusRestored,
                itemFocusRequesters = dockItemRequesters,
                statusFocusRequesters = statusFocusRequesters,
                suppressDockFocus = suppressDockFocus,
                deferredArtworkLoadEnabled = deferredArtworkLoadEnabled,
                moveModeActive = moveModeActive,
                moveModePackage = moveModePackage,
                onMoveFocus = onMoveFocus,
                onMoveNavigateFromDock = onMoveNavigateFromDock,
                onMoveDrop = onMoveDrop,
                onDockNavigateDownStart = onDockNavigateDownStart,
                onDockInteraction = onDockInteraction,
                onDockItemFocused = { index -> lastFocusedDockIndex = index },
            )
        }
    }
}


@Composable
internal fun StatusBar(
    modifier: Modifier = Modifier,
    darkModeEnabled: Boolean,
    networkStatusState: MutableState<NetworkStatus>,
    onOpenLauncherSettings: () -> Unit,
    focusRequesters: List<FocusRequester>,
    dockDownRequester: FocusRequester?,
) {
    val timeText = rememberClockText()
    val status by networkStatusState
    val launcherRequester = focusRequesters.getOrNull(0) ?: remember { FocusRequester() }
    val contentColor = themedContentColor(darkModeEnabled)

    DockStyledSurface(
        modifier = modifier,
        shape = RoundedCornerShape(DockCornerRadius),
        padding = PaddingValues(start = 18.dp, top = 0.dp, end = 0.dp, bottom = 0.dp),
        darkModeEnabled = darkModeEnabled,
        frosted = false,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = timeText,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = contentColor,
                ),
            )
            NetworkIcon(status = status, tint = contentColor)
            GlassIconButton(
                label = "Launcher Settings",
                onClick = onOpenLauncherSettings,
                darkModeEnabled = darkModeEnabled,
                contentColor = contentColor,
                modifier = Modifier
                    .focusRequester(launcherRequester)
                    .focusProperties {
                        left = FocusRequester.Cancel
                        right = FocusRequester.Cancel
                        down = dockDownRequester ?: FocusRequester.Cancel
                    },
            ) {
                Icons.Outlined.Tune
            }
        }
    }
}

@SuppressLint("UnusedBoxWithConstraintsScope")
@Composable
internal fun DockRow(
    darkModeEnabled: Boolean,
    fullScreenHeight: Dp,
    backgroundBitmap: ImageBitmap?,
    backgroundPreset: BackgroundPreset?,
    backdropOffsetY: Dp,
    dockItems: List<DockItem?>,
    onItemClick: (DockItem) -> Unit,
    onItemLongClick: (DockItem) -> Unit,
    onItemBoundsChanged: (String, androidx.compose.ui.geometry.Rect) -> Unit,
    iconOverrides: Map<String, String>,
    onDockFocusChanged: (Boolean) -> Unit,
    focusRestoreTarget: String?,
    focusRestoreToken: Int,
    onFocusRestored: () -> Unit,
    itemFocusRequesters: List<FocusRequester>,
    statusFocusRequesters: List<FocusRequester>,
    suppressDockFocus: Boolean,
    deferredArtworkLoadEnabled: Boolean,
    moveModeActive: Boolean,
    moveModePackage: String?,
    onMoveFocus: (MoveTarget) -> Unit,
    onMoveNavigateFromDock: (Int, MoveDirection) -> Unit,
    onMoveDrop: () -> Unit,
    onDockNavigateDownStart: () -> Unit,
    onDockInteraction: () -> Unit,
    onDockItemFocused: (Int) -> Unit,
) {
    var hasFocus by remember { mutableStateOf(false) }
    val statusLeftRequester = statusFocusRequesters.getOrNull(0)
    val statusRightRequester = statusFocusRequesters.getOrNull(1) ?: statusLeftRequester
    val suppressDockMoveVisualFocus = moveModeActive &&
            moveModePackage != null &&
            dockItems.none { it?.id == moveModePackage }

    LaunchedEffect(hasFocus) {
        onDockFocusChanged(hasFocus)
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(DockRowHeight),
        contentAlignment = Alignment.Center
    ) {
        val gridWidth = maxWidth
        val backgroundPadding = 18.dp
        val dockItemWidth = ((gridWidth - AppHorizontalSpacing * (DOCK_SIZE - 1)) / DOCK_SIZE)
            .coerceAtMost(DockCardWidth)
        // Keep dock cards the same height as the app-row cards. Only the width
        // compresses when six cards plus the configured gaps need to fit.
        val dockItemHeight = DrawerCardHeight
        val dockContentWidth = dockItemWidth * DOCK_SIZE + AppHorizontalSpacing * (DOCK_SIZE - 1)
        // Add only a modest amount of breathing room outside the fixed icon
        // row. The row itself stays centered and its item positions do not
        // change when the surface width changes.
        val dockSideInset = 26.dp

        DockStyledSurface(
            modifier = Modifier
                .requiredWidth(dockContentWidth + dockSideInset * 2)
                .height(DockRowHeight),
            shape = RoundedCornerShape(DockCornerRadius),
            padding = PaddingValues(horizontal = backgroundPadding, vertical = DockRowVerticalPadding),
            darkModeEnabled = darkModeEnabled,
            contentFill = true,
            backdropBitmap = backgroundBitmap,
            backdropPreset = backgroundPreset,
            backdropOffsetY = backdropOffsetY,
            backdropHeight = fullScreenHeight,
        ) {}
        Row(
            modifier = Modifier
                .width(dockItemWidth * DOCK_SIZE + AppHorizontalSpacing * (DOCK_SIZE - 1))
                .align(Alignment.Center)
                .focusGroup()
                .onFocusChanged { hasFocus = it.hasFocus },
            horizontalArrangement = Arrangement.spacedBy(AppHorizontalSpacing),
            verticalAlignment = Alignment.CenterVertically,
        ) {
                    val cellWidth = dockItemWidth
                    dockItems.forEachIndexed { index, item ->
                        key(item?.id ?: "empty-dock-slot-$index") {
                            Box(
                                modifier = Modifier.width(cellWidth),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (item == null) {
                                    if (moveModeActive) {
                                        val focusRequester = itemFocusRequesters.getOrNull(index)
                                        val focusModifier = if (focusRequester != null) {
                                            Modifier
                                                .focusRequester(focusRequester)
                                                .focusProperties {
                                                    if (suppressDockFocus) {
                                                        canFocus = false
                                                    }
                                                    left = itemFocusRequesters.getOrNull(index - 1)
                                                        ?: FocusRequester.Cancel
                                                    right = itemFocusRequesters.getOrNull(index + 1)
                                                        ?: FocusRequester.Cancel
                                                    if (statusLeftRequester != null) {
                                                        up = if (index < DOCK_SIZE / 2) {
                                                            statusLeftRequester
                                                        } else {
                                                            statusRightRequester ?: statusLeftRequester
                                                        }
                                                    }
                                                }
                                        } else {
                                            Modifier
                                        }
                                        EmptyDockSlot(
                                            width = dockItemWidth,
                                            height = dockItemHeight,
                                            modifier = focusModifier,
                                            showPlus = false,
                                            onFocus = {
                                                onDockInteraction()
                                                onDockItemFocused(index)
                                                if (moveModeActive) {
                                                    onMoveFocus(MoveTarget.Dock(index, null))
                                                }
                                            },
                                            moveModeActive = moveModeActive,
                                            onMoveNavigate = { direction ->
                                                onMoveNavigateFromDock(index, direction)
                                            },
                                            onMoveDrop = onMoveDrop,
                                        )
                                    } else {
                                            EmptyDockSlot(
                                                width = dockItemWidth,
                                                height = dockItemHeight,
                                                showPlus = false,
                                            )
                                    }
                                } else {
                                    val focusRequester = itemFocusRequesters.getOrNull(index)
                                    val previousIndex = (index - 1 downTo 0).firstOrNull { dockItems[it] != null }
                                    val nextIndex = (index + 1 until dockItems.size).firstOrNull { dockItems[it] != null }
                                    val focusModifier = if (focusRequester != null) {
                                        Modifier
                                            .focusProperties {
                                                if (suppressDockFocus && item.id != focusRestoreTarget) {
                                                    canFocus = false
                                                }
                                                left = previousIndex?.let { itemFocusRequesters.getOrNull(it) }
                                                    ?: FocusRequester.Cancel
                                                right = nextIndex?.let { itemFocusRequesters.getOrNull(it) }
                                                    ?: FocusRequester.Cancel
                                                if (statusLeftRequester != null) {
                                                    up = if (index < DOCK_SIZE / 2) {
                                                        statusLeftRequester
                                                    } else {
                                                        statusRightRequester ?: statusLeftRequester
                                                    }
                                                }
                                            }
                                    } else {
                                        Modifier
                                    }
                                    when (item) {
                                        is DockItem.App -> {
                                            DockAppItem(
                                                app = item.app,
                                                iconOverride = iconOverrides[item.app.packageName],
                                                width = dockItemWidth,
                                                height = dockItemHeight,
                                                deferredArtworkLoadEnabled = deferredArtworkLoadEnabled,
                                                onClick = { onItemClick(item) },
                                                onLongClick = { onItemLongClick(item) },
                                                onBoundsChanged = { bounds -> onItemBoundsChanged(item.id, bounds) },
                                                shouldRestoreFocus = focusRestoreTarget == item.id,
                                                focusRestoreToken = focusRestoreToken,
                                                onFocusRestored = onFocusRestored,
                                                modifier = focusModifier,
                                                focusRequester = focusRequester,
                                                suppressVisualFocus = (
                                                        suppressDockFocus &&
                                                                item.id != focusRestoreTarget
                                                        ) || suppressDockMoveVisualFocus,
                                                moveModeActive = moveModeActive,
                                                isMoving = moveModePackage == item.id,
                                                moveTarget = MoveTarget.Dock(index, item.id),
                                                onMoveFocus = onMoveFocus,
                                                onMoveNavigate = { direction ->
                                                    onMoveNavigateFromDock(index, direction)
                                                },
                                                onMoveDrop = onMoveDrop,
                                                onDirectionalKeyDown = { key ->
                                                    if (key == Key.DirectionDown) {
                                                        onDockNavigateDownStart()
                                                    }
                                                },
                                                onFocused = {
                                                    onDockInteraction()
                                                    onDockItemFocused(index)
                                                },
                                            )
                                        }
                                        is DockItem.Folder -> {
                                            DockFolderItem(
                                                folder = item.folder,
                                                apps = item.apps,
                                                iconOverrides = iconOverrides,
                                                darkModeEnabled = darkModeEnabled,
                                                width = dockItemWidth,
                                                height = dockItemHeight,
                                                onClick = { onItemClick(item) },
                                                onLongClick = { onItemLongClick(item) },
                                                onBoundsChanged = { bounds -> onItemBoundsChanged(item.id, bounds) },
                                                shouldRestoreFocus = focusRestoreTarget == item.id,
                                                focusRestoreToken = focusRestoreToken,
                                                onFocusRestored = onFocusRestored,
                                                modifier = focusModifier,
                                                focusRequester = focusRequester,
                                                suppressVisualFocus = (
                                                        suppressDockFocus &&
                                                                item.id != focusRestoreTarget
                                                        ) || suppressDockMoveVisualFocus,
                                                moveModeActive = moveModeActive,
                                                isMoving = moveModePackage == item.id,
                                                moveTarget = MoveTarget.Dock(index, item.id),
                                                onMoveFocus = onMoveFocus,
                                                onMoveNavigate = { direction ->
                                                    onMoveNavigateFromDock(index, direction)
                                                },
                                                onMoveDrop = onMoveDrop,
                                                onDirectionalKeyDown = { key ->
                                                    if (key == Key.DirectionDown) {
                                                        onDockNavigateDownStart()
                                                    }
                                                },
                                                onFocused = {
                                                    onDockInteraction()
                                                    onDockItemFocused(index)
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
        }
    }
}

@Composable
internal fun PeekRow(apps: List<LauncherApp>) {
    val peekApps = apps.take(10)
    if (peekApps.isEmpty()) return

    Box(
        modifier = Modifier
            .height(40.dp)
            .fillMaxWidth(0.72f)
            .clip(RoundedCornerShape(TileCornerRadius))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(TileCornerRadius))
            .padding(horizontal = 14.dp)
    ) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = 28.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            peekApps.forEach { app ->
                AppCard(
                    app = app,
                    iconOverride = null,
                    width = PeekCardWidth,
                    height = PeekCardHeight,
                    onClick = null,
                    onLongClick = null,
                )
            }
        }
    }
}

@Composable
internal fun DockAppItem(
    app: LauncherApp,
    iconOverride: String?,
    width: Dp = DockCardWidth,
    height: Dp = DockCardHeight,
    deferredArtworkLoadEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    shouldRestoreFocus: Boolean,
    focusRestoreToken: Int,
    onFocusRestored: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    suppressVisualFocus: Boolean,
    moveModeActive: Boolean,
    isMoving: Boolean,
    moveTarget: MoveTarget,
    onMoveFocus: (MoveTarget) -> Unit,
    onMoveNavigate: (MoveDirection) -> Unit,
    onMoveDrop: () -> Unit,
    onFocused: () -> Unit,
    onDirectionalKeyDown: ((Key) -> Unit)? = null,
    onBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    AppCard(
        app = app,
        iconOverride = iconOverride,
        width = width,
        height = height,
        prioritizeArtwork = true,
        deferredArtworkLoadEnabled = deferredArtworkLoadEnabled,
        onClick = onClick,
        onLongClick = onLongClick,
        focusScaleOrigin = TransformOrigin(0.5f, 0.5f),
        modifier = modifier,
        suppressVisualFocus = suppressVisualFocus,
        moveModeActive = moveModeActive,
        isMoving = isMoving,
        wiggle = isMoving,
        wiggleEmphasis = isMoving,
        onMoveDrop = onMoveDrop,
        onMoveNavigate = if (moveModeActive) onMoveNavigate else null,
        onDirectionalKeyDown = onDirectionalKeyDown,
        onFocusChanged = { focused ->
            if (focused && moveModeActive) {
                onMoveFocus(moveTarget)
            }
            if (focused) {
                onFocused()
            }
        },
        shouldRequestFocus = shouldRestoreFocus,
        focusRequestToken = focusRestoreToken,
        onFocusRequested = onFocusRestored,
        focusRequester = focusRequester,
        onBoundsChanged = onBoundsChanged,
    )
}

@Composable
internal fun EmptyDockSlot(
    modifier: Modifier = Modifier,
    width: Dp = DockCardWidth,
    height: Dp = DockCardHeight,
    showPlus: Boolean = true,
    onFocus: (() -> Unit)? = null,
    moveModeActive: Boolean = false,
    onMoveNavigate: ((MoveDirection) -> Unit)? = null,
    onMoveDrop: (() -> Unit)? = null,
    onDirectionalKeyDown: ((Key) -> Unit)? = null,
) {
    val base = modifier.size(width, height)
    val visualModifier = if (showPlus) {
        base
            .shadow(TileShadowElevation, RoundedCornerShape(TileCornerRadius), clip = false)
            .clip(RoundedCornerShape(TileCornerRadius))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(TileCornerRadius))
            .background(Color.White.copy(alpha = 0.08f))
    } else {
        base
    }
    Box(
        modifier = run {
            val withFocusListener = if (onFocus != null) {
                visualModifier.onFocusChanged {
                    if (it.isFocused) {
                        onFocus()
                    }
                }
            } else {
                visualModifier
            }
            if (onFocus != null || moveModeActive) {
                withFocusListener
                    .onPreviewKeyEvent { event ->
                        if (!moveModeActive && event.type == KeyEventType.KeyDown) {
                            onDirectionalKeyDown?.invoke(event.key)
                        }
                        if (!moveModeActive) return@onPreviewKeyEvent false
                        if (event.key == Key.Back) {
                            if (event.type == KeyEventType.KeyUp) {
                                onMoveDrop?.invoke()
                            }
                            return@onPreviewKeyEvent true
                        }
                        val direction = when (event.key) {
                            Key.DirectionLeft -> MoveDirection.Left
                            Key.DirectionRight -> MoveDirection.Right
                            Key.DirectionUp -> MoveDirection.Up
                            Key.DirectionDown -> MoveDirection.Down
                            else -> null
                        }
                        if (direction != null && onMoveNavigate != null) {
                            if (event.type == KeyEventType.KeyDown) {
                                onMoveNavigate(direction)
                            }
                            return@onPreviewKeyEvent true
                        }
                        val isSelectKey = event.key == Key.DirectionCenter ||
                                event.key == Key.Enter ||
                                event.key == Key.NumPadEnter
                        if (!isSelectKey) return@onPreviewKeyEvent false
                        if (event.type == KeyEventType.KeyUp) {
                            onMoveDrop?.invoke()
                        }
                        true
                    }
                    .focusable()
            } else {
                withFocusListener
            }
        },
        contentAlignment = Alignment.Center,
    ) {
        if (showPlus) {
            Text(
                text = "+",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 28.sp,
                fontWeight = FontWeight.Light,
            )
        }
    }
}

@Composable
internal fun DrawerAppCard(
    app: LauncherApp,
    iconOverride: String?,
    width: Dp = DrawerCardWidth,
    height: Dp = DrawerCardHeight,
    prioritizeArtwork: Boolean,
    deferredArtworkLoadEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    shouldRestoreFocus: Boolean,
    focusRestoreToken: Int,
    onFocusRestored: () -> Unit,
    moveModeActive: Boolean,
    isMoving: Boolean,
    moveTarget: MoveTarget,
    onMoveFocus: (MoveTarget) -> Unit,
    onMoveNavigate: (String, MoveDirection) -> Unit,
    onMoveDrop: () -> Unit,
    onDirectionalKeyPreview: ((Key) -> Boolean)? = null,
    onDirectionalKeyDown: ((Key) -> Unit)? = null,
    onBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    darkModeEnabled: Boolean = true,
    labelOverrides: Map<String, String> = emptyMap(),
) {
    var showLabel by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(
        targetValue = if (showLabel) 1f else 0f,
        animationSpec = tween(durationMillis = 80, easing = FastOutSlowInEasing),
        label = "drawerAppLabelAlpha",
    )

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppCard(
            app = app,
            iconOverride = iconOverride,
            width = width,
            height = height,
            prioritizeArtwork = prioritizeArtwork,
            deferredArtworkLoadEnabled = deferredArtworkLoadEnabled,
            focusedShadowElevation = AppGridFocusedShadowElevation,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
            moveModeActive = moveModeActive,
            isMoving = isMoving,
            wiggle = isMoving,
            wiggleEmphasis = isMoving,
            onMoveDrop = onMoveDrop,
            onMoveNavigate = if (moveModeActive) {
                { direction ->
                    onMoveNavigate(app.packageName, direction)
                }
            } else {
                null
            },
            onDirectionalKeyPreview = onDirectionalKeyPreview,
            onDirectionalKeyDown = onDirectionalKeyDown,
            onFocusChanged = { focused ->
                showLabel = focused
                if (focused) onFocus()
                if (focused && moveModeActive) {
                    onMoveFocus(moveTarget)
                }
            },
            shouldRequestFocus = shouldRestoreFocus,
            focusRequestToken = focusRestoreToken,
            onFocusRequested = onFocusRestored,
            onBoundsChanged = onBoundsChanged,
        )
        Spacer(modifier = Modifier.height(DrawerLabelSpacing))
        Text(
            text = displayLabelFor(app, labelOverrides),
            color = if (darkModeEnabled) Color.White else Color.Black,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(labelAlpha),
        )
    }
}

@Composable
internal fun FolderRowCard(
    folder: LauncherFolder,
    apps: List<LauncherApp>,
    iconOverrides: Map<String, String>,
    darkModeEnabled: Boolean,
    width: Dp = DrawerCardWidth,
    height: Dp = DrawerCardHeight,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    shouldRestoreFocus: Boolean,
    focusRestoreToken: Int,
    onFocusRestored: () -> Unit,
    moveModeActive: Boolean,
    isMoving: Boolean,
    moveTarget: MoveTarget,
    onMoveFocus: (MoveTarget) -> Unit,
    onMoveNavigate: (String, MoveDirection) -> Unit,
    onMoveDrop: () -> Unit,
    onDirectionalKeyDown: ((Key) -> Unit)? = null,
    onBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    var showLabel by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FolderTileCard(
            folder = folder,
            apps = apps,
            iconOverrides = iconOverrides,
            darkModeEnabled = darkModeEnabled,
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = modifier,
            width = width,
            height = height,
            moveModeActive = moveModeActive,
            isMoving = isMoving,
            onMoveDrop = onMoveDrop,
            onMoveNavigate = if (moveModeActive) {
                { direction -> onMoveNavigate(folder.id, direction) }
            } else {
                null
            },
            onFocusChanged = { focused ->
                showLabel = focused
                if (focused) onFocus()
                if (focused && moveModeActive) {
                    onMoveFocus(moveTarget)
                }
            },
            shouldRequestFocus = shouldRestoreFocus,
            focusRequestToken = focusRestoreToken,
            onFocusRequested = onFocusRestored,
            onDirectionalKeyDown = onDirectionalKeyDown,
            onBoundsChanged = onBoundsChanged,
        )
        Spacer(modifier = Modifier.height(DrawerLabelSpacing))
        Text(
            text = folder.name,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(if (showLabel) 1f else 0f),
        )
    }
}

@Composable
internal fun InputRowCard(
    source: InputSource,
    iconOverride: String?,
    darkModeEnabled: Boolean,
    width: Dp = DrawerCardWidth,
    height: Dp = DrawerCardHeight,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    shouldRestoreFocus: Boolean = false,
    focusRestoreToken: Int = 0,
    onFocusRestored: (() -> Unit)? = null,
    onDirectionalKeyDown: ((Key) -> Unit)? = null,
    onBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    var showLabel by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        InputCard(
            label = source.label,
            iconOverride = iconOverride,
            darkModeEnabled = darkModeEnabled,
            width = width,
            height = height,
            modifier = modifier,
            onClick = onClick,
            onLongClick = onLongClick,
            onFocusChanged = { focused ->
                showLabel = focused
                if (focused) onFocus()
            },
            shouldRequestFocus = shouldRestoreFocus,
            focusRequestToken = focusRestoreToken,
            onFocusRequested = onFocusRestored,
            onDirectionalKeyDown = onDirectionalKeyDown,
            onBoundsChanged = onBoundsChanged,
        )
        Spacer(modifier = Modifier.height(DrawerLabelSpacing))
        Text(
            text = source.label,
            color = Color.White,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.alpha(if (showLabel) 1f else 0f),
        )
    }
}

@Composable
internal fun DockFolderItem(
    folder: LauncherFolder,
    apps: List<LauncherApp>,
    iconOverrides: Map<String, String>,
    darkModeEnabled: Boolean,
    width: Dp = DockCardWidth,
    height: Dp = DockCardHeight,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    shouldRestoreFocus: Boolean,
    focusRestoreToken: Int,
    onFocusRestored: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    suppressVisualFocus: Boolean,
    moveModeActive: Boolean,
    isMoving: Boolean,
    moveTarget: MoveTarget,
    onMoveFocus: (MoveTarget) -> Unit,
    onMoveNavigate: (MoveDirection) -> Unit,
    onMoveDrop: () -> Unit,
    onFocused: () -> Unit,
    onDirectionalKeyDown: ((Key) -> Unit)? = null,
    onBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    FolderTileCard(
        folder = folder,
        apps = apps,
        iconOverrides = iconOverrides,
        darkModeEnabled = darkModeEnabled,
        onClick = onClick,
        onLongClick = onLongClick,
        modifier = modifier,
        width = width,
        height = height,
        suppressVisualFocus = suppressVisualFocus,
        moveModeActive = moveModeActive,
        isMoving = isMoving,
        onMoveDrop = onMoveDrop,
        onMoveNavigate = if (moveModeActive) onMoveNavigate else null,
        onFocusChanged = { focused ->
            if (focused && moveModeActive) {
                onMoveFocus(moveTarget)
            }
            if (focused) {
                onFocused()
            }
        },
        shouldRequestFocus = shouldRestoreFocus,
        focusRequestToken = focusRestoreToken,
        onFocusRequested = onFocusRestored,
        focusRequester = focusRequester,
        onDirectionalKeyDown = onDirectionalKeyDown,
        onBoundsChanged = onBoundsChanged,
    )
}

@Composable
private fun FolderTileCard(
    folder: LauncherFolder,
    apps: List<LauncherApp>,
    iconOverrides: Map<String, String>,
    darkModeEnabled: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    width: Dp = DrawerCardWidth,
    height: Dp = DrawerCardHeight,
    suppressVisualFocus: Boolean = false,
    moveModeActive: Boolean = false,
    isMoving: Boolean = false,
    onMoveDrop: (() -> Unit)? = null,
    onMoveNavigate: ((MoveDirection) -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    shouldRequestFocus: Boolean = false,
    focusRequestToken: Int = 0,
    onFocusRequested: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    onDirectionalKeyDown: ((Key) -> Unit)? = null,
    onBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    val shape = RoundedCornerShape(TileCornerRadius)
    var rawFocused by remember { mutableStateOf(false) }
    var longPressTriggered by remember { mutableStateOf(false) }
    var suppressNextActivation by remember { mutableStateOf(false) }
    val defaultFocusRequester = remember { FocusRequester() }
    val activeFocusRequester = focusRequester ?: defaultFocusRequester
    val interactionSource = remember { MutableInteractionSource() }
    val wiggleAmount = if (isMoving) {
        val wiggleTransition = rememberInfiniteTransition(label = "folderTileWiggle")
        val animatedWiggle by wiggleTransition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 220, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "folderTileWiggleAmount",
        )
        animatedWiggle
    } else {
        0f
    }

    LaunchedEffect(shouldRequestFocus, focusRequestToken) {
        if (shouldRequestFocus) {
            activeFocusRequester.requestFocus()
        }
    }

    val visualFocused = rawFocused && !suppressVisualFocus
    val focusShadowElevation by animateDpAsState(
        targetValue = if (visualFocused) AppGridFocusedShadowElevation else TileShadowElevation,
        animationSpec = tween(durationMillis = 90, easing = LinearOutSlowInEasing),
        label = "folderTileFocusShadowElevation",
    )
    val baseModifier = modifier.then(
        Modifier
            .size(width, height)
            .graphicsLayer {
                scaleX = if (visualFocused) 1.12f else 1f
                scaleY = if (visualFocused) 1.12f else 1f
                rotationZ = if (isMoving) wiggleAmount * 4f else 0f
            }
            .shadow(focusShadowElevation, shape, clip = false)
    )

    Box(
        modifier = baseModifier
            .onGloballyPositioned { coordinates ->
                onBoundsChanged?.invoke(coordinates.boundsInWindow())
            }
            .onFocusChanged {
                rawFocused = it.isFocused
                if (!it.isFocused) {
                    longPressTriggered = false
                }
                if (it.isFocused && shouldRequestFocus) {
                    onFocusRequested?.invoke()
                }
                onFocusChanged?.invoke(it.isFocused)
            }
            .focusRequester(activeFocusRequester)
            .onPreviewKeyEvent { event ->
                if (!moveModeActive && event.type == KeyEventType.KeyDown) {
                    onDirectionalKeyDown?.invoke(event.key)
                }
                if (moveModeActive && event.key == Key.Back) {
                    if (event.type == KeyEventType.KeyUp) {
                        onMoveDrop?.invoke()
                    }
                    return@onPreviewKeyEvent true
                }
                if (moveModeActive && onMoveNavigate != null) {
                    val direction = when (event.key) {
                        Key.DirectionLeft -> MoveDirection.Left
                        Key.DirectionRight -> MoveDirection.Right
                        Key.DirectionUp -> MoveDirection.Up
                        Key.DirectionDown -> MoveDirection.Down
                        else -> null
                    }
                    if (direction != null) {
                        if (event.type == KeyEventType.KeyDown) {
                            onMoveNavigate(direction)
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                val isSelectKey = event.key == Key.DirectionCenter ||
                        event.key == Key.Enter ||
                        event.key == Key.NumPadEnter
                if (!isSelectKey) return@onPreviewKeyEvent false
                if (moveModeActive) {
                    if (event.type == KeyEventType.KeyUp) {
                        onMoveDrop?.invoke()
                    }
                    true
                } else {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (event.nativeKeyEvent.isLongPress && !longPressTriggered) {
                                longPressTriggered = true
                                suppressNextActivation = true
                                onLongClick()
                                true
                            } else {
                                false
                            }
                        }
                        KeyEventType.KeyUp -> {
                            if (longPressTriggered || suppressNextActivation) {
                                longPressTriggered = false
                                suppressNextActivation = false
                                true
                            } else {
                                onClick()
                                true
                            }
                        }
                        else -> false
                    }
                }
            }
            .focusable(interactionSource = interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (moveModeActive) {
                        onMoveDrop?.invoke()
                    } else if (longPressTriggered || suppressNextActivation) {
                        longPressTriggered = false
                        suppressNextActivation = false
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    if (!moveModeActive) {
                        longPressTriggered = true
                        suppressNextActivation = true
                        onLongClick()
                    }
                }
            ),
        contentAlignment = Alignment.Center,
    ) {
        DockStyledSurface(
            modifier = Modifier.matchParentSize(),
            shape = shape,
            padding = PaddingValues(0.dp),
            darkModeEnabled = darkModeEnabled,
            contentFill = true,
            blurEnabled = true,
            frosted = false,
        ) {
            FolderArtworkPreview(
                folder = folder,
                apps = apps,
                iconOverrides = iconOverrides,
                darkModeEnabled = darkModeEnabled,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun FolderArtworkPreview(
    folder: LauncherFolder,
    apps: List<LauncherApp>,
    iconOverrides: Map<String, String>,
    darkModeEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val previewApps = apps.take(9)
    val previewIconWidth = 28.dp
    val previewIconHeight = 16.dp
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(TileCornerRadius))
            .padding(horizontal = 18.dp, vertical = 12.dp)
    ) {
        if (previewApps.isEmpty()) {
            Icon(
                imageVector = Icons.Outlined.Apps,
                contentDescription = folder.name,
                tint = Color.White.copy(alpha = 0.72f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(28.dp),
            )
        } else {
            Column(
                modifier = Modifier.align(Alignment.Center),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                previewApps.chunked(3).forEach { rowApps ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        rowApps.forEach { app ->
                            Box(
                                modifier = Modifier
                                    .width(previewIconWidth)
                                    .height(previewIconHeight)
                                    .clip(RoundedCornerShape(4.dp)),
                            ) {
                                AppIcon(
                                    app = app,
                                    iconOverride = iconOverrides[app.packageName],
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Fit,
                                    shouldLoadArtwork = true,
                                )
                            }
                        }
                        repeat(3 - rowApps.size) {
                            Spacer(modifier = Modifier.width(previewIconWidth).height(previewIconHeight))
                        }
                    }
                }
                repeat(3 - previewApps.chunked(3).size) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        repeat(3) {
                            Spacer(modifier = Modifier.width(previewIconWidth).height(previewIconHeight))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun FolderOverlay(
    folder: LauncherFolder,
    apps: List<LauncherApp>,
    iconOverrides: Map<String, String>,
    darkModeEnabled: Boolean,
    focusTargetPackage: String?,
    focusRestoreToken: Int,
    onFocusRestored: () -> Unit,
    moveModePackage: String?,
    onMoveDrop: () -> Unit,
    onMoveApp: (String, Int) -> Unit,
    onDismiss: () -> Unit,
    onRenameCommit: (String) -> Unit,
    onAppClick: (LauncherApp) -> Unit,
    onAppLongClick: (LauncherApp) -> Unit,
    onAppBoundsChanged: (LauncherApp, androidx.compose.ui.geometry.Rect) -> Unit,
    requestInlineRename: Boolean = false,
    onInlineRenameHandled: () -> Unit = {},
    labelOverrides: Map<String, String> = emptyMap(),
) {
    val moveModeActive = moveModePackage != null
    var isRenamingTitle by remember(folder.id) { mutableStateOf(false) }
    var renameInput by remember(folder.id) { mutableStateOf(TextFieldValue("")) }
    val renameFocusRequester = remember { FocusRequester() }
    val softwareKeyboardController = LocalSoftwareKeyboardController.current
    BackHandler {
        when {
            isRenamingTitle -> isRenamingTitle = false
            moveModeActive -> onMoveDrop()
            else -> onDismiss()
        }
    }
    val folderShape = RoundedCornerShape(44.dp)
    val noBringIntoViewSpec = remember {
        object : BringIntoViewSpec {
            override fun calculateScrollDistance(offset: Float, size: Float, containerSize: Float): Float = 0f
        }
    }
    val pages = remember(apps) { apps.chunked(9).ifEmpty { listOf(emptyList()) } }
    var currentPage by remember(folder.id) { mutableIntStateOf(0) }
    var pageDirection by remember(folder.id) { mutableIntStateOf(1) }
    var localFocusTarget by remember(folder.id, focusTargetPackage) {
        mutableStateOf(focusTargetPackage)
    }
    var localFocusToken by remember(folder.id, focusRestoreToken) {
        mutableIntStateOf(focusRestoreToken)
    }
    val titleFocusRequester = remember { FocusRequester() }
    var titleFocused by remember { mutableStateOf(false) }
    var titleSelectPressed by remember { mutableStateOf(false) }
    val titleShape = RoundedCornerShape(32.dp)

    LaunchedEffect(localFocusTarget) {
        if (localFocusTarget != null) {
            titleFocused = false
            titleSelectPressed = false
        }
    }

    LaunchedEffect(isRenamingTitle) {
        if (isRenamingTitle) {
            renameFocusRequester.requestFocus()
            softwareKeyboardController?.show()
        }
    }

    LaunchedEffect(requestInlineRename, folder.id) {
        if (requestInlineRename) {
            renameInput = TextFieldValue(
                text = folder.name,
                selection = TextRange(0, folder.name.length),
            )
            isRenamingTitle = true
            onInlineRenameHandled()
        }
    }

    fun targetPackageForPage(pageIndex: Int, row: Int, preferLastColumn: Boolean): String? {
        val pageApps = pages.getOrNull(pageIndex).orEmpty()
        if (pageApps.isEmpty()) return null
        val rowApps = pageApps.drop(row * 3).take(3)
        return if (preferLastColumn) {
            rowApps.lastOrNull()?.packageName ?: pageApps.lastOrNull()?.packageName
        } else {
            rowApps.firstOrNull()?.packageName ?: pageApps.firstOrNull()?.packageName
        }
    }

    LaunchedEffect(pages.size) {
        currentPage = currentPage.coerceIn(0, (pages.size - 1).coerceAtLeast(0))
    }

    LaunchedEffect(focusTargetPackage, focusRestoreToken, apps) {
        val targetPackage = focusTargetPackage ?: return@LaunchedEffect
        val pageIndex = pages.indexOfFirst { page ->
            page.any { app -> app.packageName == targetPackage }
        }
        if (pageIndex >= 0) {
            pageDirection = if (pageIndex >= currentPage) 1 else -1
            currentPage = pageIndex
            localFocusTarget = targetPackage
            localFocusToken = focusRestoreToken
        }
    }

    fun rowCountForPage(pageIndex: Int, row: Int): Int {
        val pageApps = pages.getOrNull(pageIndex).orEmpty()
        return pageApps.drop(row * 3).take(3).size
    }

    fun absoluteIndexForPageRowColumn(pageIndex: Int, row: Int, column: Int): Int? {
        val pageStart = pageIndex * 9
        val rowCount = rowCountForPage(pageIndex, row)
        if (column !in 0 until rowCount) return null
        val absoluteIndex = pageStart + row * 3 + column
        return absoluteIndex.takeIf { it in apps.indices }
    }

    fun firstIndexForPage(pageIndex: Int): Int? {
        val absoluteIndex = pageIndex * 9
        return absoluteIndex.takeIf { it in apps.indices }
    }

    fun firstIndexForPageMatchingRow(pageIndex: Int, preferredRow: Int): Int? {
        absoluteIndexForPageRowColumn(pageIndex, preferredRow, 0)?.let { return it }
        return (0..2).firstNotNullOfOrNull { rowIndex ->
            absoluteIndexForPageRowColumn(pageIndex, rowIndex, 0)
        }
    }

    fun lastIndexForPage(pageIndex: Int): Int? {
        val nextPageStart = ((pageIndex + 1) * 9).coerceAtMost(apps.size)
        val absoluteIndex = nextPageStart - 1
        return absoluteIndex.takeIf { it in apps.indices }
    }

    val handleMoveNavigateInFolder: (String, MoveDirection) -> Unit = moveNavigate@{ itemId, direction ->
        val movingPackage = moveModePackage ?: return@moveNavigate
        if (itemId != movingPackage) return@moveNavigate
        val currentIndex = apps.indexOfFirst { it.packageName == movingPackage }
        if (currentIndex < 0) return@moveNavigate

        val pageIndex = currentIndex / 9
        val indexInPage = currentIndex % 9
        val row = indexInPage / 3
        val column = indexInPage % 3
        val targetIndex = when (direction) {
            MoveDirection.Left -> when {
                column > 0 -> currentIndex - 1
                pageIndex > 0 -> lastIndexForPage(pageIndex - 1)
                else -> null
            }
            MoveDirection.Right -> when {
                absoluteIndexForPageRowColumn(pageIndex, row, column + 1) != null -> currentIndex + 1
                pageIndex < pages.lastIndex -> firstIndexForPageMatchingRow(pageIndex + 1, row)
                else -> null
            }
            MoveDirection.Up -> {
                if (row > 0) {
                    absoluteIndexForPageRowColumn(pageIndex, row - 1, column)
                } else {
                    null
                }
            }
            MoveDirection.Down -> {
                if (row < 2) {
                    absoluteIndexForPageRowColumn(pageIndex, row + 1, column)
                } else {
                    null
                }
            }
        } ?: return@moveNavigate

        if (targetIndex == currentIndex) return@moveNavigate
        onMoveApp(movingPackage, targetIndex)
        val targetPage = targetIndex / 9
        pageDirection = if (targetPage >= currentPage) 1 else -1
        currentPage = targetPage
        localFocusTarget = movingPackage
        localFocusToken += 1
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 88.dp, vertical = 28.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                scaleX = if (titleFocused) 1.08f else 1f
                                scaleY = if (titleFocused) 1.08f else 1f
                            }
                            .then(if (titleFocused) Modifier.shadow(4.dp, titleShape, clip = false) else Modifier)
                            .focusRequester(titleFocusRequester)
                            .onFocusChanged { titleFocused = it.isFocused }
                            .focusProperties {
                                canFocus = !isRenamingTitle && localFocusTarget == null && !moveModeActive
                            }
                            .onPreviewKeyEvent { event ->
                                val isSelectKey = event.key == Key.DirectionCenter ||
                                        event.key == Key.Enter ||
                                        event.key == Key.NumPadEnter
                                if (isSelectKey) {
                                    when (event.type) {
                                        KeyEventType.KeyDown -> {
                                            if (!isRenamingTitle) {
                                                titleSelectPressed = true
                                            }
                                            true
                                        }
                                        KeyEventType.KeyUp -> {
                                            if (isRenamingTitle) {
                                                onRenameCommit(renameInput.text)
                                                isRenamingTitle = false
                                            } else {
                                                titleSelectPressed = false
                                                renameInput = TextFieldValue(
                                                    text = folder.name,
                                                    selection = TextRange(0, folder.name.length),
                                                )
                                                isRenamingTitle = true
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                } else if (event.type == KeyEventType.KeyDown) {
                                    when (event.key) {
                                        Key.DirectionDown -> {
                                            if (!isRenamingTitle) {
                                                targetPackageForPage(
                                                    pageIndex = currentPage,
                                                    row = 0,
                                                    preferLastColumn = false,
                                                )?.let { targetPackage ->
                                                    localFocusTarget = targetPackage
                                                    localFocusToken += 1
                                                }
                                            }
                                            true
                                        }
                                        else -> false
                                    }
                                } else {
                                    false
                                }
                            }
                            .combinedClickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                onClick = {
                                    renameInput = TextFieldValue(
                                        text = folder.name,
                                        selection = TextRange(0, folder.name.length),
                                    )
                                    isRenamingTitle = true
                                    titleSelectPressed = false
                                },
                                onLongClick = {},
                            )
                            .focusable(),
                    ) {
                        if (isRenamingTitle) {
                            Box(
                                modifier = Modifier
                                    .clip(titleShape)
                                    .background(Color.White)
                                    .border(1.dp, Color.White, titleShape)
                                    .padding(horizontal = 20.dp, vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = renameInput.text,
                                    color = Color.Transparent,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center,
                                )
                                BasicTextField(
                                    value = renameInput,
                                    onValueChange = { renameInput = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = Color(0xFF141414),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                    ),
                                    cursorBrush = SolidColor(Color(0xFF141414)),
                                    modifier = Modifier
                                        .matchParentSize()
                                        .focusRequester(renameFocusRequester)
                                        .onPreviewKeyEvent { event ->
                                            if (event.type == KeyEventType.KeyUp &&
                                                (event.key == Key.Enter ||
                                                    event.key == Key.NumPadEnter ||
                                                    event.key == Key.DirectionCenter)
                                            ) {
                                                onRenameCommit(renameInput.text)
                                                isRenamingTitle = false
                                                true
                                            } else {
                                                false
                                            }
                                        },
                                )
                            }
                        } else {
                            val titleHighlighted = titleFocused || titleSelectPressed
                            if (titleHighlighted) {
                                Box(
                                    modifier = Modifier
                                        .clip(titleShape)
                                        .background(Color.White)
                                        .border(1.dp, Color.White, titleShape)
                                        .padding(horizontal = 20.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = folder.name,
                                        color = Color(0xFF141414),
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            } else {
                                DockStyledSurface(
                                    shape = titleShape,
                                    darkModeEnabled = darkModeEnabled,
                                    padding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                ) {
                                    Text(
                                        text = folder.name,
                                        color = if (darkModeEnabled) Color.White else Color.Black,
                                        fontSize = 24.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                        }
                    }
                    DockStyledSurface(
                        modifier = Modifier
                            .width(600.dp)
                            .height(400.dp),
                        shape = folderShape,
                        darkModeEnabled = darkModeEnabled,
                        padding = PaddingValues(0.dp),
                    ) {
                        CompositionLocalProvider(LocalBringIntoViewSpec provides noBringIntoViewSpec) {
                            if (apps.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "Folder is empty",
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 18.sp,
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AnimatedContent(
                                        targetState = currentPage,
                                        transitionSpec = {
                                            if (targetState > initialState) {
                                                slideInHorizontally(
                                                    animationSpec = tween(ARC_PAGE_FORWARD_DURATION_MS),
                                                    initialOffsetX = { it }
                                                ) togetherWith
                                                        slideOutHorizontally(
                                                            animationSpec = tween(ARC_PAGE_FORWARD_DURATION_MS),
                                                            targetOffsetX = { -it }
                                                        )
                                            } else {
                                                slideInHorizontally(
                                                    animationSpec = tween(ARC_PAGE_REVERSE_DURATION_MS),
                                                    initialOffsetX = { -it }
                                                ) togetherWith
                                                        slideOutHorizontally(
                                                            animationSpec = tween(ARC_PAGE_REVERSE_DURATION_MS),
                                                            targetOffsetX = { it }
                                                        )
                                            }
                                        },
                                        label = "folderPage",
                                    ) { pageIndex ->
                                        val pageApps = pages.getOrElse(pageIndex) { emptyList() }
                                        val pageSlots = List(9) { slotIndex -> pageApps.getOrNull(slotIndex) }
                                        val pageGridState = rememberLazyGridState()
                                        LazyVerticalGrid(
                                            modifier = Modifier
                                                .width(560.dp)
                                                .padding(vertical = 6.dp),
                                            columns = GridCells.Fixed(3),
                                            state = pageGridState,
                                            contentPadding = PaddingValues(
                                                start = 4.dp,
                                                top = 6.dp,
                                                end = 4.dp,
                                                bottom = 2.dp,
                                            ),
                                            verticalArrangement = Arrangement.spacedBy(14.dp),
                                            horizontalArrangement = Arrangement.spacedBy(30.dp),
                                        ) {
                                            gridItemsIndexed(
                                                pageSlots,
                                                key = { localIndex, app -> app?.packageName ?: "empty-$pageIndex-$localIndex" }
                                            ) { localIndex, app ->
                                                val row = localIndex / 3
                                                val column = localIndex % 3
                                                val rowAppsCount = pageApps.drop(row * 3).take(3).size
                                                val isLeftEdge = column == 0
                                                val isRightEdge = column >= rowAppsCount - 1

                                                if (app != null) {
                                                    DrawerAppCard(
                                                        app = app,
                                                        iconOverride = iconOverrides[app.packageName],
                                                        prioritizeArtwork = true,
                                                        deferredArtworkLoadEnabled = true,
                                                        onClick = { onAppClick(app) },
                                                        darkModeEnabled = darkModeEnabled,
                                                        onLongClick = { onAppLongClick(app) },
                                                        onFocus = {},
                                                        shouldRestoreFocus = localFocusTarget == app.packageName,
                                                        focusRestoreToken = localFocusToken,
                                                        onFocusRestored = {
                                                            localFocusTarget = null
                                                            onFocusRestored()
                                                        },
                                                        labelOverrides = labelOverrides,
                                                        moveModeActive = moveModeActive,
                                                        isMoving = moveModePackage == app.packageName,
                                                        moveTarget = MoveTarget.Grid(0, app.packageName),
                                                        onMoveFocus = {},
                                                        onMoveNavigate = handleMoveNavigateInFolder,
                                                        onMoveDrop = onMoveDrop,
                                                        onDirectionalKeyPreview = { key ->
                                                            if (moveModeActive) {
                                                                return@DrawerAppCard false
                                                            }
                                                            when {
                                                                key == Key.DirectionUp && row == 0 -> {
                                                                    titleFocusRequester.requestFocus()
                                                                    true
                                                                }
                                                                key == Key.DirectionLeft && isLeftEdge && pageIndex > 0 -> {
                                                                    targetPackageForPage(
                                                                        pageIndex = pageIndex - 1,
                                                                        row = row,
                                                                        preferLastColumn = true,
                                                                    )?.let { targetPackage ->
                                                                        pageDirection = -1
                                                                        currentPage = pageIndex - 1
                                                                        localFocusTarget = targetPackage
                                                                        localFocusToken += 1
                                                                    }
                                                                    true
                                                                }

                                                                key == Key.DirectionRight &&
                                                                        isRightEdge &&
                                                                        pageIndex < pages.lastIndex -> {
                                                                    targetPackageForPage(
                                                                        pageIndex = pageIndex + 1,
                                                                        row = row,
                                                                        preferLastColumn = false,
                                                                    )?.let { targetPackage ->
                                                                        pageDirection = 1
                                                                        currentPage = pageIndex + 1
                                                                        localFocusTarget = targetPackage
                                                                        localFocusToken += 1
                                                                    }
                                                                    true
                                                                }

                                                                else -> false
                                                            }
                                                        },
                                                        onBoundsChanged = { bounds ->
                                                            onAppBoundsChanged(app, bounds)
                                                        },
                                                    )
                                                } else {
                                                    Spacer(
                                                        modifier = Modifier
                                                            .fillMaxWidth()
                                                            .height(DrawerCardHeight + DrawerLabelSpacing + DrawerLabelHeight)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    if (pages.size > 1) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            repeat(pages.size) { pageIndex ->
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (pageIndex == currentPage) {
                                                Color.White.copy(alpha = 0.92f)
                                            } else {
                                                Color.White.copy(alpha = 0.34f)
                                            }
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun InputCard(
    label: String,
    iconOverride: String?,
    darkModeEnabled: Boolean,
    width: Dp = DrawerCardWidth,
    height: Dp = DrawerCardHeight,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    shouldRequestFocus: Boolean = false,
    focusRequestToken: Int = 0,
    onFocusRequested: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    onDirectionalKeyDown: ((Key) -> Unit)? = null,
    onBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    val context = LocalContext.current
    val shape = RoundedCornerShape(TileCornerRadius)
    var focused by remember { mutableStateOf(false) }
    var longPressTriggered by remember { mutableStateOf(false) }
    var suppressNextActivation by remember { mutableStateOf(false) }
    val defaultFocusRequester = remember { FocusRequester() }
    val activeFocusRequester = focusRequester ?: defaultFocusRequester
    val interactionSource = remember { MutableInteractionSource() }
    var overrideBitmap by remember(iconOverride) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(shouldRequestFocus, focusRequestToken) {
        if (shouldRequestFocus) {
            activeFocusRequester.requestFocus()
        }
    }

    LaunchedEffect(iconOverride) {
        overrideBitmap = withContext(Dispatchers.IO) {
            iconOverride?.let { uriString ->
                loadTileBitmapFromUri(context, Uri.parse(uriString))?.asImageBitmap()
            }
        }
    }

    val focusShadowElevation by animateDpAsState(
        targetValue = if (focused) AppGridFocusedShadowElevation else TileShadowElevation,
        animationSpec = tween(durationMillis = 90, easing = LinearOutSlowInEasing),
        label = "inputCardFocusShadowElevation",
    )
    val baseModifier = modifier.then(
        Modifier
            .size(width, height)
            .graphicsLayer {
                transformOrigin = TransformOrigin(0.5f, 0.5f)
                scaleX = if (focused) 1.18f else 1f
                scaleY = if (focused) 1.18f else 1f
            }
            .shadow(focusShadowElevation, shape, clip = false)
            .onGloballyPositioned { coordinates ->
                onBoundsChanged?.invoke(coordinates.boundsInWindow())
            }
            .onFocusChanged {
                focused = it.isFocused
                if (!it.isFocused) {
                    longPressTriggered = false
                }
                if (it.isFocused && shouldRequestFocus) {
                    onFocusRequested?.invoke()
                }
                onFocusChanged?.invoke(it.isFocused)
            }
            .focusRequester(activeFocusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown) {
                    onDirectionalKeyDown?.invoke(event.key)
                }
                val isSelectKey = event.key == Key.DirectionCenter ||
                        event.key == Key.Enter ||
                        event.key == Key.NumPadEnter
                if (!isSelectKey) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        if (event.nativeKeyEvent.isLongPress && !longPressTriggered) {
                            longPressTriggered = true
                            suppressNextActivation = true
                            onLongClick()
                            true
                        } else {
                            false
                        }
                    }
                    KeyEventType.KeyUp -> {
                        if (longPressTriggered || suppressNextActivation) {
                            longPressTriggered = false
                            suppressNextActivation = false
                            true
                        } else {
                            onClick()
                            true
                        }
                    }
                    else -> false
                }
            }
            .focusable(interactionSource = interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (longPressTriggered || suppressNextActivation) {
                        longPressTriggered = false
                        suppressNextActivation = false
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    longPressTriggered = true
                    suppressNextActivation = true
                    onLongClick()
                },
            )
    )

    Box(
        modifier = baseModifier,
        contentAlignment = Alignment.Center,
    ) {
        DockStyledSurface(
            modifier = Modifier.matchParentSize(),
            shape = shape,
            padding = PaddingValues(0.dp),
            darkModeEnabled = darkModeEnabled,
            contentFill = true,
            blurEnabled = false,
            frosted = false,
        ) {
            if (overrideBitmap != null) {
                Image(
                    bitmap = overrideBitmap!!,
                    contentDescription = label,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds,
                    filterQuality = FilterQuality.Low,
                )
            } else {
                // Inputs without custom artwork are intentionally solid. Fill
                // the entire tile so the label cannot reveal a second Haze or
                // translucent surface behind it.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            if (darkModeEnabled) Color(0xFF2A2A2A) else Color(0xFFEDEDED),
                            shape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (darkModeEnabled) Color.White else Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun AppCard(
    app: LauncherApp,
    iconOverride: String?,
    width: Dp,
    height: Dp,
    prioritizeArtwork: Boolean = false,
    deferredArtworkLoadEnabled: Boolean = true,
    focusedShadowElevation: Dp = TileShadowElevation,
    onClick: (() -> Unit)?,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    focusScaleOrigin: TransformOrigin = TransformOrigin(0.5f, 0.5f),
    suppressVisualFocus: Boolean = false,
    moveModeActive: Boolean = false,
    isMoving: Boolean = false,
    wiggle: Boolean = false,
    wiggleEmphasis: Boolean = false,
    onMoveDrop: (() -> Unit)? = null,
    onMoveNavigate: ((MoveDirection) -> Unit)? = null,
    onFocusChanged: ((Boolean) -> Unit)? = null,
    onDirectionalKeyPreview: ((Key) -> Boolean)? = null,
    shouldRequestFocus: Boolean = false,
    focusRequestToken: Int = 0,
    onFocusRequested: (() -> Unit)? = null,
    focusRequester: FocusRequester? = null,
    onDirectionalKeyDown: ((Key) -> Unit)? = null,
    onBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    val shape = RoundedCornerShape(TileCornerRadius)
    var rawFocused by remember { mutableStateOf(false) }
    var longPressTriggered by remember { mutableStateOf(false) }
    var suppressNextActivation by remember { mutableStateOf(false) }
    val defaultFocusRequester = remember { FocusRequester() }
    val activeFocusRequester = focusRequester ?: defaultFocusRequester
    val interactionSource = remember { MutableInteractionSource() }
    val wiggleAmount = if (wiggle) {
        val wiggleTransition = rememberInfiniteTransition(label = "cardWiggle")
        val animatedWiggle by wiggleTransition.animateFloat(
            initialValue = -1f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 220, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "cardWiggleAmount",
        )
        animatedWiggle
    } else {
        0f
    }

    LaunchedEffect(shouldRequestFocus, focusRequestToken) {
        if (shouldRequestFocus) {
            activeFocusRequester.requestFocus()
        }
    }

    val visualFocused = rawFocused && !suppressVisualFocus
    val focusScale by animateFloatAsState(
        targetValue = if (visualFocused) AppCardFocusScale else 1f,
        animationSpec = tween(durationMillis = 90, easing = LinearOutSlowInEasing),
        label = "appCardFocusScale",
    )
    val focusShadowElevation by animateDpAsState(
        targetValue = if (visualFocused) focusedShadowElevation else TileShadowElevation,
        animationSpec = tween(durationMillis = 90, easing = LinearOutSlowInEasing),
        label = "appCardFocusShadowElevation",
    )
    val focusFillAlpha by animateFloatAsState(
        targetValue = if (visualFocused) 0.18f else 0.14f,
        animationSpec = tween(durationMillis = 90, easing = LinearOutSlowInEasing),
        label = "appCardFocusFillAlpha",
    )
    val focusBorderAlpha by animateFloatAsState(
        targetValue = if (visualFocused) 0.3f else 0.16f,
        animationSpec = tween(durationMillis = 90, easing = LinearOutSlowInEasing),
        label = "appCardFocusBorderAlpha",
    )

    val baseModifier = modifier.then(
        Modifier
            .size(width, height)
            .graphicsLayer {
                transformOrigin = focusScaleOrigin
                scaleX = focusScale
                scaleY = focusScale
                if (wiggle) {
                    val amplitude = if (wiggleEmphasis) 4f else 2.2f
                    rotationZ = wiggleAmount * amplitude
                } else {
                    rotationZ = 0f
                }
            }
            .shadow(focusShadowElevation, shape, clip = false)
            .clip(shape)
            .background(
                Color.White.copy(alpha = focusFillAlpha)
            )
            .border(
                1.dp,
                Color.White.copy(alpha = focusBorderAlpha),
                shape
            )
            .onGloballyPositioned { coordinates ->
                onBoundsChanged?.invoke(coordinates.boundsInWindow())
            }
    )

    val interactiveModifier = if (onClick != null && onLongClick != null) {
        Modifier
            .onFocusChanged {
                rawFocused = it.isFocused
                if (!it.isFocused) {
                    longPressTriggered = false
                }
                if (it.isFocused && shouldRequestFocus) {
                    onFocusRequested?.invoke()
                }
                onFocusChanged?.invoke(it.isFocused)
            }
            .focusRequester(activeFocusRequester)
            .onPreviewKeyEvent { event ->
                if (!moveModeActive && event.type == KeyEventType.KeyDown) {
                    if (onDirectionalKeyPreview?.invoke(event.key) == true) {
                        return@onPreviewKeyEvent true
                    }
                    onDirectionalKeyDown?.invoke(event.key)
                }
                if (moveModeActive && event.key == Key.Back) {
                    if (event.type == KeyEventType.KeyUp) {
                        onMoveDrop?.invoke()
                    }
                    return@onPreviewKeyEvent true
                }
                if (moveModeActive && onMoveNavigate != null) {
                    val direction = when (event.key) {
                        Key.DirectionLeft -> MoveDirection.Left
                        Key.DirectionRight -> MoveDirection.Right
                        Key.DirectionUp -> MoveDirection.Up
                        Key.DirectionDown -> MoveDirection.Down
                        else -> null
                    }
                    if (direction != null) {
                        if (event.type == KeyEventType.KeyDown) {
                            onMoveNavigate(direction)
                        }
                        return@onPreviewKeyEvent true
                    }
                }
                val isSelectKey = event.key == Key.DirectionCenter ||
                        event.key == Key.Enter ||
                        event.key == Key.NumPadEnter
                if (!isSelectKey) return@onPreviewKeyEvent false
                if (moveModeActive) {
                    if (event.type == KeyEventType.KeyUp) {
                        onMoveDrop?.invoke()
                    }
                    true
                } else {
                    when (event.type) {
                        KeyEventType.KeyDown -> {
                            if (event.nativeKeyEvent.isLongPress && !longPressTriggered) {
                                longPressTriggered = true
                                suppressNextActivation = true
                                onLongClick()
                                true
                            } else {
                                false
                            }
                        }
                        KeyEventType.KeyUp -> {
                            if (longPressTriggered || suppressNextActivation) {
                                longPressTriggered = false
                                suppressNextActivation = false
                                true
                            } else {
                                onClick()
                                true
                            }
                        }
                        else -> false
                    }
                }
            }
            .focusable(interactionSource = interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    if (moveModeActive) {
                        onMoveDrop?.invoke()
                    } else if (longPressTriggered || suppressNextActivation) {
                        longPressTriggered = false
                        suppressNextActivation = false
                    } else {
                        onClick()
                    }
                },
                onLongClick = {
                    if (!moveModeActive) {
                        longPressTriggered = true
                        suppressNextActivation = true
                        onLongClick()
                    }
                }
            )
    } else {
        Modifier
    }

    Box(
        modifier = baseModifier.then(interactiveModifier),
        contentAlignment = Alignment.Center,
    ) {
        AppIcon(
            app = app,
            iconOverride = iconOverride,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.FillBounds,
            shouldLoadArtwork = prioritizeArtwork || deferredArtworkLoadEnabled || rawFocused,
        )
    }
}

@Composable
internal fun AppIcon(
    app: LauncherApp,
    iconOverride: String?,
    modifier: Modifier,
    contentScale: ContentScale,
    shouldLoadArtwork: Boolean,
) {
    val context = LocalContext.current
    var overrideBitmap by remember(iconOverride) { mutableStateOf<ImageBitmap?>(null) }
    var artworkBitmap by remember(app.packageName, app.componentName, iconOverride) {
        mutableStateOf(getCachedAppArtwork(app)?.asImageBitmap())
    }

    LaunchedEffect(iconOverride) {
        overrideBitmap = withContext(Dispatchers.IO) {
            iconOverride?.let { uriString ->
                loadTileBitmapFromUri(context, Uri.parse(uriString))?.asImageBitmap()
            }
        }
    }
    LaunchedEffect(app.packageName, app.componentName, shouldLoadArtwork, iconOverride != null) {
        if (!shouldLoadArtwork && iconOverride == null) return@LaunchedEffect
        if (artworkBitmap == null) {
            artworkBitmap = getCachedAppArtwork(app)?.asImageBitmap()
        }
        if (artworkBitmap == null && shouldLoadArtwork) {
            artworkBitmap = withContext(Dispatchers.IO) {
                loadAppArtwork(context, app)?.asImageBitmap()
            }
        }
    }

    val image = overrideBitmap ?: artworkBitmap
    if (image != null) {
        Image(
            bitmap = image,
            contentDescription = app.label,
            modifier = modifier,
            contentScale = contentScale,
            filterQuality = FilterQuality.Low,
        )
    } else {
        // Keep the fallback transparent so the tile's surface remains one
        // consistent solid shape. A separate translucent rectangle here made
        // tiles without artwork (including input-style tiles) look like they
        // contained a square cutout of the wallpaper.
        Box(modifier = modifier)
    }
}

@Composable
internal fun NetworkIcon(status: NetworkStatus, tint: Color = Color.White) {
    val icon = when {
        !status.hasInternet -> Icons.Outlined.WifiOff
        status.transport == TransportType.ETHERNET -> Icons.Outlined.SettingsEthernet
        else -> Icons.Outlined.Wifi
    }
    Image(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier.size(20.dp),
        colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(tint),
    )
}

@Composable
internal fun GlassIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    darkModeEnabled: Boolean = false,
    contentColor: Color = Color.White,
    icon: @Composable () -> ImageVector,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val shape = RoundedCornerShape(TileCornerRadius)
    val backgroundColor = if (isFocused) {
        if (darkModeEnabled) Color(0xFF4A4A4A) else Color.White
    } else {
        Color.Transparent
    }
    val borderColor = if (isFocused) {
        if (darkModeEnabled) Color(0xFF4A4A4A) else Color.White
    } else {
        Color.Transparent
    }
    val iconTint = if (isFocused) {
        if (darkModeEnabled) Color.White else Color(0xFF141414)
    } else {
        contentColor
    }
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(shape)
            .background(backgroundColor)
            .border(1.dp, borderColor, shape)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {},
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            imageVector = icon(),
            contentDescription = label,
            modifier = Modifier.size(18.dp),
            colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(iconTint),
        )
    }
}

@Composable
internal fun GlassTextIconButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .size(34.dp)
            .graphicsLayer {
                scaleX = if (focused) 1.12f else 1f
                scaleY = if (focused) 1.12f else 1f
            }
            .clip(CircleShape)
            .background(if (focused) GlassFillFocused else GlassFill)
            .border(1.dp, if (focused) GlassBorderFocused else GlassBorder, CircleShape)
            .shadow(if (focused) 8.dp else 2.dp, CircleShape, clip = false)
            .onFocusChanged { focused = it.isFocused }
            .combinedClickable(onClick = onClick, onLongClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
        )
    }
}

@Composable
internal fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
    padding: PaddingValues,
    darkModeEnabled: Boolean = false,
    contentFill: Boolean = false,
    translucent: Boolean = false,
    hazeState: HazeState? = null,
    hazeStyle: HazeBlurStyle? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val useHaze = hazeState != null && hazeStyle != null
    val fillColor = if (useHaze) {
        Color.Transparent
    } else if (translucent) {
        frostedGlassTint(darkModeEnabled).copy(
            alpha = if (darkModeEnabled) 0.88f else 0.84f,
        )
    } else {
        // Keep the solid-surface appearance while allowing a small amount of
        // the selected background to show through.
        frostedGlassTint(darkModeEnabled).copy(alpha = 0.82f)
    }
    val borderColor = if (darkModeEnabled) {
        Color.White.copy(alpha = 0.10f)
    } else {
        Color.White.copy(alpha = 0.55f)
    }
    val hazeModifier = if (useHaze) {
        Modifier.hazeBlur(
            input = HazeInput.Sources(hazeState),
            style = hazeStyle,
            performanceMode = HazePerformanceMode.Performance,
            expandLayerBounds = false,
        )
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .clip(shape)
            // Clip before Haze so its capture/effect layer cannot form a
            // rectangular halo outside the rounded surface.
            .then(hazeModifier)
            .background(fillColor, shape)
            .border(1.dp, borderColor, shape)
    ) {
        val contentModifier = if (contentFill) {
            Modifier
                .fillMaxSize()
                .padding(padding)
        } else {
            Modifier.padding(padding)
        }
        Box(
            modifier = contentModifier,
            content = content,
        )
    }
}

@Composable
@Suppress("UNUSED_PARAMETER")
internal fun DockStyledSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape,
    padding: PaddingValues,
    darkModeEnabled: Boolean,
    contentFill: Boolean = false,
    blurEnabled: Boolean = true,
    frosted: Boolean = false,
    backdropBitmap: ImageBitmap? = null,
    backdropPreset: BackgroundPreset? = null,
    backdropOffsetY: Dp = 0.dp,
    backdropHeight: Dp = 0.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    val hazeState = LocalLauncherHazeState.current
    GlassSurface(
        modifier = modifier,
        shape = shape,
        padding = padding,
        darkModeEnabled = darkModeEnabled,
        contentFill = contentFill,
        translucent = true,
        hazeState = hazeState.takeIf { blurEnabled },
        hazeStyle = hazeState?.let { launcherHazeStyle() }
            .takeIf { blurEnabled },
        content = content,
    )
}
