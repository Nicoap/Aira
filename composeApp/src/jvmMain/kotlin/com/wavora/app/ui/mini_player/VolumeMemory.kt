package com.wavora.app.ui.mini_player

/**
 * Уровень громкости до мьюта.
 *
 * WHY: мини-плеер (компактный, средний, квадратный и раскрытый виды) и остров — это
 * разные composable'ы, у каждого был свой `if (volume > 0) 0 else 1f`, поэтому размьют
 * всегда возвращал 100 %, а не то значение, которое было до мьюта. Один общий объект
 * на всё приложение даёт одинаковое поведение везде.
 */
internal object VolumeMemory {
    private const val NO_LEVEL_RESTORED = -1f

    private var lastAudible: Float = NO_LEVEL_RESTORED

    /**
     * Возвращает громкость после нажатия на кнопку звука: если сейчас звук есть —
     * запоминаем его и приглушаем, если звука нет — отдаём запомненное значение.
     */
    fun toggle(current: Float): Float =
        if (current > 0f) {
            lastAudible = current
            0f
        } else {
            if (lastAudible > 0f) lastAudible else 0.5f
        }
}
