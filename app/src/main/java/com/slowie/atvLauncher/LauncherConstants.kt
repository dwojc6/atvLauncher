package com.slowie.atvLauncher

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

internal const val PREFS_NAME = "launcher_prefs"
internal const val KEY_DOCK = "dock_packages"
internal const val KEY_HIDDEN = "hidden_packages"
internal const val KEY_HIDDEN_INPUTS = "hidden_inputs"
internal const val KEY_BACKGROUND_URI = "background_uri"
internal const val KEY_BACKGROUND_URI_LIGHT = "background_uri_light"
internal const val KEY_BACKGROUND_URI_DARK = "background_uri_dark"
internal const val KEY_BACKGROUND_PRESET = "background_preset"
internal const val KEY_BACKGROUND_PRESET_LIGHT = "background_preset_light"
internal const val KEY_BACKGROUND_PRESET_DARK = "background_preset_dark"
internal const val KEY_ICON_OVERRIDE_PREFIX = "icon_override_"
internal const val KEY_LABEL_OVERRIDE_PREFIX = "label_override_"
internal const val KEY_GRID_ORDER = "grid_order"
internal const val KEY_FOLDERS = "home_folders_v1"
internal const val KEY_DARK_MODE = "dark_mode_enabled"
internal const val KEY_THEME_MODE = "theme_mode"
internal const val KEY_APP_CACHE = "app_cache_v1"
internal const val KEY_LAST_LAUNCHED_TARGET_ID = "last_launched_target_id"
internal const val KEY_LAST_LAUNCHED_FOLDER_ID = "last_launched_folder_id"
internal const val DOCK_SIZE = 6
internal const val FOLDER_ID_PREFIX = "folder:"
internal const val DEFAULT_FOLDER_NAME = "Folder"

internal val DockCardWidth = 144.dp
internal val DockCardHeight = 81.dp
internal val DockRowHeight = 116.dp
internal val DrawerCardWidth = DockCardWidth
internal val DrawerCardHeight = DockCardHeight
internal const val AppCardFocusScale = 1.18f
internal val AppGridFocusedShadowElevation = 12.dp
internal val TileShadowElevation = 2.dp
// tvOS-style cards use a pronounced, squircle-like corner rather than a
// small Material radius. 18dp matches the proportions of the Apple TV tiles.
internal val TileCornerRadius = 18.dp
internal val DockCornerRadius = 32.dp
internal val PeekCardWidth = 120.dp
internal val PeekCardHeight = 72.dp
internal val DrawerPeekHeight = DrawerCardHeight * 0.1f
internal val GridHorizontalPadding = 48.dp
internal val GridSpacing = 16.dp
internal val AppHorizontalSpacing = 20.dp
internal val GridBottomPadding = 18.dp
internal val DockRowBottomPadding = 6.dp
internal val DockRowTopInsetInApps = 0.dp
internal val DrawerLabelSpacing = 10.dp
internal val DrawerLabelHeight = 16.dp
internal val DockRowWidthFraction = 1f
internal val DockRowVerticalPadding = 18.dp
internal val GlassFill = Color.Transparent
internal val GlassFillFocused = Color.Transparent
internal val GlassTint = Color(0xFFEDEDED.toInt()).copy(alpha = 0.95f)
internal val GlassBorder = Color.White.copy(alpha = 0.18f)
internal val GlassBorderFocused = Color.White.copy(alpha = 0.28f)
