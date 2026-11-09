package com.wavora.app

import com.sun.jna.CallbackReference
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.platform.win32.WinUser
import com.wavora.logger.Logger
import java.awt.Window

private const val TAG = "WindowsWindowFrame"

private const val WM_GETMINMAXINFO = 0x0024
private const val WM_NCCALCSIZE = 0x0083
private const val WM_NCHITTEST = 0x0084
private const val WS_MAXIMIZE = 0x01000000

private const val HTCLIENT = 1
private const val HTCAPTION = 2
private const val HTLEFT = 10
private const val HTRIGHT = 11
private const val HTTOP = 12
private const val HTTOPLEFT = 13
private const val HTTOPRIGHT = 14
private const val HTBOTTOM = 15
private const val HTBOTTOMLEFT = 16
private const val HTBOTTOMRIGHT = 17

/**
 * Gives the undecorated (frameless, transparent) main window the behaviour of a normal Windows window:
 * Win+arrows / Aero Snap, drag-to-edge snapping, resizing from the edges, double-click on the title bar,
 * and a maximized state that respects the taskbar.
 *
 * How: the window gets WS_THICKFRAME / WS_MAXIMIZEBOX / WS_MINIMIZEBOX / WS_SYSMENU, and its window procedure is
 * subclassed: WM_NCCALCSIZE removes the non-client area (no native frame is drawn), WM_NCHITTEST reports the
 * Compose title bar as HTCAPTION (except the window buttons) and the edges as resize borders,
 * WM_GETMINMAXINFO keeps the maximized window inside the work area.
 *
 * Safety: Windows only, never throws (any failure leaves the plain window as it was), disabled by
 * the environment variable WAVORA_NATIVE_FRAME=0. The JNA callback is held strongly
 * (a garbage-collected JNA callback crashes the process with "Invalid memory access").
 */
