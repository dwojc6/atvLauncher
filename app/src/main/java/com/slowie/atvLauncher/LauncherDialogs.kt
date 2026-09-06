@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class,
    androidx.tv.material3.ExperimentalTvMaterial3Api::class,
)

package com.slowie.atvLauncher

import android.graphics.Color as AndroidColor
import android.graphics.drawable.ColorDrawable
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.tween
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.tv.material3.Icon
import androidx.tv.material3.Text
import kotlin.math.roundToInt

private enum class AnchoredMenuTone {
    Default,
    Destructive,
}

private enum class AnchoredMenuSide {
    Left,
    Right,
}

private data class AnchoredMenuItem(
    val text: String,
    val onClick: () -> Unit,
    val tone: AnchoredMenuTone = AnchoredMenuTone.Default,
    val showsChevron: Boolean = false,
    val showsAddCircle: Boolean = false,
)

private fun anchoredMenuPanelHeight(itemCount: Int) = (
    28 + // 14.dp top and bottom panel padding
        54 * itemCount +
        8 * (itemCount - 1).coerceAtLeast(0)
    ).dp

@Composable
internal fun AnchoredAppOptionsMenu(
    anchor: HomeMenuAnchor,
    darkModeEnabled: Boolean,
    folders: List<LauncherFolder>,
    showMoveToOption: Boolean,
    showMoveToSubmenu: Boolean,
    onDismiss: () -> Unit,
    onDismissMoveToSubmenu: () -> Unit,
    onMove: () -> Unit,
    onMoveTo: (() -> Unit)?,
    onHide: () -> Unit,
    onManage: () -> Unit,
    onChangeIcon: () -> Unit,
    onResetIcon: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onCreateFolder: () -> Unit,
    onMoveToFolder: (String) -> Unit,
) {
    val mainItems = buildList {
        add(AnchoredMenuItem(text = "Manage app", onClick = onManage))
        add(AnchoredMenuItem(text = "Move app", onClick = onMove))
        if (showMoveToOption && onMoveTo != null) {
            add(
                AnchoredMenuItem(
                    text = "Move to...",
                    onClick = onMoveTo,
                    showsChevron = true,
                )
            )
        }
        if (onRename != null) {
            add(AnchoredMenuItem(text = "Rename app", onClick = onRename))
        }
        if (onResetIcon != null) {
            add(AnchoredMenuItem(text = "Reset to original icon", onClick = onResetIcon))
        }
        add(
            AnchoredMenuItem(
                text = "Hide app",
                onClick = onHide,
                tone = AnchoredMenuTone.Destructive,
            )
        )
        add(AnchoredMenuItem(text = "Change app icon", onClick = onChangeIcon))
    }
    val submenuItems = buildList {
        if (showMoveToOption && onMoveTo != null) {
            add(AnchoredMenuItem(text = "New Folder", onClick = onCreateFolder, showsAddCircle = true))
            folders.forEach { folder ->
                add(AnchoredMenuItem(text = folder.name, onClick = { onMoveToFolder(folder.id) }))
            }
        }
    }

    AnchoredOptionsDialog(
        anchor = anchor,
        darkModeEnabled = darkModeEnabled,
        onDismiss = onDismiss,
        submenuVisible = showMoveToSubmenu,
        onSubmenuDismiss = onDismissMoveToSubmenu,
        submenuAnchorIndex = if (showMoveToOption) 2 else 0,
        mainMenuItems = mainItems,
        submenuItems = submenuItems,
    )
}

@Composable
internal fun AnchoredFolderAppOptionsMenu(
    anchor: HomeMenuAnchor,
    darkModeEnabled: Boolean,
    folders: List<LauncherFolder>,
    showMoveToSubmenu: Boolean,
    onDismiss: () -> Unit,
    onDismissMoveToSubmenu: () -> Unit,
    onMove: () -> Unit,
    onMoveTo: () -> Unit,
    onManage: () -> Unit,
    onMoveToHomeScreen: () -> Unit,
    onCreateFolder: () -> Unit,
    onMoveToFolder: (String) -> Unit,
) {
    val mainItems = listOf(
        AnchoredMenuItem(text = "Move app", onClick = onMove),
        AnchoredMenuItem(
            text = "Move to...",
            onClick = onMoveTo,
            showsChevron = true,
        ),
        AnchoredMenuItem(text = "Manage app", onClick = onManage),
    )
    val submenuItems = buildList {
        add(AnchoredMenuItem(text = "Home Screen", onClick = onMoveToHomeScreen))
        folders.forEach { folder ->
            add(AnchoredMenuItem(text = folder.name, onClick = { onMoveToFolder(folder.id) }))
        }
    }

    AnchoredOptionsDialog(
        anchor = anchor,
        darkModeEnabled = darkModeEnabled,
        onDismiss = onDismiss,
        submenuVisible = showMoveToSubmenu,
        onSubmenuDismiss = onDismissMoveToSubmenu,
        submenuAnchorIndex = 1,
        mainMenuItems = mainItems,
        submenuItems = submenuItems,
    )
}

