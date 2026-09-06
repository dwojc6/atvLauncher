package com.slowie.atvLauncher

import androidx.compose.ui.graphics.Color

internal const val BACKGROUND_PRESET_CYCLE_ALL = "cycle_all"

internal data class BackgroundPreset(
    val id: String,
    val name: String,
    val color: Color,
)

internal fun defaultBackgroundPresets(): List<BackgroundPreset> {
    return listOf(
        BackgroundPreset(
            id = "sunset_glow",
            name = "Sunset Glow",
            color = Color(0xFF8E3B57),
        ),
        BackgroundPreset(
            id = "ocean_mist",
            name = "Ocean Mist",
            color = Color(0xFF2D5F8F),
        ),
        BackgroundPreset(
            id = "midnight_plum",
            name = "Midnight Plum",
            color = Color(0xFF2A0F1F),
        ),
        BackgroundPreset(
            id = "forest_shade",
            name = "Forest Shade",
            color = Color(0xFF1F3B20),
        ),
        BackgroundPreset(
            id = "desert_peach",
            name = "Desert Peach",
            color = Color(0xFFB56A4D),
        ),
        BackgroundPreset(
            id = "aurora",
            name = "Aurora",
            color = Color(0xFF3A7CA5),
        ),
        BackgroundPreset(
            id = "slate_teal",
            name = "Slate Teal",
            color = Color(0xFF2F4F4F),
        ),
        BackgroundPreset(
            id = "rose_quartz",
            name = "Rose Quartz",
            color = Color(0xFF9E5F7A),
        ),
        BackgroundPreset(
            id = "golden_hour",
            name = "Golden Hour",
            color = Color(0xFFC97A3A),
        ),
        BackgroundPreset(
            id = "deep_space",
            name = "Deep Space",
            color = Color(0xFF14163A),
        ),
        BackgroundPreset(
            id = "ice_blue",
            name = "Ice Blue",
            color = Color(0xFF6FA3D9),
        ),
        BackgroundPreset(
            id = "olive_fog",
            name = "Olive Fog",
            color = Color(0xFF5A6640),
        ),
        BackgroundPreset(
            id = "ember_night",
            name = "Ember Night",
            color = Color(0xFF6B2B1F),
        ),
        BackgroundPreset(
            id = "arctic_dawn",
            name = "Arctic Dawn",
            color = Color(0xFF7AA8B8),
        ),
        BackgroundPreset(
            id = BACKGROUND_PRESET_CYCLE_ALL,
            name = "Cycle All",
            color = Color(0xFF4E5E70),
        ),
    )
}

internal fun cycleableBackgroundPresets(): List<BackgroundPreset> {
    return defaultBackgroundPresets().filterNot { it.id == BACKGROUND_PRESET_CYCLE_ALL }
}

internal fun findBackgroundPreset(id: String?): BackgroundPreset? {
    if (id.isNullOrBlank()) return null
    return defaultBackgroundPresets().firstOrNull { it.id == id }
}
