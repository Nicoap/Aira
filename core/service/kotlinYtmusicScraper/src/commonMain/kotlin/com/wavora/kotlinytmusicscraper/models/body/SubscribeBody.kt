package com.wavora.scraper.models.body

import com.wavora.scraper.models.Context
import kotlinx.serialization.Serializable

@Serializable
data class SubscribeBody(
    val channelIds: List<String>,
    val context: Context,
)