@Composable
internal fun HomeFolderOptionsMenu(
    anchor: HomeMenuAnchor,
    darkModeEnabled: Boolean,
    onDismiss: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
) {
    AnchoredOptionsDialog(
        anchor = anchor,
        darkModeEnabled = darkModeEnabled,
        onDismiss = onDismiss,
        mainMenuItems = listOf(
            AnchoredMenuItem(text = "Move folder", onClick = onMove),
            AnchoredMenuItem(text = "Rename folder", onClick = onRename),
        ),
    )
}

@Composable
internal fun HomeBackgroundPresetModeMenu(
    anchor: HomeMenuAnchor,
    darkModeEnabled: Boolean,
    onDismiss: () -> Unit,
    onSetLight: () -> Unit,
    onSetDark: () -> Unit,
) {
    AnchoredOptionsDialog(
        anchor = anchor,
        darkModeEnabled = darkModeEnabled,
        onDismiss = onDismiss,
        mainMenuItems = listOf(
            AnchoredMenuItem(text = "Set for Light Mode only", onClick = onSetLight),
            AnchoredMenuItem(text = "Set for Dark Mode only", onClick = onSetDark),
        ),
    )
}

@Composable
internal fun HomeInputOptionsMenu(
    anchor: HomeMenuAnchor,
    darkModeEnabled: Boolean,
    onDismiss: () -> Unit,
    onHide: () -> Unit,
    onChangeIcon: () -> Unit,
) {
    AnchoredOptionsDialog(
        anchor = anchor,
        darkModeEnabled = darkModeEnabled,
        onDismiss = onDismiss,
        mainMenuItems = listOf(
            AnchoredMenuItem(
                text = "Hide input",
                onClick = onHide,
                tone = AnchoredMenuTone.Destructive,
            ),
            AnchoredMenuItem(text = "Change input icon", onClick = onChangeIcon),
        ),
    )
}

