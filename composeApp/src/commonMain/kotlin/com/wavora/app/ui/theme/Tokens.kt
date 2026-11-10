package com.wavora.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * Wavora design tokens: one place for the neutral colour ramp, the spacing scale and the corner
 * radii. New UI should use these instead of ad-hoc Color(0x...) / N.dp values.
 */

/**
 * Flat, neutral dark ramp (no colour tint): one tone step per surface level. The accent colour
 * (MaterialTheme.colorScheme.primary) is reserved for state: progress, the current line, the
 * current track, active toggles.
 */
object WavoraTone {
    val ground = Color(0xFF0B0B0B)
    val surface1 = Color(0xFF131313)
    val surface2 = Color(0xFF1B1B1B)
    val hairline = Color(0xFF262626)
    val ink = Color(0xFFF2F2F2)
    val muted = Color(0xFF8C8C8C)
}

/** 4-pt spacing scale for padding, gaps and margins. Icon and control sizes are not spacing. */
object WavoraSpace {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 40.dp
}

/** Corner radii by role. */
object WavoraRadius {
    /** Small row thumbnails and inner chips. */
    val thumb = 4.dp

    /** Cover art, video frames, cards. */
    val art = 8.dp

    /** Panels, sheets, dialogs. */
    val panel = 12.dp
}