internal class WindowsWindowFrame private constructor(
    private val window: Window,
    private val hwnd: WinDef.HWND,
    private val captionHeightDp: Float,
    private val controlsWidthDp: Float,
) : WinUser.WindowProc {
    private val user32 = User32.INSTANCE
    private var previousProc: Pointer? = null
    private var procPointer: Pointer? = null

    @Volatile
    private var attached = false

    private fun scale(): Double = window.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0

    override fun callback(
        hWnd: WinDef.HWND,
        uMsg: Int,
        wParam: WinDef.WPARAM,
        lParam: WinDef.LPARAM,
    ): WinDef.LRESULT {
        if (attached) {
            try {
                when (uMsg) {
                    // Client area = whole window: no native title bar / frame is drawn.
                    WM_NCCALCSIZE -> if (wParam.toLong() != 0L) return WinDef.LRESULT(0)

                    WM_NCHITTEST -> hitTest(lParam)?.let { return WinDef.LRESULT(it.toLong()) }

                    WM_GETMINMAXINFO -> if (fillMinMaxInfo(lParam)) return WinDef.LRESULT(0)
                }
            } catch (t: Throwable) {
                Logger.w(TAG, "window proc failed for msg=$uMsg: ${t.message}")
            }
        }
        return user32.CallWindowProc(previousProc, hWnd, uMsg, wParam, lParam)
    }

    private fun hitTest(lParam: WinDef.LPARAM): Int? {
        val rect = WinDef.RECT()
        if (!user32.GetWindowRect(hwnd, rect)) return null
        val packed = lParam.toLong()
        val x = (packed and 0xFFFF).toShort().toInt()
        val y = ((packed shr 16) and 0xFFFF).toShort().toInt()
        val width = rect.right - rect.left
        val height = rect.bottom - rect.top
        val rx = x - rect.left
        val ry = y - rect.top
        if (rx < 0 || ry < 0 || rx >= width || ry >= height) return null

        val scale = scale()
        val maximized = (user32.GetWindowLong(hwnd, WinUser.GWL_STYLE) and WS_MAXIMIZE) != 0
        if (!maximized) {
            val border = (5 * scale).toInt().coerceAtLeast(4)
            val left = rx < border
            val right = rx >= width - border
            val top = ry < border
            val bottom = ry >= height - border
            when {
                top && left -> return HTTOPLEFT
                top && right -> return HTTOPRIGHT
                bottom && left -> return HTBOTTOMLEFT
                bottom && right -> return HTBOTTOMRIGHT
                left -> return HTLEFT
                right -> return HTRIGHT
                top -> return HTTOP
                bottom -> return HTBOTTOM
            }
        }
        val captionHeight = (captionHeightDp * scale).toInt()
        val controlsWidth = (controlsWidthDp * scale).toInt()
        if (ry < captionHeight && rx < width - controlsWidth) return HTCAPTION
        return HTCLIENT
    }

    private fun fillMinMaxInfo(lParam: WinDef.LPARAM): Boolean {
        val monitor = user32.MonitorFromWindow(hwnd, WinUser.MONITOR_DEFAULTTONEAREST) ?: return false
        val info = WinUser.MONITORINFO()
        info.cbSize = info.size()
        if (!user32.GetMonitorInfo(monitor, info).booleanValue()) return false
        val work = info.rcWork
        val full = info.rcMonitor
        val scale = scale()
        val p = Pointer(lParam.toLong())
        // MINMAXINFO: ptReserved(0) ptMaxSize(8) ptMaxPosition(16) ptMinTrackSize(24) ptMaxTrackSize(32)
        p.setInt(8, work.right - work.left)
        p.setInt(12, work.bottom - work.top)
        p.setInt(16, work.left - full.left)
        p.setInt(20, work.top - full.top)
        p.setInt(24, (MIN_WIDTH_DP * scale).toInt())
        p.setInt(28, (MIN_HEIGHT_DP * scale).toInt())
        return true
    }

    private fun attach() {
        val style = user32.GetWindowLong(hwnd, WinUser.GWL_STYLE)
        user32.SetWindowLong(
            hwnd,
            WinUser.GWL_STYLE,
            style or WinUser.WS_THICKFRAME or WinUser.WS_MINIMIZEBOX or WinUser.WS_MAXIMIZEBOX or WinUser.WS_SYSMENU,
        )
        procPointer = CallbackReference.getFunctionPointer(this)
        previousProc = user32.SetWindowLongPtr(hwnd, WinUser.GWL_WNDPROC, procPointer)
        attached = true
        user32.SetWindowPos(
            hwnd,
            null,
            0,
            0,
            0,
            0,
            WinUser.SWP_FRAMECHANGED or WinUser.SWP_NOMOVE or WinUser.SWP_NOSIZE or
                WinUser.SWP_NOZORDER or WinUser.SWP_NOACTIVATE,
        )
        Logger.i(TAG, "native frame attached (snap / resize / caption)")
    }

    /** Restores the original window procedure. Safe to call more than once. */
    fun detach() {
        if (!attached) return
        attached = false
        try {
            user32.SetWindowLongPtr(hwnd, WinUser.GWL_WNDPROC, previousProc)
        } catch (t: Throwable) {
            Logger.w(TAG, "detach failed: ${t.message}")
        }
    }

    companion object {
        private const val MIN_WIDTH_DP = 760
        private const val MIN_HEIGHT_DP = 480

        // Strong reference for the lifetime of the process (see class comment).
        @Volatile
        private var active: WindowsWindowFrame? = null

        fun install(
            window: Window,
            captionHeightDp: Float,
            controlsWidthDp: Float,
        ): WindowsWindowFrame? {
            if (!System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)) return null
            if (System.getenv("WAVORA_NATIVE_FRAME") == "0") return null
            return try {
                val pointer = Native.getWindowPointer(window) ?: return null
                val frame = WindowsWindowFrame(window, WinDef.HWND(pointer), captionHeightDp, controlsWidthDp)
                frame.attach()
                active?.detach()
                active = frame
                frame
            } catch (t: Throwable) {
                Logger.w(TAG, "native frame not installed: ${t.message}")
                null
            }
        }
    }
}