@Composable
private fun AnchoredOptionsDialog(
    anchor: HomeMenuAnchor,
    darkModeEnabled: Boolean,
    onDismiss: () -> Unit,
    submenuVisible: Boolean = false,
    onSubmenuDismiss: (() -> Unit)? = null,
    submenuAnchorIndex: Int = 0,
    mainMenuItems: List<AnchoredMenuItem>,
    submenuItems: List<AnchoredMenuItem> = emptyList(),
) {
    BackHandler(enabled = submenuVisible && onSubmenuDismiss != null) {
        onSubmenuDismiss?.invoke()
    }

    var consumeSelectKeyUp by remember { mutableStateOf(true) }
    val mainFocusRequesters = remember(mainMenuItems.size) {
        List(mainMenuItems.size) { FocusRequester() }
    }
    val submenuFocusRequesters = remember(submenuItems.size) {
        List(submenuItems.size) { FocusRequester() }
    }
    var mainMenuSize by remember { mutableStateOf(IntSize.Zero) }
    var submenuSize by remember { mutableStateOf(IntSize.Zero) }
    var submenuWasVisible by remember { mutableStateOf(false) }

    LaunchedEffect(mainMenuItems.size) {
        withFrameNanos { }
        withFrameNanos { }
        mainFocusRequesters.firstOrNull()?.requestFocus()
    }

    LaunchedEffect(submenuVisible, submenuItems.size) {
        withFrameNanos { }
        withFrameNanos { }
        if (submenuVisible) {
            submenuFocusRequesters.firstOrNull()?.requestFocus()
        } else if (submenuWasVisible) {
            mainFocusRequesters.getOrNull(submenuAnchorIndex)?.requestFocus()
        }
        submenuWasVisible = submenuVisible
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val dialogView = LocalView.current
        DisposableEffect(dialogView) {
            val window = (dialogView.parent as? DialogWindowProvider)?.window
            val originalDimAmount = window?.attributes?.dimAmount
            val originalBackground = window?.decorView?.background
            window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            window?.setDimAmount(0f)
            window?.setBackgroundDrawable(ColorDrawable(AndroidColor.TRANSPARENT))
            onDispose {
                if (originalDimAmount != null) {
                    window.setDimAmount(originalDimAmount)
                }
                if (originalBackground != null) {
                    window.setBackgroundDrawable(originalBackground)
                }
            }
        }
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    val isSubmenuDismissKey =
                        event.key == Key.Back ||
                            event.key == Key.Escape ||
                            event.key == Key.Backspace
                    if (submenuVisible && onSubmenuDismiss != null && isSubmenuDismissKey) {
                        if (event.type == KeyEventType.KeyUp) {
                            onSubmenuDismiss()
                        }
                        return@onPreviewKeyEvent true
                    }
                    if (!consumeSelectKeyUp) return@onPreviewKeyEvent false
                    val isSelectKey = event.key == Key.DirectionCenter ||
                        event.key == Key.Enter ||
                        event.key == Key.NumPadEnter
                    if (!isSelectKey) return@onPreviewKeyEvent false
                    when (event.type) {
                        KeyEventType.KeyDown -> true
                        KeyEventType.KeyUp -> {
                            consumeSelectKeyUp = false
                            true
                        }
                        else -> false
                    }
                }
        ) {
            val density = LocalDensity.current
            val panelPaddingPx = with(density) { 28.dp.toPx() }
            val panelGapPx = with(density) { 18.dp.toPx() }
            val panelOverlapPx = with(density) { 54.dp.toPx() }
            val mainPanelWidthPx = with(density) { 348.dp.toPx() }
            val submenuPanelWidthPx = with(density) { 332.dp.toPx() }
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            val viewportHeightPx = with(density) { maxHeight.toPx() }
            val primarySide = if (anchor.boundsInWindow.center.y < viewportHeightPx / 2f) {
                AnchoredMenuSide.Right
            } else {
                AnchoredMenuSide.Left
            }
            val mainMenuX = when (primarySide) {
                AnchoredMenuSide.Right -> anchor.boundsInWindow.right + panelGapPx
                AnchoredMenuSide.Left -> anchor.boundsInWindow.left - panelGapPx - mainPanelWidthPx
            }.coerceIn(
                panelPaddingPx,
                (viewportWidthPx - mainPanelWidthPx - panelPaddingPx).coerceAtLeast(panelPaddingPx),
            )
            val mainMenuY = (
                anchor.boundsInWindow.center.y - (mainMenuSize.height / 2f)
            ).coerceIn(
                panelPaddingPx,
                (viewportHeightPx - mainMenuSize.height - panelPaddingPx).coerceAtLeast(panelPaddingPx),
            )
            val submenuSide = if (primarySide == AnchoredMenuSide.Right) {
                AnchoredMenuSide.Left
            } else {
                AnchoredMenuSide.Right
            }
            val submenuX = when (submenuSide) {
                AnchoredMenuSide.Right -> mainMenuX + mainPanelWidthPx - panelOverlapPx
                AnchoredMenuSide.Left -> mainMenuX - submenuPanelWidthPx + panelOverlapPx
            }.coerceIn(
                panelPaddingPx,
                (viewportWidthPx - submenuPanelWidthPx - panelPaddingPx).coerceAtLeast(panelPaddingPx),
            )
            val submenuAnchorTopPx = with(density) { (22.dp + (submenuAnchorIndex * 62).dp).toPx() }
            val submenuY = (
                mainMenuY + submenuAnchorTopPx
            ).coerceIn(
                panelPaddingPx,
                (viewportHeightPx - submenuSize.height - panelPaddingPx).coerceAtLeast(panelPaddingPx),
            )
            val submenuTransformOrigin = when (submenuSide) {
                AnchoredMenuSide.Right -> TransformOrigin(0f, 0.12f)
                AnchoredMenuSide.Left -> TransformOrigin(1f, 0.12f)
            }
            val dismissSubmenuKey = when (submenuSide) {
                AnchoredMenuSide.Right -> Key.DirectionLeft
                AnchoredMenuSide.Left -> Key.DirectionRight
            }

            Box(modifier = Modifier.fillMaxSize()) {
                AnchoredMenuPanel(
                    modifier = Modifier
                        .width(348.dp)
                        .height(anchoredMenuPanelHeight(mainMenuItems.size))
                        .onGloballyPositioned { mainMenuSize = it.size }
                        .offset {
                            IntOffset(
                                x = mainMenuX.roundToInt(),
                                y = mainMenuY.roundToInt(),
                            )
                        },
                    darkModeEnabled = darkModeEnabled,
                    items = mainMenuItems,
                    focusRequesters = mainFocusRequesters,
                )
                if (submenuItems.isNotEmpty()) {
                    AnimatedVisibility(
                        visible = submenuVisible,
                        enter = fadeIn(animationSpec = tween(durationMillis = 90)) + scaleIn(
                            animationSpec = tween(durationMillis = 110),
                            initialScale = 0.92f,
                            transformOrigin = submenuTransformOrigin,
                        ),
                        exit = fadeOut(animationSpec = tween(durationMillis = 80)) + scaleOut(
                            animationSpec = tween(durationMillis = 90),
                            targetScale = 0.92f,
                            transformOrigin = submenuTransformOrigin,
                        ),
                    ) {
                        AnchoredMenuPanel(
                            modifier = Modifier
                                .width(332.dp)
                                .height(anchoredMenuPanelHeight(submenuItems.size))
                                .onGloballyPositioned { submenuSize = it.size }
                                .onPreviewKeyEvent { event ->
                                    if (
                                        submenuVisible &&
                                        onSubmenuDismiss != null &&
                                        event.key == dismissSubmenuKey
                                    ) {
                                        if (event.type == KeyEventType.KeyDown) {
                                            onSubmenuDismiss()
                                        }
                                        true
                                    } else {
                                        false
                                    }
                                }
                                .offset {
                                    IntOffset(
                                        x = submenuX.roundToInt(),
                                        y = submenuY.roundToInt(),
                                    )
                                }
                                .graphicsLayer {
                                    transformOrigin = submenuTransformOrigin
                                },
                            darkModeEnabled = darkModeEnabled,
                            items = submenuItems,
                            focusRequesters = submenuFocusRequesters,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AnchoredMenuPanel(
    modifier: Modifier,
    darkModeEnabled: Boolean,
    items: List<AnchoredMenuItem>,
    focusRequesters: List<FocusRequester>,
) {
    var contentReady by remember(items.size) { mutableStateOf(false) }
    LaunchedEffect(items.size) {
        withFrameNanos { }
        contentReady = true
    }

    DockStyledSurface(
        modifier = modifier,
        shape = RoundedCornerShape(34.dp),
        darkModeEnabled = darkModeEnabled,
        padding = androidx.compose.foundation.layout.PaddingValues(14.dp),
    ) {
        if (contentReady) {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items.forEachIndexed { index, item ->
                    AnchoredMenuOption(
                        text = item.text,
                        onClick = item.onClick,
                        tone = item.tone,
                        showsChevron = item.showsChevron,
                        showsAddCircle = item.showsAddCircle,
                        darkModeEnabled = darkModeEnabled,
                        focusRequester = focusRequesters.getOrNull(index),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnchoredMenuOption(
    text: String,
    onClick: () -> Unit,
    tone: AnchoredMenuTone,
    showsChevron: Boolean,
    showsAddCircle: Boolean = false,
    darkModeEnabled: Boolean,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val optionFocusRequester = focusRequester ?: remember { FocusRequester() }
    val destructiveFocusBackground = Color(0xFFFF5555)
    val backgroundColor = when {
        focused && tone == AnchoredMenuTone.Destructive -> destructiveFocusBackground
        focused -> Color.White
        else -> Color.Transparent
    }
    val textColor = when {
        focused && tone == AnchoredMenuTone.Destructive -> Color.White
        focused -> Color(0xFF171717)
        tone == AnchoredMenuTone.Destructive -> Color(0xFFFF5555)
        darkModeEnabled -> Color.White
        else -> Color.Black
    }

    Row(
        modifier = Modifier
            .focusRequester(optionFocusRequester)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .defaultMinSize(minHeight = 54.dp)
            .onPreviewKeyEvent { event ->
                val isSelectKey = event.key == Key.DirectionCenter ||
                    event.key == Key.Enter ||
                    event.key == Key.NumPadEnter
                if (!isSelectKey) return@onPreviewKeyEvent false
                if (event.type == KeyEventType.KeyUp) {
                    onClick()
                    true
                } else {
                    event.type == KeyEventType.KeyDown
                }
            }
            .focusable(interactionSource = interactionSource)
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = {},
            )
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (showsAddCircle) {
                Icon(
                    imageVector = Icons.Outlined.AddCircleOutline,
                    contentDescription = null,
                    tint = textColor,
                )
            }
            Text(
                text = text,
                color = textColor,
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        if (showsChevron) {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = textColor,
            )
        }
    }
}

@Composable
internal fun RenameAppDialog(
    appLabel: String,
    initialName: String,
    onNameChange: (String) -> Unit,
    onApply: () -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit,
) {
    SimpleDialog(
        title = "Rename $appLabel",
        onDismiss = onDismiss,
        consumeInitialSelectKeyUp = false,
    ) { firstOptionRequester ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                BasicTextField(
                    value = initialName,
                    onValueChange = onNameChange,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DialogOption(
                text = "Apply",
                onClick = onApply,
                focusRequester = firstOptionRequester,
            )
            DialogOption(text = "Reset to default name", onClick = onReset)
            DialogOption(text = "Cancel", onClick = onDismiss)
        }
    }
}

@Composable
internal fun BackgroundImageDialog(
    onDismiss: () -> Unit,
    onChangeBackground: () -> Unit,
    onChangeBackgroundFromUrl: () -> Unit,
    onResetBackground: () -> Unit,
) {
    SimpleDialog(
        title = "Background Image",
        onDismiss = onDismiss,
        consumeInitialSelectKeyUp = false,
    ) { firstOptionRequester ->
        DialogOption(
            text = "Choose from device",
            onClick = onChangeBackground,
            focusRequester = firstOptionRequester,
        )
        DialogOption(text = "Set from URL", onClick = onChangeBackgroundFromUrl)
        DialogOption(text = "Reset background", onClick = onResetBackground)
        DialogOption(text = "Close", onClick = onDismiss)
    }
}

@Composable
internal fun BackgroundUrlDialog(
    lightUrl: String,
    darkUrl: String,
    bothUrl: String,
    error: String?,
    isLoading: Boolean,
    onLightUrlChange: (String) -> Unit,
    onDarkUrlChange: (String) -> Unit,
    onBothUrlChange: (String) -> Unit,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
) {
    SimpleDialog(
        title = "Background URL",
        onDismiss = onDismiss,
        consumeInitialSelectKeyUp = false,
    ) { firstOptionRequester ->
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            UrlField("Light Mode", lightUrl, onLightUrlChange)
            UrlField("Dark Mode", darkUrl, onDarkUrlChange)
            UrlField("Both Modes", bothUrl, onBothUrlChange)
            if (error != null) {
                Text(
                    text = error,
                    color = Color(0xFFFF8A8A),
                    fontSize = 13.sp,
                )
            }
            DialogOption(
                text = if (isLoading) "Downloading..." else "Apply",
                onClick = onApply,
                focusRequester = firstOptionRequester,
            )
            DialogOption(text = "Cancel", onClick = onDismiss)
        }
    }
}

@Composable
private fun UrlField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = Color.White.copy(alpha = 0.82f), fontSize = 13.sp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Color.White.copy(alpha = 0.12f))
                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(18.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = TextStyle(color = Color.White, fontSize = 14.sp),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            // D-pad navigation should not summon the keyboard.
                            // It is opened explicitly by the OK/Enter key below.
                            keyboardController?.hide()
                        }
                    }
                    .onPreviewKeyEvent { event ->
                        val isSelectKey = event.key == Key.DirectionCenter ||
                            event.key == Key.Enter ||
                            event.key == Key.NumPadEnter
                        if (isSelectKey && event.type == KeyEventType.KeyUp) {
                            keyboardController?.show()
                        }
                        false
                    },
            )
            if (value.isBlank()) {
                Text(
                    text = "https://example.com/image.jpg",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 14.sp,
                )
            }
        }
    }
}

@Composable
internal fun BackgroundPresetModeDialog(
    presetName: String,
    onSetLight: () -> Unit,
    onSetDark: () -> Unit,
    onDismiss: () -> Unit,
) {
    SimpleDialog(
        title = presetName,
        onDismiss = onDismiss,
        consumeInitialSelectKeyUp = true,
    ) { firstOptionRequester ->
        DialogOption(
            text = "Set for Light Mode only",
            onClick = onSetLight,
            focusRequester = firstOptionRequester,
        )
        DialogOption(text = "Set for Dark Mode only", onClick = onSetDark)
        DialogOption(text = "Cancel", onClick = onDismiss)
    }
}

@Composable
internal fun AppOptionsDialog(
    appLabel: String,
    onDismiss: () -> Unit,
    onMove: () -> Unit,
    onMoveTo: (() -> Unit)?,
    onHide: () -> Unit,
    onManage: () -> Unit,
    onChangeIcon: () -> Unit,
    onResetIcon: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
) {
    SimpleDialog(
        title = appLabel,
        onDismiss = onDismiss,
        consumeInitialSelectKeyUp = true,
    ) { firstOptionRequester ->
        DialogOption(text = "Manage app", onClick = onManage, focusRequester = firstOptionRequester)
        DialogOption(text = "Move app", onClick = onMove)
        if (onMoveTo != null) {
            DialogOption(text = "Move to...", onClick = onMoveTo)
        }
        if (onRename != null) {
            DialogOption(text = "Rename app", onClick = onRename)
        }
        if (onResetIcon != null) {
            DialogOption(text = "Reset to original icon", onClick = onResetIcon)
        }
        DialogOption(text = "Hide app", onClick = onHide)
        DialogOption(text = "Change app icon", onClick = onChangeIcon)
        DialogOption(text = "Cancel", onClick = onDismiss)
    }
}

@Composable
internal fun FolderAppOptionsDialog(
    appLabel: String,
    onDismiss: () -> Unit,
    onMove: () -> Unit,
    onMoveTo: () -> Unit,
    onManage: () -> Unit,
) {
    SimpleDialog(
        title = appLabel,
        onDismiss = onDismiss,
        consumeInitialSelectKeyUp = true,
    ) { firstOptionRequester ->
        DialogOption(text = "Move app", onClick = onMove, focusRequester = firstOptionRequester)
        DialogOption(text = "Move to...", onClick = onMoveTo)
        DialogOption(text = "Manage app", onClick = onManage)
        DialogOption(text = "Cancel", onClick = onDismiss)
    }
}

@Composable
internal fun FolderOptionsDialog(
    folderName: String,
    onDismiss: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
) {
    SimpleDialog(
        title = folderName,
        onDismiss = onDismiss,
        consumeInitialSelectKeyUp = true,
    ) { firstOptionRequester ->
        DialogOption(text = "Move folder", onClick = onMove, focusRequester = firstOptionRequester)
        DialogOption(text = "Rename folder", onClick = onRename)
        DialogOption(text = "Cancel", onClick = onDismiss)
    }
}

@Composable
internal fun MoveAppToDialog(
    appLabel: String,
    showHomeScreenOption: Boolean,
    folders: List<LauncherFolder>,
    onDismiss: () -> Unit,
    onMoveToHomeScreen: () -> Unit,
    onCreateFolder: () -> Unit,
    onMoveToFolder: (String) -> Unit,
) {
    SimpleDialog(
        title = "Move $appLabel",
        onDismiss = onDismiss,
        consumeInitialSelectKeyUp = false,
    ) { firstOptionRequester ->
        var optionIndex = 0
        if (showHomeScreenOption) {
            DialogOption(
                text = "Home Screen",
                onClick = onMoveToHomeScreen,
                focusRequester = if (optionIndex++ == 0) firstOptionRequester else null,
            )
        }
        DialogOption(
            text = "New Folder",
            onClick = onCreateFolder,
            showsAddCircle = true,
            focusRequester = if (optionIndex++ == 0) firstOptionRequester else null,
        )
        folders.forEach { folder ->
            DialogOption(
                text = folder.name,
                onClick = { onMoveToFolder(folder.id) },
                focusRequester = if (optionIndex++ == 0) firstOptionRequester else null,
            )
        }
        DialogOption(text = "Cancel", onClick = onDismiss)
    }
}

@Composable
internal fun InputOptionsDialog(
    inputLabel: String,
    onDismiss: () -> Unit,
    onHide: () -> Unit,
    onChangeIcon: () -> Unit,
) {
    SimpleDialog(
        title = inputLabel,
        onDismiss = onDismiss,
        consumeInitialSelectKeyUp = true,
    ) { firstOptionRequester ->
        DialogOption(text = "Hide input", onClick = onHide, focusRequester = firstOptionRequester)
        DialogOption(text = "Change input icon", onClick = onChangeIcon)
        DialogOption(text = "Cancel", onClick = onDismiss)
    }
}

@Composable
internal fun MoveDockDialog(
    appLabel: String,
    dockSlots: List<String?>,
    onDismiss: () -> Unit,
    onSlotSelected: (Int) -> Unit,
) {
    SimpleDialog(
        title = "Move $appLabel",
        onDismiss = onDismiss,
        consumeInitialSelectKeyUp = false,
    ) { firstOptionRequester ->
        dockSlots.forEachIndexed { index, packageName ->
            val slotLabel = if (packageName == null) {
                "Slot ${index + 1} (empty)"
            } else {
                "Slot ${index + 1}"
            }
            DialogOption(
                text = slotLabel,
                onClick = { onSlotSelected(index) },
                focusRequester = if (index == 0) firstOptionRequester else null,
            )
        }
        DialogOption(text = "Cancel", onClick = onDismiss)
    }
}

@Composable
internal fun SimpleDialog(
    title: String,
    onDismiss: () -> Unit,
    consumeInitialSelectKeyUp: Boolean,
    content: @Composable ColumnScope.(FocusRequester) -> Unit,
) {
    var consumeSelectKeyUp by remember { mutableStateOf(consumeInitialSelectKeyUp) }
    val firstOptionRequester = remember { FocusRequester() }
    val view = LocalView.current

    DisposableEffect(view) {
        val window = (view.parent as? DialogWindowProvider)?.window
        val originalDimAmount = window?.attributes?.dimAmount
        window?.setDimAmount(0f)
        onDispose {
            if (originalDimAmount != null) {
                window.setDimAmount(originalDimAmount)
            }
        }
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        LaunchedEffect(firstOptionRequester) {
            withFrameNanos { }
            firstOptionRequester.requestFocus()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f)),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .width(420.dp)
                    .wrapContentHeight()
                    .shadow(12.dp, RoundedCornerShape(26.dp), clip = false)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Color.Black)
                    .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(26.dp))
                    .padding(24.dp)
            ) {
                Column(
                    modifier = Modifier
                        .onPreviewKeyEvent { event ->
                            if (!consumeSelectKeyUp) return@onPreviewKeyEvent false
                            val isSelectKey = event.key == Key.DirectionCenter ||
                                event.key == Key.Enter ||
                                event.key == Key.NumPadEnter
                            if (!isSelectKey) return@onPreviewKeyEvent false
                            when (event.type) {
                                KeyEventType.KeyDown -> true
                                KeyEventType.KeyUp -> {
                                    consumeSelectKeyUp = false
                                    true
                                }
                                else -> false
                            }
                        },
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    content(firstOptionRequester)
                }
            }
        }
    }
}

