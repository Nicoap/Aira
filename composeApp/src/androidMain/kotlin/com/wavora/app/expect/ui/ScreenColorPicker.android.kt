package com.wavora.app.expect.ui

import androidx.compose.ui.graphics.Color

/**
 * На Android захват экрана требует разрешения MediaProjection, поэтому пипетки нет:
 * сразу сообщаем, что цвет не выбран (кнопка пипетки в UI не показывается).
 */
actual fun startScreenColorPicker(onPicked: (Color?) -> Unit) {
    onPicked(null)
}
