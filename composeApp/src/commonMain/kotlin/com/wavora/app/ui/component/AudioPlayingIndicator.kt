package com.wavora.app.ui.component

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.unit.Dp
import io.github.alexzhirkevich.compottie.Compottie
import io.github.alexzhirkevich.compottie.LottieCompositionSpec
import io.github.alexzhirkevich.compottie.rememberLottieComposition
import io.github.alexzhirkevich.compottie.rememberLottiePainter
import wavora.composeapp.generated.resources.Res

/**
 * Animated equalizer shown for the track that is currently playing.
 */
@Composable
fun AudioPlayingIndicator(
    size: Dp,
    modifier: Modifier = Modifier,
    playing: Boolean = true,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    val composition by rememberLottieComposition {
        LottieCompositionSpec.JsonString(
            Res.readBytes("files/audio_playing_animation.json").decodeToString(),
        )
    }
    Crossfade(playing) { isPlaying ->
        if (isPlaying) {
            Image(
                painter =
                    rememberLottiePainter(
                        composition = composition,
                        iterations = Compottie.IterateForever,
                    ),
                contentDescription = null,
                colorFilter = ColorFilter.tint(tint),
                modifier = modifier.size(size),
            )
        }
    }
}
