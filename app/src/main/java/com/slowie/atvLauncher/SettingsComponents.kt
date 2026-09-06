@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalTvMaterial3Api::class,
)

package com.slowie.atvLauncher

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed as gridItemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private const val LONG_PRESS_THRESHOLD_MS = 600L

private suspend fun requestVisibleListItemFocus(
    listState: LazyListState,
    index: Int,
    requester: FocusRequester?,
    allowScrollCorrection: Boolean = true,
) {
    if (requester == null) return

    withFrameNanos { }
    snapshotFlow { listState.layoutInfo.visibleItemsInfo.isNotEmpty() }
        .first { it }

    val totalItems = listState.layoutInfo.totalItemsCount
    if (totalItems == 0) return

    val safeIndex = index.coerceIn(0, totalItems - 1)
    val layoutInfo = listState.layoutInfo
    val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == safeIndex }
    val isFullyVisible = itemInfo != null &&
        itemInfo.offset >= layoutInfo.viewportStartOffset &&
        itemInfo.offset + itemInfo.size <= layoutInfo.viewportEndOffset

    if (allowScrollCorrection && !isFullyVisible) {
        listState.scrollToItem(safeIndex)
        snapshotFlow {
            listState.layoutInfo.visibleItemsInfo.any { it.index == safeIndex }
        }.first { it }
    }

    withFrameNanos { }
    requester.requestFocus()
}

