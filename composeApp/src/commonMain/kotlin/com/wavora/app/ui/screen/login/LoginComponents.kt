package com.wavora.app.ui.screen.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.wavora.app.expect.ui.WebViewState
import com.wavora.app.ui.theme.LocalAppTypography

/**
 * What the user sees above a sign-in page: one sentence on what to do and what happens next, and a loading
 * bar, so a blank web view while the page loads does not look like a hang. The strip keeps its height when
 * the bar disappears, so the page below does not jump.
 */
@Composable
internal fun LoginHint(
    text: String,
    state: State<WebViewState>,
) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = text,
            style = LocalAppTypography.current.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        )
        val current = state.value
        if (current is WebViewState.Loading && current.progress in 0..99) {
            LinearProgressIndicator(
                progress = { current.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Spacer(Modifier.height(4.dp))
        }
    }
}
