package com.wavora.app.ui.component

import androidx.compose.foundation.MarqueeAnimationMode
import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * [basicMarquee] that runs only while the pointer is over the element.
 *
 * Any live marquee is an endless animation: Compose then redraws the WHOLE window on every vsync, even when the
 * text is far off screen or the value is unused. With dozens of long titles on a screen that kept the renderer
 * busy all the time. At rest the text is simply clipped; hovering scrolls it so it can be read in full.
 * The parameters mirror [basicMarquee].
 */
fun Modifier.hoverMarquee(
    iterations: Int = 3,
    animationMode: MarqueeAnimationMode = MarqueeAnimationMode.Immediately,
    repeatDelayMillis: Int = 1200,
    initialDelayMillis: Int = repeatDelayMillis,
    spacing: MarqueeSpacing = MarqueeSpacing.fractionOfContainer(1f / 3f),
    velocity: Dp = 30.dp,
): Modifier =
    composed {
        val hoverSource = remember { MutableInteractionSource() }
        val hovered by hoverSource.collectIsHoveredAsState()
        this.hoverable(hoverSource).then(
            if (hovered) {
                Modifier.basicMarquee(
                    iterations = iterations,
                    animationMode = animationMode,
                    repeatDelayMillis = repeatDelayMillis,
                    initialDelayMillis = initialDelayMillis,
                    spacing = spacing,
                    velocity = velocity,
                )
            } else {
                Modifier
            },
        )
    }
