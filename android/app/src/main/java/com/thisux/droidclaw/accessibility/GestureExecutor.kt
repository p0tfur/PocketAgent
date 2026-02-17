package com.thisux.droidclaw.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Intent
import android.graphics.Path
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityNodeInfo
import com.thisux.droidclaw.model.ServerMessage
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

data class ActionResult(val success: Boolean, val error: String? = null, val data: String? = null)

class GestureExecutor(private val service: DroidClawAccessibilityService) {

    companion object {
        private const val TAG = "GestureExecutor"
    }

    suspend fun execute(msg: ServerMessage): ActionResult {
        return try {
            when (msg.type) {
                "tap" -> executeTap(msg.x ?: 0, msg.y ?: 0)
                "type" -> executeType(msg.text ?: "")
                "enter" -> executeEnter()
                "back" -> executeGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
                "home" -> executeGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
                "notifications" -> executeGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
                "longpress" -> executeLongPress(msg.x ?: 0, msg.y ?: 0)
                "swipe" -> executeSwipe(
                    msg.x1 ?: 0, msg.y1 ?: 0,
                    msg.x2 ?: 0, msg.y2 ?: 0,
                    msg.duration ?: 300
                )
                "launch" -> executeLaunch(msg.packageName ?: "")
                "clear" -> executeClear()
                "clipboard_set" -> executeClipboardSet(msg.text ?: "")
                "clipboard_get" -> executeClipboardGet()
                "paste" -> executePaste()
                "open_url" -> executeOpenUrl(msg.url ?: "")
                "switch_app" -> executeLaunch(msg.packageName ?: "")
                "keyevent" -> executeKeyEvent(msg.code ?: 0)
                "open_settings" -> executeOpenSettings()
                "wait" -> executeWait(msg.duration ?: 1000)
                else -> ActionResult(false, "Unknown action: ${msg.type}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Action ${msg.type} failed", e)
            ActionResult(false, e.message)
        }
    }

    private suspend fun executeTap(x: Int, y: Int): ActionResult {
        val node = service.findNodeAt(x, y)
        if (node != null) {
            try {
                if (node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                    return ActionResult(true)
                }
            } finally {
                node.recycle()
            }
        }
        return dispatchTapGesture(x, y)
    }

    private suspend fun executeType(text: String): ActionResult {
        val focused = findFocusedNode()
        if (focused != null) {
            try {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
                }
                if (focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    return ActionResult(true)
                }
            } finally {
                focused.recycle()
            }
        }
        return ActionResult(false, "No focused editable node found")
    }

    private fun executeEnter(): ActionResult {
        val focused = findFocusedNode()
        if (focused != null) {
            try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    val action = AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER
                    if (focused.performAction(action.id)) {
                        return ActionResult(true)
                    }
                }
            } finally {
                focused.recycle()
            }
        }
        // Fallback: dispatch Enter keyevent
        return executeKeyEvent(android.view.KeyEvent.KEYCODE_ENTER)
    }

    private fun executeGlobalAction(action: Int): ActionResult {
        val success = service.performGlobalAction(action)
        return ActionResult(success, if (!success) "Global action failed" else null)
    }

    private suspend fun executeLongPress(x: Int, y: Int): ActionResult {
        val node = service.findNodeAt(x, y)
        if (node != null) {
            try {
                if (node.performAction(AccessibilityNodeInfo.ACTION_LONG_CLICK)) {
                    return ActionResult(true)
                }
            } finally {
                node.recycle()
            }
        }
        return dispatchSwipeGesture(x, y, x, y, 1000)
    }

    private suspend fun executeSwipe(x1: Int, y1: Int, x2: Int, y2: Int, duration: Int): ActionResult {
        return dispatchSwipeGesture(x1, y1, x2, y2, duration)
    }

    private fun executeLaunch(packageName: String): ActionResult {
        val intent = service.packageManager.getLaunchIntentForPackage(packageName)
            ?: return ActionResult(false, "Package not found: $packageName")
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        service.startActivity(intent)
        return ActionResult(true)
    }

    private fun executeClear(): ActionResult {
        val focused = findFocusedNode()
        if (focused != null) {
            try {
                val args = Bundle().apply {
                    putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "")
                }
                if (focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
                    return ActionResult(true)
                }
            } finally {
                focused.recycle()
            }
        }
        return ActionResult(false, "No focused editable node to clear")
    }

    private fun executeClipboardSet(text: String): ActionResult {
        val clipboard = service.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("droidclaw", text)
        clipboard.setPrimaryClip(clip)
        return ActionResult(true)
    }

    private fun executeClipboardGet(): ActionResult {
        val clipboard = service.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString() ?: ""
        return ActionResult(true, data = text)
    }

    private fun executePaste(): ActionResult {
        val focused = findFocusedNode()
        if (focused != null) {
            try {
                if (focused.performAction(AccessibilityNodeInfo.ACTION_PASTE)) {
                    return ActionResult(true)
                }
            } finally {
                focused.recycle()
            }
        }
        return ActionResult(false, "No focused node to paste into")
    }

    private fun executeOpenUrl(url: String): ActionResult {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        service.startActivity(intent)
        return ActionResult(true)
    }

    private fun executeKeyEvent(code: Int): ActionResult {
        return try {
            Runtime.getRuntime().exec(arrayOf("input", "keyevent", code.toString()))
            ActionResult(true)
        } catch (e: Exception) {
            ActionResult(false, "keyevent failed: ${e.message}")
        }
    }

    private fun executeOpenSettings(): ActionResult {
        val intent = Intent(android.provider.Settings.ACTION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        service.startActivity(intent)
        return ActionResult(true)
    }

    private suspend fun executeWait(duration: Int): ActionResult {
        kotlinx.coroutines.delay(duration.toLong())
        return ActionResult(true)
    }

    private suspend fun dispatchTapGesture(x: Int, y: Int): ActionResult {
        val path = Path().apply { moveTo(x.toFloat(), y.toFloat()) }
        val stroke = GestureDescription.StrokeDescription(path, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture)
    }

    private suspend fun dispatchSwipeGesture(
        x1: Int, y1: Int, x2: Int, y2: Int, duration: Int
    ): ActionResult {
        val path = Path().apply {
            moveTo(x1.toFloat(), y1.toFloat())
            lineTo(x2.toFloat(), y2.toFloat())
        }
        val stroke = GestureDescription.StrokeDescription(path, 0, duration.toLong())
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        return dispatchGesture(gesture)
    }

    private suspend fun dispatchGesture(gesture: GestureDescription): ActionResult =
        suspendCancellableCoroutine { cont ->
            service.dispatchGesture(
                gesture,
                object : AccessibilityService.GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(ActionResult(true))
                    }
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        if (cont.isActive) cont.resume(ActionResult(false, "Gesture cancelled"))
                    }
                },
                null
            )
        }

    private fun findFocusedNode(): AccessibilityNodeInfo? {
        return service.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
    }
}
