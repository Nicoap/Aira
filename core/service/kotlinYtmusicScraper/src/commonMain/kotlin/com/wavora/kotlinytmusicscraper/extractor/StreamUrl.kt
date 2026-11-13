package com.wavora.scraper.extractor

/**
 * Переписывает прямой адрес потока (googlevideo) в адрес, который сможет открыть плеер.
 *
 * На Windows сервер отдаёт 403 на открытый `Range: bytes=0-`, который шлёт libVLC, но нормально
 * отдаёт куски ограниченной длины. Поэтому JVM-реализация подставляет локальный потоковый прокси,
 * который превращает запрос плеера в цепочку ограниченных запросов. На остальных платформах
 * (Android/iOS) ссылка отдаётся как есть.
 */
expect fun rewriteStreamUrl(url: String): String

/**
 * Returns the original (upstream) stream address for a link that [rewriteStreamUrl] may have produced.
 * Links of an earlier run point at a dead local port, so health checks must look at the upstream address.
 * Anything that is not a local proxy link is returned unchanged.
 */
expect fun upstreamStreamUrl(url: String): String