@Composable
internal fun LauncherSettingsScreen(
    onDismiss: () -> Unit,
    onAppearance: () -> Unit,
    onApps: () -> Unit,
    onHomeButtonTakeover: () -> Unit,
    onSystem: () -> Unit,
    onNetwork: () -> Unit,
    onDevicePreferences: () -> Unit,
    onDeveloper: () -> Unit,
    onDateTime: () -> Unit,
    onDisplaySound: () -> Unit,
    onAbout: () -> Unit,
    onPowerOff: () -> Unit,
    initialFocusIndex: Int,
    initialScrollIndex: Int,
    initialScrollOffset: Int,
    onScrollPositionChanged: (Int, Int) -> Unit,
    onOptionFocused: (Int) -> Unit,
    darkModeEnabled: Boolean,
) {
    BackHandler(onBack = onDismiss)
    val palette = menuPalette(darkModeEnabled)
    val options = remember {
        listOf(
            SettingOption("Appearance", onAppearance),
            SettingOption("Apps", onApps),
            SettingOption("Home Button Takeover", onHomeButtonTakeover),
            SettingOption("System", onSystem),
            SettingOption("Network", onNetwork),
            SettingOption("Device Preferences", onDevicePreferences),
            SettingOption("Display & Sound", onDisplaySound),
            SettingOption("Developer", onDeveloper),
            SettingOption("Date & Time", onDateTime),
            SettingOption("About", onAbout),
            SettingOption("Power Off", onPowerOff),
        )
    }
    val focusRequesters = remember { List(options.size) { FocusRequester() } }
    var lastFocusedIndex by remember(initialFocusIndex) {
        mutableStateOf(initialFocusIndex.coerceIn(0, options.size - 1))
    }
    val lastFocusedIndexState = rememberUpdatedState(lastFocusedIndex)
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollIndex.coerceIn(0, options.size - 1),
        initialFirstVisibleItemScrollOffset = initialScrollOffset.coerceAtLeast(0),
    )
    val coroutineScope = rememberCoroutineScope()
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    DisposableEffect(listState) {
        onDispose {
            onScrollPositionChanged(
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset,
            )
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val targetIndex = lastFocusedIndexState.value.coerceIn(0, options.size - 1)
                scrollJob?.cancel()
                scrollJob = coroutineScope.launch {
                    requestVisibleListItemFocus(
                        listState = listState,
                        index = targetIndex,
                        requester = focusRequesters.getOrNull(targetIndex),
                        allowScrollCorrection = false,
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(options.size) {
        val targetIndex = lastFocusedIndex.coerceIn(0, options.size - 1)
        scrollJob?.cancel()
        scrollJob = coroutineScope.launch {
            requestVisibleListItemFocus(
                listState = listState,
                index = targetIndex,
                requester = focusRequesters.getOrNull(targetIndex),
                allowScrollCorrection = false,
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Text(
            text = "Settings",
            color = palette.title,
            fontSize = 40.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 96.dp, vertical = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SettingsIconTile(palette)
            Box(
                modifier = Modifier
                    .width(780.dp)
                    .height(560.dp)
                    .focusGroup(),
            ) {
                val canScrollUp by remember { derivedStateOf { listState.canScrollBackward } }
                val canScrollDown by remember { derivedStateOf { listState.canScrollForward } }
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            val topAlpha = if (canScrollUp) 0f else 1f
                            val bottomAlpha = if (canScrollDown) 0f else 1f
                            val fadeBrush = Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = topAlpha),
                                0.12f to Color.Black,
                                0.88f to Color.Black,
                                1f to Color.Black.copy(alpha = bottomAlpha),
                            )
                            drawContent()
                            drawRect(brush = fadeBrush, blendMode = BlendMode.DstIn)
                        },
                ) {
                    itemsIndexed(options) { index, option ->
                        val requester = focusRequesters[index]
                        val upRequester = focusRequesters.getOrNull(index - 1)
                        val downRequester = focusRequesters.getOrNull(index + 1)
                        SettingsListItem(
                            label = option.label,
                            onClick = option.onClick,
                            onFocused = {
                                lastFocusedIndex = index
                                onOptionFocused(index)
                                val layoutInfo = listState.layoutInfo
                                val totalItems = layoutInfo.totalItemsCount
                                if (totalItems == 0) return@SettingsListItem
                                val safeIndex = index.coerceIn(0, totalItems - 1)
                                val viewportStart = layoutInfo.viewportStartOffset
                                val viewportEnd = layoutInfo.viewportEndOffset
                                val itemInfo = layoutInfo.visibleItemsInfo
                                    .firstOrNull { it.index == safeIndex }
                                val isFullyVisible = itemInfo != null &&
                                    itemInfo.offset >= viewportStart &&
                                    itemInfo.offset + itemInfo.size <= viewportEnd
                                if (!isFullyVisible) {
                                    scrollJob?.cancel()
                                    scrollJob = coroutineScope.launch {
                                        listState.scrollToItem(safeIndex)
                                    }
                                }
                            },
                            focusRequester = requester,
                            upRequester = upRequester,
                            downRequester = downRequester,
                            palette = palette,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeButtonTakeoverSettingsScreen(
    onDismiss: () -> Unit,
    onOpenAccessibilitySettings: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onLaunchNow: () -> Unit,
    darkModeEnabled: Boolean,
    initialFocusIndex: Int,
    onOptionFocused: (Int) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val palette = menuPalette(darkModeEnabled)
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val focusRequesters = remember { List(3) { FocusRequester() } }
    var lastFocusedIndex by remember(initialFocusIndex) {
        mutableStateOf(initialFocusIndex.coerceIn(0, focusRequesters.size - 1))
    }
    val lastFocusedIndexState = rememberUpdatedState(lastFocusedIndex)
    val coroutineScope = rememberCoroutineScope()
    var homeTakeoverEnabled by remember { mutableStateOf(isHomeCaptureServiceEnabled(context)) }

    LaunchedEffect(focusRequesters.size) {
        val targetIndex = lastFocusedIndex.coerceIn(0, focusRequesters.size - 1)
        withFrameNanos { }
        focusRequesters.getOrNull(targetIndex)?.requestFocus()
    }

    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                homeTakeoverEnabled = isHomeCaptureServiceEnabled(context)
                coroutineScope.launch {
                    withFrameNanos { }
                    focusRequesters.getOrNull(lastFocusedIndexState.value)?.requestFocus()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Text(
            text = "Home Button Takeover",
            color = palette.title,
            fontSize = 40.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 96.dp, vertical = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeTakeoverIconTile(palette)
            Column(
                modifier = Modifier.width(620.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(
                    text = if (homeTakeoverEnabled) {
                        "Status: Enabled in Accessibility"
                    } else {
                        "Status: Disabled"
                    },
                    color = palette.itemText,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                )
                SettingsListItem(
                    label = if (homeTakeoverEnabled) {
                        "Open Accessibility Settings"
                    } else {
                        "Enable in Accessibility"
                    },
                    onClick = onOpenAccessibilitySettings,
                    onFocused = {
                        lastFocusedIndex = 0
                        onOptionFocused(0)
                    },
                    focusRequester = focusRequesters[0],
                    upRequester = null,
                    downRequester = focusRequesters[1],
                    palette = palette,
                )
                SettingsListItem(
                    label = "Open Launcher App Info",
                    onClick = onOpenAppInfo,
                    onFocused = {
                        lastFocusedIndex = 1
                        onOptionFocused(1)
                    },
                    focusRequester = focusRequesters[1],
                    upRequester = focusRequesters[0],
                    downRequester = focusRequesters[2],
                    palette = palette,
                )
                SettingsListItem(
                    label = "Launch Launcher Now",
                    onClick = onLaunchNow,
                    onFocused = {
                        lastFocusedIndex = 2
                        onOptionFocused(2)
                    },
                    focusRequester = focusRequesters[2],
                    upRequester = focusRequesters[1],
                    downRequester = null,
                    palette = palette,
                )
            }
        }
    }
}

@Composable
private fun HomeTakeoverIconTile(palette: MenuPalette) {
    Box(
        modifier = Modifier
            .size(300.dp)
            .clip(RoundedCornerShape(60.dp))
            .background(palette.iconTile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Home,
            contentDescription = null,
            tint = palette.iconGlyph,
            modifier = Modifier.size(130.dp),
        )
    }
}

@Composable
internal fun AppearanceSettingsScreen(
    onDismiss: () -> Unit,
    onBackgroundImage: () -> Unit,
    darkModeEnabled: Boolean,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    initialFocusIndex: Int,
    onOptionFocused: (Int) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val palette = menuPalette(darkModeEnabled)
    val focusRequesters = remember { List(4) { FocusRequester() } }
    var lastFocusedIndex by remember(initialFocusIndex) {
        mutableStateOf(initialFocusIndex.coerceIn(0, focusRequesters.size - 1))
    }
    val lastFocusedIndexState = rememberUpdatedState(lastFocusedIndex)
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(focusRequesters.size) {
        val targetIndex = lastFocusedIndex.coerceIn(0, focusRequesters.size - 1)
        withFrameNanos { }
        focusRequesters.getOrNull(targetIndex)?.requestFocus()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    withFrameNanos { }
                    focusRequesters.getOrNull(lastFocusedIndexState.value)?.requestFocus()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Text(
            text = "Appearance",
            color = palette.title,
            fontSize = 40.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 96.dp, vertical = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppearanceIconTile(palette)
            Column(
                modifier = Modifier.width(540.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val backgroundRequester = focusRequesters[0]
                val lightModeRequester = focusRequesters[1]
                val darkModeRequester = focusRequesters[2]
                val systemModeRequester = focusRequesters[3]
                SettingsListItem(
                    label = "Background Image",
                    onClick = onBackgroundImage,
                    onFocused = {
                        lastFocusedIndex = 0
                        onOptionFocused(0)
                    },
                    focusRequester = backgroundRequester,
                    upRequester = null,
                    downRequester = lightModeRequester,
                    palette = palette,
                )
                SettingsSelectableItem(
                    label = "Light Mode",
                    selected = themeMode == ThemeMode.LIGHT,
                    onClick = {
                        onThemeModeChange(ThemeMode.LIGHT)
                        lightModeRequester.requestFocus()
                    },
                    onFocused = {
                        lastFocusedIndex = 1
                        onOptionFocused(1)
                    },
                    focusRequester = lightModeRequester,
                    upRequester = backgroundRequester,
                    downRequester = darkModeRequester,
                    palette = palette,
                )
                SettingsSelectableItem(
                    label = "Dark Mode",
                    selected = themeMode == ThemeMode.DARK,
                    onClick = {
                        onThemeModeChange(ThemeMode.DARK)
                        darkModeRequester.requestFocus()
                    },
                    onFocused = {
                        lastFocusedIndex = 2
                        onOptionFocused(2)
                    },
                    focusRequester = darkModeRequester,
                    upRequester = lightModeRequester,
                    downRequester = systemModeRequester,
                    palette = palette,
                )
                SettingsSelectableItem(
                    label = "System",
                    selected = themeMode == ThemeMode.SYSTEM,
                    onClick = {
                        onThemeModeChange(ThemeMode.SYSTEM)
                        systemModeRequester.requestFocus()
                    },
                    onFocused = {
                        lastFocusedIndex = 3
                        onOptionFocused(3)
                    },
                    focusRequester = systemModeRequester,
                    upRequester = darkModeRequester,
                    downRequester = null,
                    palette = palette,
                )
            }
        }
    }
}

@Composable
internal fun BackgroundImageSettingsScreen(
    onDismiss: () -> Unit,
    onDefaultOptions: () -> Unit,
    onChooseFromDevice: () -> Unit,
    onSetFromUrl: () -> Unit,
    darkModeEnabled: Boolean,
    initialFocusIndex: Int,
    onOptionFocused: (Int) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val palette = menuPalette(darkModeEnabled)
    val focusRequesters = remember { List(3) { FocusRequester() } }
    var lastFocusedIndex by remember(initialFocusIndex) {
        mutableStateOf(initialFocusIndex.coerceIn(0, focusRequesters.size - 1))
    }
    val lastFocusedIndexState = rememberUpdatedState(lastFocusedIndex)
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(focusRequesters.size) {
        val targetIndex = lastFocusedIndex.coerceIn(0, focusRequesters.size - 1)
        withFrameNanos { }
        focusRequesters.getOrNull(targetIndex)?.requestFocus()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                coroutineScope.launch {
                    withFrameNanos { }
                    focusRequesters.getOrNull(lastFocusedIndexState.value)?.requestFocus()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Text(
            text = "Background Image",
            color = palette.title,
            fontSize = 40.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 96.dp, vertical = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BackgroundImageIconTile(palette)
            Column(
                modifier = Modifier.width(540.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                val defaultRequester = focusRequesters[0]
                val deviceRequester = focusRequesters[1]
                val urlRequester = focusRequesters[2]
                SettingsListItem(
                    label = "Default Options",
                    onClick = onDefaultOptions,
                    onFocused = {
                        lastFocusedIndex = 0
                        onOptionFocused(0)
                    },
                    focusRequester = defaultRequester,
                    upRequester = null,
                    downRequester = deviceRequester,
                    palette = palette,
                )
                SettingsListItem(
                    label = "Choose From Device",
                    onClick = onChooseFromDevice,
                    onFocused = {
                        lastFocusedIndex = 1
                        onOptionFocused(1)
                    },
                    focusRequester = deviceRequester,
                    upRequester = defaultRequester,
                    downRequester = urlRequester,
                    palette = palette,
                )
                SettingsListItem(
                    label = "Set From URL",
                    onClick = onSetFromUrl,
                    onFocused = {
                        lastFocusedIndex = 2
                        onOptionFocused(2)
                    },
                    focusRequester = urlRequester,
                    upRequester = deviceRequester,
                    downRequester = null,
                    palette = palette,
                )
            }
        }
    }
}

@Composable
internal fun BackgroundPresetGridScreen(
    presets: List<BackgroundPreset>,
    lightModeSelectedId: String?,
    darkModeSelectedId: String?,
    onSelect: (BackgroundPreset) -> Unit,
    onLongSelect: (BackgroundPreset, Rect) -> Unit,
    onDismiss: () -> Unit,
    darkModeEnabled: Boolean,
    initialFocusIndex: Int,
    onOptionFocused: (Int) -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val palette = menuPalette(darkModeEnabled)
    val safeInitialIndex = initialFocusIndex.coerceIn(0, maxOf(presets.lastIndex, 0))
    var focusedIndex by remember { mutableStateOf(safeInitialIndex) }
    val rootRequester = remember { FocusRequester() }
    var rootReady by remember { mutableStateOf(false) }
    var rootHasFocus by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()
    val coroutineScope = rememberCoroutineScope()
    var selectKeyDown by remember { mutableStateOf(false) }
    var longPressFired by remember { mutableStateOf(false) }
    var longPressTarget by remember { mutableStateOf<BackgroundPreset?>(null) }
    var longPressBounds by remember { mutableStateOf<Rect?>(null) }
    var focusedTileBounds by remember { mutableStateOf<Rect?>(null) }
    var longPressJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(rootReady) {
        if (rootReady) {
            rootRequester.requestFocus()
        }
    }

    LaunchedEffect(rootReady, rootHasFocus) {
        if (rootReady && !rootHasFocus) {
            rootRequester.requestFocus()
        }
    }

    LaunchedEffect(focusedIndex) {
        onOptionFocused(focusedIndex)
        if (presets.isNotEmpty()) {
            gridState.animateScrollToItem(focusedIndex)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            longPressJob?.cancel()
            longPressJob = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.background)
                .focusRequester(rootRequester)
                .onGloballyPositioned { rootReady = true }
                .onFocusChanged { rootHasFocus = it.isFocused }
                .focusable()
                .onPreviewKeyEvent { event ->
                    val key = event.key
                    val isNavKey = key == Key.DirectionLeft ||
                        key == Key.DirectionRight ||
                        key == Key.DirectionUp ||
                        key == Key.DirectionDown
                    val isSelectKey = key == Key.DirectionCenter ||
                        key == Key.Enter ||
                        key == Key.NumPadEnter
                    if (event.type == KeyEventType.KeyDown) {
                        when {
                            isNavKey && presets.isNotEmpty() -> {
                                val columns = 3
                                val col = focusedIndex % columns
                                when (key) {
                                    Key.DirectionLeft -> {
                                        if (col > 0) focusedIndex -= 1
                                    }
                                    Key.DirectionRight -> {
                                        if (col < columns - 1 && focusedIndex + 1 < presets.size) {
                                            focusedIndex += 1
                                        }
                                    }
                                    Key.DirectionUp -> {
                                        val target = focusedIndex - columns
                                        if (target >= 0) focusedIndex = target
                                    }
                                    Key.DirectionDown -> {
                                        val target = focusedIndex + columns
                                        if (target < presets.size) focusedIndex = target
                                    }
                                    else -> Unit
                                }
                                true
                            }
                            isSelectKey -> {
                                if (!selectKeyDown) {
                                    selectKeyDown = true
                                    longPressFired = false
                                    val target = presets.getOrNull(focusedIndex)
                                    longPressTarget = target
                                    longPressBounds = focusedTileBounds
                                    if (target != null) {
                                        longPressJob?.cancel()
                                        longPressJob = coroutineScope.launch {
                                            delay(LONG_PRESS_THRESHOLD_MS)
                                            longPressFired = true
                                            onLongSelect(target, longPressBounds ?: Rect.Zero)
                                        }
                                    }
                                }
                                true
                            }
                            else -> false
                        }
                    } else if (event.type == KeyEventType.KeyUp) {
                        if (isSelectKey) {
                            longPressJob?.cancel()
                            longPressJob = null
                            val fired = longPressFired
                            val target = longPressTarget
                            longPressFired = false
                            selectKeyDown = false
                            longPressTarget = null
                            if (!fired && target != null) {
                                onSelect(target)
                            }
                            true
                        } else {
                            isNavKey
                        }
                    } else {
                        false
                    }
                }
        ) {
            val headerHeight = 96.dp
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(headerHeight)
                    .zIndex(1f)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(headerHeight)
                        .background(palette.background)
                )
                Text(
                    text = "Choose Background",
                    color = palette.title,
                    fontSize = 40.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 84.dp)
                    .zIndex(0f),
                contentPadding = PaddingValues(top = headerHeight + 24.dp, bottom = 32.dp),
                state = gridState,
                verticalArrangement = Arrangement.spacedBy(22.dp),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
            ) {
                gridItemsIndexed(presets) { index, preset ->
                    BackgroundPresetTile(
                        preset = preset,
                        isLightModeSelected = preset.id == lightModeSelectedId,
                        isDarkModeSelected = preset.id == darkModeSelectedId,
                        palette = palette,
                        onClick = { onSelect(preset) },
                        onLongClick = { onLongSelect(preset, focusedTileBounds ?: Rect.Zero) },
                        focused = index == focusedIndex,
                        onBoundsChanged = { bounds ->
                            if (index == focusedIndex) {
                                focusedTileBounds = bounds
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsIconTile(palette: MenuPalette) {
    Box(
        modifier = Modifier
            .size(300.dp)
            .clip(RoundedCornerShape(60.dp))
            .background(palette.iconTile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = null,
            tint = palette.iconGlyph,
            modifier = Modifier.size(130.dp),
        )
    }
}

@Composable
private fun AppearanceIconTile(palette: MenuPalette) {
    Box(
        modifier = Modifier
            .size(300.dp)
            .clip(RoundedCornerShape(60.dp))
            .background(palette.iconTile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Brush,
            contentDescription = null,
            tint = palette.iconGlyph,
            modifier = Modifier.size(130.dp),
        )
    }
}

@Composable
private fun BackgroundImageIconTile(palette: MenuPalette) {
    Box(
        modifier = Modifier
            .size(300.dp)
            .clip(RoundedCornerShape(60.dp))
            .background(palette.iconTile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Image,
            contentDescription = null,
            tint = palette.iconGlyph,
            modifier = Modifier.size(130.dp),
        )
    }
}

@Composable
private fun BackgroundPresetTile(
    preset: BackgroundPreset,
    isLightModeSelected: Boolean,
    isDarkModeSelected: Boolean,
    palette: MenuPalette,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    focused: Boolean,
    onBoundsChanged: (Rect) -> Unit = {},
) {
    val shape = RoundedCornerShape(22.dp)
    val highlight = focused
    val borderColor = when {
        highlight -> Color.White
        isLightModeSelected || isDarkModeSelected -> Color.White.copy(alpha = 0.7f)
        else -> Color.White.copy(alpha = 0.25f)
    }
    val borderWidth = when {
        highlight -> 3.dp
        isLightModeSelected || isDarkModeSelected -> 2.dp
        else -> 1.dp
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(280.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 280.dp, height = 168.dp)
                .graphicsLayer {
                    transformOrigin = TransformOrigin(0.5f, 0.5f)
                    scaleX = if (highlight) 1.03f else 1f
                    scaleY = if (highlight) 1.03f else 1f
                }
                .shadow(if (highlight) 10.dp else 0.dp, shape, clip = false)
                .clip(shape)
                .background(preset.color)
                .border(
                    width = borderWidth,
                    color = borderColor,
                    shape = shape,
                )
                .onGloballyPositioned { layoutCoordinates ->
                    onBoundsChanged(layoutCoordinates.boundsInWindow())
                }
                .combinedClickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ),
        ) {
            if (highlight) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.White.copy(alpha = 0.06f))
                )
            }
            if (isLightModeSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .size(26.dp)
                        .shadow(4.dp, CircleShape, clip = false)
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = palette.itemTextFocused,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (isDarkModeSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .size(26.dp)
                        .shadow(4.dp, CircleShape, clip = false)
                        .background(palette.itemTextFocused, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = preset.name,
            color = palette.itemText,
            fontSize = 16.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun AppsSettingsScreen(
    apps: List<LauncherApp>,
    inputSources: List<InputSource>,
    hiddenPackages: Set<String>,
    hiddenInputs: Set<String>,
    onToggleAppHidden: (String) -> Unit,
    onToggleInputHidden: (String) -> Unit,
    onDismiss: () -> Unit,
    darkModeEnabled: Boolean,
) {
    BackHandler(onBack = onDismiss)
    val palette = menuPalette(darkModeEnabled)
    val settingsEntries = remember(apps, inputSources) {
        buildList {
            apps.sortedBy { it.label.lowercase() }
                .forEach { add(AppsSettingsEntry.AppEntry(it)) }
            inputSources.sortedBy { it.label.lowercase() }
                .forEach { add(AppsSettingsEntry.InputEntry(it)) }
        }
    }
    var lastFocusedIndex by remember { mutableStateOf(0) }
    val lastFocusedIndexState = rememberUpdatedState(lastFocusedIndex)
    val lifecycleOwner = LocalLifecycleOwner.current
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = 0,
    )
    val coroutineScope = rememberCoroutineScope()
    val focusRequesters = remember(settingsEntries) { List(settingsEntries.size) { FocusRequester() } }
    var scrollJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(settingsEntries) {
        if (settingsEntries.isEmpty()) return@LaunchedEffect
        val targetIndex = lastFocusedIndex.coerceIn(0, settingsEntries.lastIndex)
        scrollJob?.cancel()
        scrollJob = coroutineScope.launch {
            requestVisibleListItemFocus(
                listState = listState,
                index = targetIndex,
                requester = focusRequesters.getOrNull(targetIndex),
            )
        }
    }

    DisposableEffect(lifecycleOwner, settingsEntries) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && settingsEntries.isNotEmpty()) {
                val targetIndex = lastFocusedIndexState.value.coerceIn(0, settingsEntries.lastIndex)
                scrollJob?.cancel()
                scrollJob = coroutineScope.launch {
                    requestVisibleListItemFocus(
                        listState = listState,
                        index = targetIndex,
                        requester = focusRequesters.getOrNull(targetIndex),
                    )
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.background)
    ) {
        Text(
            text = "Apps & Inputs",
            color = palette.title,
            fontSize = 40.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.TopCenter).padding(top = 24.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 96.dp, vertical = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(72.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppsIconTile(palette)
            Box(
                modifier = Modifier
                    .width(540.dp)
                    .height(376.dp)
                    .focusGroup(),
            ) {
                val canScrollUp by remember { derivedStateOf { listState.canScrollBackward } }
                val canScrollDown by remember { derivedStateOf { listState.canScrollForward } }
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                        .drawWithContent {
                            val topAlpha = if (canScrollUp) 0f else 1f
                            val bottomAlpha = if (canScrollDown) 0f else 1f
                            val fadeBrush = Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = topAlpha),
                                0.12f to Color.Black,
                                0.88f to Color.Black,
                                1f to Color.Black.copy(alpha = bottomAlpha),
                            )
                            drawContent()
                            drawRect(brush = fadeBrush, blendMode = BlendMode.DstIn)
                        },
                ) {
                    itemsIndexed(settingsEntries, key = { _, entry -> entry.id }) { index, entry ->
                        val hidden = when (entry) {
                            is AppsSettingsEntry.AppEntry -> hiddenPackages.contains(entry.app.packageName)
                            is AppsSettingsEntry.InputEntry -> hiddenInputs.contains(entry.source.id)
                        }
                        val requester = focusRequesters.getOrNull(index)
                        val upRequester = focusRequesters.getOrNull(index - 1)
                        val downRequester = focusRequesters.getOrNull(index + 1)
                        AppsListItem(
                            label = entry.label,
                            checked = !hidden,
                            onClick = {
                                when (entry) {
                                    is AppsSettingsEntry.AppEntry -> onToggleAppHidden(entry.app.packageName)
                                    is AppsSettingsEntry.InputEntry -> onToggleInputHidden(entry.source.id)
                                }
                                requester?.requestFocus()
                            },
                            onFocused = {
                                val previousFocusedIndex = lastFocusedIndex
                                lastFocusedIndex = index
                                val layoutInfo = listState.layoutInfo
                                val totalItems = layoutInfo.totalItemsCount
                                if (totalItems == 0) return@AppsListItem
                                val safeIndex = index.coerceIn(0, totalItems - 1)
                                val movingUp = safeIndex < previousFocusedIndex
                                val firstVisibleIndex = listState.firstVisibleItemIndex
                                if (movingUp && firstVisibleIndex > 0 && safeIndex <= firstVisibleIndex + 1) {
                                    scrollJob?.cancel()
                                    scrollJob = coroutineScope.launch {
                                        listState.scrollToItem((firstVisibleIndex - 1).coerceAtLeast(0))
                                    }
                                    return@AppsListItem
                                }
                                val viewportStart = layoutInfo.viewportStartOffset
                                val viewportEnd = layoutInfo.viewportEndOffset
                                val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == safeIndex }
                                val isFullyVisible = itemInfo != null &&
                                    itemInfo.offset >= viewportStart &&
                                    itemInfo.offset + itemInfo.size <= viewportEnd
                                if (!isFullyVisible) {
                                    scrollJob?.cancel()
                                    scrollJob = coroutineScope.launch {
                                        listState.scrollToItem(safeIndex)
                                    }
                                }
                            },
                            focusRequester = requester,
                            upRequester = upRequester,
                            downRequester = downRequester,
                            palette = palette,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsIconTile(palette: MenuPalette) {
    Box(
        modifier = Modifier
            .size(300.dp)
            .clip(RoundedCornerShape(60.dp))
            .background(palette.iconTile),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Apps,
            contentDescription = null,
            tint = palette.iconGlyph,
            modifier = Modifier.size(130.dp),
        )
    }
}

@Composable
private fun AppsListItem(
    label: String,
    checked: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    focusRequester: FocusRequester?,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    palette: MenuPalette,
) {
    val shape = RoundedCornerShape(26.dp)
    var focused by remember { mutableStateOf(false) }
    var selectPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val fallbackFocusRequester = remember { FocusRequester() }
    val background = if (focused || selectPressed) palette.itemBackgroundFocused else palette.itemBackground
    val textColor = if (focused || selectPressed) palette.itemTextFocused else palette.itemText

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                scaleX = if (focused) 1.04f else 1f
                scaleY = if (focused) 1.04f else 1f
            }
            .then(if (focused) Modifier.shadow(4.dp, shape, clip = false) else Modifier)
            .clip(shape)
            .background(background)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .focusRequester(focusRequester ?: fallbackFocusRequester)
            .onPreviewKeyEvent { event ->
                val isSelectKey = event.key == Key.DirectionCenter ||
                    event.key == Key.Enter ||
                    event.key == Key.NumPadEnter
                if (!isSelectKey) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        selectPressed = true
                        true
                    }
                    KeyEventType.KeyUp -> {
                        selectPressed = false
                        onClick()
                        focusRequester?.requestFocus()
                        true
                    }
                    else -> Unit
                }
                true
            }
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
                up = upRequester ?: FocusRequester.Cancel
                down = downRequester ?: FocusRequester.Cancel
            }
            .focusable()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    onClick()
                    focusRequester?.requestFocus()
                },
                onLongClick = {},
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = if (checked) "On" else "Off",
            color = textColor.copy(alpha = 0.9f),
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SettingsListItem(
    label: String,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    focusRequester: FocusRequester,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    palette: MenuPalette,
) {
    val shape = RoundedCornerShape(26.dp)
    var focused by remember { mutableStateOf(false) }
    var selectPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val background = if (focused || selectPressed) palette.itemBackgroundFocused else palette.itemBackground
    val textColor = if (focused || selectPressed) palette.itemTextFocused else palette.itemText

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                scaleX = if (focused) 1.04f else 1f
                scaleY = if (focused) 1.04f else 1f
            }
            .then(if (focused) Modifier.shadow(4.dp, shape, clip = false) else Modifier)
            .clip(shape)
            .background(background)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                val isSelectKey = event.key == Key.DirectionCenter ||
                    event.key == Key.Enter ||
                    event.key == Key.NumPadEnter
                if (!isSelectKey) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        selectPressed = true
                        true
                    }
                    KeyEventType.KeyUp -> {
                        selectPressed = false
                        onClick()
                        focusRequester.requestFocus()
                        true
                    }
                    else -> Unit
                }
                false
            }
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
                up = upRequester ?: FocusRequester.Cancel
                down = downRequester ?: FocusRequester.Cancel
            }
            .focusable()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    onClick()
                    focusRequester.requestFocus()
                    selectPressed = false
                },
                onLongClick = {},
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.weight(1f))
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = textColor.copy(alpha = if (focused || selectPressed) 0.9f else 0.6f),
            modifier = Modifier.size(26.dp),
        )
    }
}

@Composable
private fun SettingsSelectableItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onFocused: () -> Unit,
    focusRequester: FocusRequester,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
    palette: MenuPalette,
) {
    val shape = RoundedCornerShape(26.dp)
    var focused by remember { mutableStateOf(false) }
    var selectPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val isHighlighted = focused || selectPressed
    val background = if (isHighlighted) palette.itemBackgroundFocused else palette.itemBackground
    val textColor = if (isHighlighted) palette.itemTextFocused else palette.itemText

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                scaleX = if (focused) 1.04f else 1f
                scaleY = if (focused) 1.04f else 1f
            }
            .then(if (focused) Modifier.shadow(4.dp, shape, clip = false) else Modifier)
            .clip(shape)
            .background(background)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                val isSelectKey = event.key == Key.DirectionCenter ||
                    event.key == Key.Enter ||
                    event.key == Key.NumPadEnter
                if (!isSelectKey) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        selectPressed = true
                        true
                    }
                    KeyEventType.KeyUp -> {
                        selectPressed = false
                        onClick()
                        focusRequester.requestFocus()
                        true
                    }
                    else -> Unit
                }
                false
            }
            .focusProperties {
                left = FocusRequester.Cancel
                right = FocusRequester.Cancel
                up = upRequester ?: FocusRequester.Cancel
                down = downRequester ?: FocusRequester.Cancel
            }
            .focusable()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    onClick()
                    focusRequester.requestFocus()
                    selectPressed = false
                },
                onLongClick = {},
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = textColor.copy(alpha = if (focused) 0.9f else 0.7f),
                modifier = Modifier.size(22.dp),
            )
        } else {
            Spacer(modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun SettingsToggleItem(
    label: String,
    checked: Boolean,
    onToggle: () -> Unit,
    onFocused: () -> Unit,
    focusRequester: FocusRequester,
    upRequester: FocusRequester?,
    downRequester: FocusRequester?,
) {
    val shape = RoundedCornerShape(26.dp)
    var focused by remember { mutableStateOf(false) }
    var selectPressed by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }
    val background = if (focused || selectPressed) Color.White else Color(0xFFCECED1)
    val textColor = if (focused || selectPressed) Color(0xFF141414) else Color(0xFF2B2B2D)
    val toggleBackground = if (checked) Color(0xFF2F2F2F) else Color(0xFFCFCFCF)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .graphicsLayer {
                scaleX = if (focused) 1.04f else 1f
                scaleY = if (focused) 1.04f else 1f
            }
            .then(if (focused) Modifier.shadow(4.dp, shape, clip = false) else Modifier)
            .clip(shape)
            .background(background)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) {
                    onFocused()
                }
            }
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                val isSelectKey = event.key == Key.DirectionCenter ||
                    event.key == Key.Enter ||
                    event.key == Key.NumPadEnter
                if (!isSelectKey) return@onPreviewKeyEvent false
                when (event.type) {
                    KeyEventType.KeyDown -> {
                        selectPressed = true
                        true
                    }
                    KeyEventType.KeyUp -> {
                        selectPressed = false
                        onToggle()
                        focusRequester.requestFocus()
                        true
                    }
                    else -> false
                }
            }
            .focusProperties {
                left = FocusRequester.Cancel
                up = upRequester ?: FocusRequester.Cancel
                down = downRequester ?: FocusRequester.Cancel
            }
            .focusable()
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    onToggle()
                    focusRequester.requestFocus()
                },
                onLongClick = {},
            )
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(width = 44.dp, height = 24.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(toggleBackground),
        ) {
            Box(
                modifier = Modifier
                    .padding(3.dp)
                    .size(18.dp)
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
                    .clip(CircleShape)
                    .background(Color.White),
            )
        }
    }
}

private data class SettingOption(
    val label: String,
    val onClick: () -> Unit,
)