@Composable
internal fun DialogOption(
    text: String,
    onClick: () -> Unit,
    showsAddCircle: Boolean = false,
    focusRequester: FocusRequester? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()
    val optionFocusRequester = focusRequester ?: remember { FocusRequester() }

    val baseModifier = Modifier
        .fillMaxWidth()
        .clip(RoundedCornerShape(18.dp))
        .background(Color.White.copy(alpha = if (focused) 0.2f else 0.12f))
        .border(
            1.dp,
            Color.White.copy(alpha = if (focused) 0.32f else 0.22f),
            RoundedCornerShape(18.dp)
        )
        .onPreviewKeyEvent { event ->
            val isSelectKey = event.key == Key.DirectionCenter ||
                event.key == Key.Enter ||
                event.key == Key.NumPadEnter
            if (!isSelectKey) return@onPreviewKeyEvent false
            if (event.type == KeyEventType.KeyUp) {
                onClick()
                optionFocusRequester.requestFocus()
                true
            } else {
                event.type == KeyEventType.KeyDown
            }
        }
        .focusable(interactionSource = interactionSource)
        .combinedClickable(
            interactionSource = interactionSource,
            indication = null,
            onClick = {
                onClick()
                optionFocusRequester.requestFocus()
            },
            onLongClick = {},
        )
        .padding(vertical = 12.dp, horizontal = 16.dp)

    Row(
        modifier = Modifier
            .focusRequester(optionFocusRequester)
            .then(baseModifier),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (showsAddCircle) {
            Icon(
                imageVector = Icons.Outlined.AddCircleOutline,
                contentDescription = null,
                tint = Color.White,
            )
        }
        Text(
            text = text,
            color = Color.White,
            fontSize = 14.sp,
        )
    }
}
