package com.wavora.app.expect.ui

import androidx.compose.ui.graphics.Color
import java.awt.Cursor
import java.awt.EventQueue
import java.awt.Graphics
import java.awt.GraphicsEnvironment
import java.awt.Rectangle
import java.awt.Robot
import java.awt.event.KeyAdapter
import java.awt.event.KeyEvent
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JWindow

/**
 * Пипетка: снимаем экран один раз, показываем этот же снимок полноэкранным оверлеем и берём
 * пиксель из снимка по клику.
 *
 * Почему не «клик по кнопке + Robot.getPixelColor»: кнопка находится в самом диалоге, и Robot
 * возвращал цвет диалога, а не того места, куда целился пользователь. Почему не прозрачный
 * оверлей: прозрачность окна зависит от драйвера и на части систем окно не получает клики.
 * Снимок 1:1 решает оба случая: что видит пользователь — то и попадает в цвет.
 */
actual fun startScreenColorPicker(onPicked: (Color?) -> Unit) {
    EventQueue.invokeLater {
        val area = screenArea()
        if (area == null) {
            onPicked(null)
            return@invokeLater
        }
        val shot =
            runCatching { Robot().createScreenCapture(area) }.getOrNull()
                ?: run {
                    onPicked(null)
                    return@invokeLater
                }

        lateinit var overlay: JWindow
        fun close() {
            overlay.isVisible = false
            overlay.dispose()
        }

        fun pick(x: Int, y: Int) {
            val px = x.coerceIn(0, shot.width - 1)
            val py = y.coerceIn(0, shot.height - 1)
            val argb = shot.getRGB(px, py)
            close()
            onPicked(Color((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF))
        }

        val panel =
            object : JPanel() {
                override fun paintComponent(g: Graphics) {
                    g.drawImage(shot, 0, 0, null)
                }
            }.apply {
                isOpaque = true
                addMouseListener(
                    object : MouseAdapter() {
                        override fun mousePressed(e: MouseEvent) {
                            pick(e.x, e.y)
                        }
                    },
                )
            }

        overlay =
            JWindow().apply {
                isAlwaysOnTop = true
                cursor = Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR)
                setBounds(area)
                contentPane = panel
                addKeyListener(
                    object : KeyAdapter() {
                        override fun keyPressed(e: KeyEvent) {
                            if (e.keyCode == KeyEvent.VK_ESCAPE) {
                                close()
                                onPicked(null)
                            }
                        }
                    },
                )
            }
        overlay.isVisible = true
        overlay.requestFocus()
    }
}

/** Общая область всех мониторов; `null`, если экранов нет (не должно случаться). */
private fun screenArea(): Rectangle? =
    GraphicsEnvironment
        .getLocalGraphicsEnvironment()
        .screenDevices
        .map { it.defaultConfiguration.bounds }
        .reduceOrNull { acc, bounds -> acc.union(bounds) }
