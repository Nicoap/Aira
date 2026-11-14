package com.wavora.app.expect.ui

import androidx.compose.ui.graphics.Color

/**
 * На iOS для чтения экрана нужен ReplayKit, поэтому пипетки нет:
 * сразу сообщаем, что цвет не выбран (кнопка пипетки в UI не показывается).
 */
actual fun startScreenColorPicker(onPicked: (Color?) -> Unit) {
    onPicked(null)
}
