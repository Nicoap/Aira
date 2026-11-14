package com.wavora.app.expect.ui

import androidx.compose.ui.graphics.Color

/**
 * Цвет пикселя под курсором — пипетка для выбора цвета «с экрана».
 *
 * WHY expect/actual: на десктопе это `java.awt.Robot`, на Android и iOS захват экрана требует
 * отдельного разрешения (MediaProjection / ReplayKit), поэтому там возвращается `null`, и
 * кнопка пипетки просто не показывается.
 */
expect fun sampleScreenPixel(): Color?
