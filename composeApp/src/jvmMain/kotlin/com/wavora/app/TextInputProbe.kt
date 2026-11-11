package com.wavora.app

import java.awt.Component
import java.awt.Container
import java.awt.Window

/**
 * Tells whether a Compose text field currently has an active text-input session.
 *
 * Compose Desktop announces that through the standard Swing hook: while a text field is being
 * edited, the window's root component returns a non-null InputMethodRequests (enableInput /
 * disableInput in ComposeSceneMediator). The window-level key handler ALSO sees letters, Space and
 * arrows typed into a text field (the field only consumes the separate "typed character" event),
 * so global shortcuts use this to stay out of the way while the user is typing.
 */
internal object TextInputProbe {
    /** The main Compose window; set from DesktopApp. */
    @Volatile
    var window: Window? = null

    fun isTextInputActive(): Boolean {
        val root = window ?: return false
        return hasInputMethodRequests(root)
    }

    private fun hasInputMethodRequests(component: Component): Boolean {
        if (component.inputMethodRequests != null) return true
        if (component is Container) {
            for (child in component.components) {
                if (hasInputMethodRequests(child)) return true
            }
        }
        return false
    }
}