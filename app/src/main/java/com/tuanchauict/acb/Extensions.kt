package com.tuanchauict.acb

import android.content.Context
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.InputConnection
import android.widget.Toast
import androidx.annotation.StringRes
import com.tuanchauict.acb.ime.MetaState

fun InputConnection?.sendKeyEventOnce(
    action: Int,
    code: Int,
    metaState: MetaState,
    sendingTimeMillis: Long = System.currentTimeMillis()
) {
    if (this == null) {
        return
    }
    val keyEvent = KeyEvent(
        sendingTimeMillis,
        sendingTimeMillis,
        action,
        code,
        0,
        metaState.value
    )
    sendKeyEvent(keyEvent)
}

fun InputConnection?.sendKeyEventDownUpWithActionBetween(
    code: Int,
    metaState: MetaState,
    action: () -> Unit = {}
) {
    sendKeyEventOnce(KeyEvent.ACTION_DOWN, code, metaState)
    action()
    sendKeyEventOnce(KeyEvent.ACTION_UP, code, metaState)
}

var View.isVisible: Boolean
    get() = visibility == View.VISIBLE
    set(value) {
        visibility = if (value) View.VISIBLE else View.GONE
    }

fun Context.toast(@StringRes textRes: Int) =
    Toast.makeText(this, textRes, Toast.LENGTH_SHORT).show()
