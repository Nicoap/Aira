package com.wavora.app.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.wavora.app.extension.NonLazyGrid
import com.wavora.app.ui.navigation.destination.library.LibraryDynamicPlaylistDestination
import com.wavora.app.ui.screen.library.LibraryDynamicPlaylistType
import com.wavora.app.ui.theme.typo
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import wavora.composeapp.generated.resources.Res
import wavora.composeapp.generated.resources.downloaded
import wavora.composeapp.generated.resources.favorite
import wavora.composeapp.generated.resources.followed
import wavora.composeapp.generated.resources.most_played
import com.wavora.app.ui.theme.LocalAppTypography

@Composable
fun LibraryTilingBox(navController: NavController) {
    val listItem =
        listOf(
            LibraryTilingState.Favorite,
            LibraryTilingState.Followed,
            LibraryTilingState.MostPlayed,
            LibraryTilingState.Downloaded,
        )
    NonLazyGrid(
        columns = 2,
        itemCount = 4,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp, end = 10.dp),
    ) { number ->
        Box(
            Modifier.padding(start = 10.dp, top = 10.dp),
        ) {
            LibraryTilingItem(
                listItem[number],
                onClick = {
                    when (listItem[number]) {
                        LibraryTilingState.Favorite -> {
                            navController.navigate(
                                LibraryDynamicPlaylistDestination(
                                    type = LibraryDynamicPlaylistType.Favorite.toStringParams(),
                                ),
                            )
                        }

                        LibraryTilingState.Followed -> {
                            navController.navigate(
                                LibraryDynamicPlaylistDestination(
                                    type = LibraryDynamicPlaylistType.Followed.toStringParams(),
                                ),
                            )
                        }

                        LibraryTilingState.MostPlayed -> {
                            navController.navigate(
                                LibraryDynamicPlaylistDestination(
                                    type = LibraryDynamicPlaylistType.MostPlayed.toStringParams(),
                                ),
                            )
                        }

                        LibraryTilingState.Downloaded -> {
                            navController.navigate(
                                LibraryDynamicPlaylistDestination(
                                    type = LibraryDynamicPlaylistType.Downloaded.toStringParams(),
                                ),
                            )
                        }
                    }
                },
            )
        }
    }
}

@Composable
fun LibraryTilingItem(
    state: LibraryTilingState,
    onClick: () -> Unit = {},
) {
    val title = stringResource(state.title)
    ElevatedCard(
        modifier =
            Modifier.fillMaxWidth().clickable {
                onClick.invoke()
            },
        shape = RoundedCornerShape(8.dp),
        elevation = CardDefaults.elevatedCardElevation(),
        colors =
            CardDefaults.elevatedCardColors().copy(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                state.icon,
                contentDescription = title,
                modifier =
                    Modifier
                        .size(50.dp)
                        .padding(10.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(
                title,
                style = LocalAppTypography.current.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

data class LibraryTilingState(
    val title: StringResource,
    val icon: ImageVector,
) {
    companion object {
        val Favorite =
            LibraryTilingState(
                title = Res.string.favorite,
                icon = Icons.Default.Favorite,
            )
        val Followed =
            LibraryTilingState(
                title = Res.string.followed,
                icon = Icons.Default.Insights,
            )
        val MostPlayed =
            LibraryTilingState(
                title = Res.string.most_played,
                icon = Icons.AutoMirrored.Filled.TrendingUp,
            )
        val Downloaded =
            LibraryTilingState(
                title = Res.string.downloaded,
                icon = Icons.Default.Downloading,
            )
    }
}