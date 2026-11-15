package com.wavora.app.ui.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset

/*
 * Указатель мыши есть только на десктопе, а эти расширения живут в commonMain и
 * используются экранами, которые компилируются и под Android/iOS. Раньше здесь
 * напрямую вызывался `Modifier.onPointerEvent` — он есть только в desktop-таргете,
 * из-за чего сборка Android падала с `Unresolved reference 'onPointerEvent'`.
 * Поэтому объявляем ожидания здесь, а платформенные реализации — в actual-файлах;
 * на тач-платформах все три — no-op.
 */

/** ПКМ: открывает контекстное меню. На тач-платформах ничего не делает. */
expect fun Modifier.onSecondaryClick(action: () -> Unit): Modifier

/**
 * Колесо мыши: отдаёт величину прокрутки по Y. Используется для изменения громкости
 * в нижней панели плеера (одно деление ≈ 5 %). На тач-платформах — no-op.
 */
expect fun Modifier.onScrollWheel(onScroll: (Float) -> Unit): Modifier

/**
 * Наведение курсора: сообщает позицию указателя и уход курсора с элемента.
 * Нужно для подсказок на тепловой карте статистики. На тач-платформах — no-op.
 */
expect fun Modifier.onHoverMove(
    onMove: (Offset) -> Unit,
    onExit: () -> Unit,
): Modifier
