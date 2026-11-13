package com.wavora.scraper.extractor

/** На iOS прямой адрес потока открывается плеером, переписывать нечего. */
actual fun rewriteStreamUrl(url: String): String = url

actual fun upstreamStreamUrl(url: String): String = url
