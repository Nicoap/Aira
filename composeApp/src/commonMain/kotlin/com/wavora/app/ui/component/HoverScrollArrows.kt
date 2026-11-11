package com.wavora.app.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Desktop affordance for a horizontal carousel: while the mouse is over it, round previous/next
 * buttons appear (only in a direction that can still scroll) and move by ~80% of the viewport.
 * Touch devices never hover, so nothing changes there.
 */
@Composable
fun HoverScrollArrows(
    state: LazyListState,
    modifier: Modifier = Modifier,
    // Vertical offset of the arrow buttons, aimed at the middle of a 160dp thumbnail.
    arrowTopPadding: Dp = 70.dp,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scope = rememberCoroutineScope()
    Box(modifier.hoverable(interaction)) {
        content()
        AnimatedVisibility(
            visible = hovered && state.canScrollBackward,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = arrowTopPadding),
        ) {
            ScrollArrow(Icons.AutoMirrored.Rounded.KeyboardArrowLeft) {
                scope.launch { state.animateScrollBy(-(state.layoutInfo.viewportSize.width * 0.8f)) }
            }
        }
        AnimatedVisibility(
            visible = hovered && state.canScrollForward,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopEnd).padding(end = 12.dp, top = arrowTopPadding),
        ) {
            ScrollArrow(Icons.AutoMirrored.Rounded.KeyboardArrowRight) {
                scope.launch { state.animateScrollBy(state.layoutInfo.viewportSize.width * 0.8f) }
            }
        }
    }
}

@Composable
private fun ScrollArrow(
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.White)
    }
}