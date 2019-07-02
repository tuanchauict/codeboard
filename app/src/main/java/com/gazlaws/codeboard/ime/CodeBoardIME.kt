package com.gazlaws.codeboard.ime

import android.annotation.SuppressLint
import android.content.Context
import android.inputmethodservice.InputMethodService
import android.inputmethodservice.Keyboard
import android.inputmethodservice.KeyboardView
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.support.annotation.IntegerRes
import android.view.KeyEvent
import android.view.KeyEvent.KEYCODE_CTRL_LEFT
import android.view.KeyEvent.KEYCODE_SHIFT_LEFT
import android.view.KeyEvent.META_CTRL_ON
import android.view.KeyEvent.META_SHIFT_ON
import android.view.View
import android.view.ViewConfiguration
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import com.gazlaws.codeboard.BooleanMap
import com.gazlaws.codeboard.Preferences
import com.gazlaws.codeboard.R
import com.gazlaws.codeboard.sendKeyEventDownUpWithActionBetween
import com.gazlaws.codeboard.sendKeyEventOnce

/**
 * Created by Ruby(aka gazlaws) on 13/02/2016.
 * Kotlinized by Tuan Chau (aka tuanchauict)
 */
class CodeBoardIME : InputMethodService(), KeyboardView.OnKeyboardActionListener {
    var keyboardView: KeyboardView? = null
        private set
    private lateinit var sEditorInfo: EditorInfo
    private var isCtrlOn = false

    @IntegerRes
    private var currentKeyboardMode = R.integer.keyboard_normal
    private var switchedKeyboard = false

    private val uiHandler = Handler(Looper.getMainLooper())

    private val characterLongPressController: CharacterLongPressController =
        CharacterLongPressController(this)

    private val shiftKeyPressHandler: ShiftKeyPressHandler = ShiftKeyPressHandler(this)

    private val preferences: Preferences by lazy { Preferences(applicationContext) }

    private val mapKeyCodeToOnKeyAction: Map<Int, () -> Unit?> = mapOf(
        Keycode.ESCAPE to {
            currentInputConnection.sendKeyEventOnce(
                KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_ESCAPE,
                MetaState.CONTROL_ON
            )
        },
        Keycode.INPUT_METHOD_PICKER to {
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        },
        Keycode.SYM_MODE to {
            val newKeyboardMode = if (currentKeyboardMode == R.integer.keyboard_normal) {
                R.integer.keyboard_sym
            } else {
                R.integer.keyboard_normal
            }
            currentKeyboardMode = newKeyboardMode
            keyboardView?.keyboard = chooseKeyboard(newKeyboardMode)
            controlKeyUpdateView()
            shiftKeyPressHandler.updateViewByShiftKey()
        },
        Keycode.CONTROL to {
            val controlKeyAction = if (isCtrlOn) KeyEvent.ACTION_UP else KeyEvent.ACTION_DOWN
            currentInputConnection.sendKeyEventOnce(
                controlKeyAction,
                KEYCODE_CTRL_LEFT,
                MetaState.CONTROL_ON
            )
            isCtrlOn = !isCtrlOn
            controlKeyUpdateView()
        },
        Keycode.DPAD_LEFT to {
            handleArrow(KeyEvent.KEYCODE_DPAD_LEFT)
        },
        Keycode.DPAD_RIGHT to {
            handleArrow(KeyEvent.KEYCODE_DPAD_RIGHT)
        }
    )

    private fun onKeyCtrl(code: Int) {
        val codeChar = code.toChar().toUpperCase()
        if (sEditorInfo.isDroidEdit() && codeChar in DROID_EDIT_PROBLEM_KEY_CODES) {
            val actionKey =
                if (codeChar == 'Z' && !shiftKeyPressHandler.isShiftOn) 'z' else codeChar
            val action = DROID_EDIT_PROBLEM_KEY_CODES[actionKey] ?: return
            currentInputConnection?.performContextMenuAction(action)
            return
        }
        val keyCode = CHAR_TO_KEYCODE_MAP[codeChar]
        if (keyCode == null) {
            currentInputConnection?.commitText("$codeChar", 1)
            shiftKeyPressHandler.releaseShiftKeyWhenNotLocked()
            return
        }

        val metaState = if (codeChar == 'Z' && shiftKeyPressHandler.isShiftOn) {
            MetaState.SHIFT_CONTROL_ON
        } else {
            MetaState.CONTROL_ON
        }

        currentInputConnection.sendKeyEventOnce(
            KeyEvent.ACTION_DOWN,
            keyCode,
            metaState,
            System.currentTimeMillis() + 1
        )
    }

    override fun onKey(primaryCode: Int, keyCodes: IntArray) {
        if (shiftKeyPressHandler.onKey(primaryCode)) {
            return
        }
        KEYCODE_TO_MENU_ACTION_MAP[primaryCode]?.also {
            currentInputConnection?.performContextMenuAction(it)
            return
        }
        KEYCODE_TO_SIMPLE_DOWN_UP_KEY_EVENT_MAP[primaryCode]?.also {
            sendDownUpKeyEvents(it)
            return
        }
        mapKeyCodeToOnKeyAction[primaryCode]?.also { action ->
            action.invoke()
            return
        }

        val code = primaryCode.toChar()
        when {
            isCtrlOn -> {
                onKeyCtrl(primaryCode)
                shiftKeyPressHandler.releaseShiftKeyWhenNotLocked()
                isCtrlOn = false
                controlKeyUpdateView()
            }
            shiftKeyPressHandler.isShiftOn && code.isLetter() -> {
                currentInputConnection?.commitText("${code.toUpperCase()}", 1)
                shiftKeyPressHandler.releaseShiftKeyWhenNotLocked()
            }
            else -> {
                if (!switchedKeyboard && !characterLongPressController.isLongPressSuccess) {
                    currentInputConnection?.commitText("$code", 1)
                }
                switchedKeyboard = false
            }
        }
    }

    override fun onPress(primaryCode: Int) {
        if (preferences.isSoundOn) {
            MediaPlayer.create(this, R.raw.keypress_sound).run {
                setOnCompletionListener { mp -> mp.release() }
                start()
            }
        }
        if (preferences.isVibrateOn) {
            vibrate(20)
        }

        uiHandler.removeCallbacksAndMessages(null)
        uiHandler.postDelayed({
            try {
                onKeyLongPress(primaryCode)
            } catch (_: Exception) {
            }
        }, ViewConfiguration.getLongPressTimeout().toLong())

        characterLongPressController.fire(primaryCode)
    }

    override fun onRelease(primaryCode: Int) {
        uiHandler.removeCallbacksAndMessages(null)
        characterLongPressController.release()
    }

    private fun onKeyLongPress(keyCode: Int) {
        if (keyCode == Keycode.SPACE) {
            switchedKeyboard = true
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.showInputMethodPicker()
        }

        vibrate(50L)
    }

    override fun onText(text: CharSequence) {
        val inputConnection = currentInputConnection ?: return
        inputConnection.commitText(text, 1)

        val numberOfRepeats = if ("for" in text) 7 else 3
        repeat(numberOfRepeats) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DPAD_LEFT)
        }
    }

    override fun swipeDown() {
        keyboardView?.closing()
    }

    override fun swipeLeft() = Unit

    override fun swipeRight() = Unit

    override fun swipeUp() = Unit

    private fun chooseKeyboard(@IntegerRes keyboardMode: Int): Keyboard =
        Keyboard(this, R.xml.code_1, keyboardMode)

    @SuppressLint("InflateParams")
    override fun onCreateInputView(): View? {
        val keyboardView =
            layoutInflater.inflate(R.layout.keyboard, null) as? KeyboardView ?: return null
        this.keyboardView = keyboardView

        shiftKeyPressHandler.reset()

        keyboardView.isPreviewEnabled = preferences.isPreviewEnabled

        isCtrlOn = false

        currentKeyboardMode = R.integer.keyboard_normal
        //reset to normal

        val keyboard = chooseKeyboard(currentKeyboardMode)
        keyboardView.keyboard = keyboard
        keyboardView.setOnKeyboardActionListener(this)

        return keyboardView
    }

    override fun onStartInputView(attribute: EditorInfo, restarting: Boolean) {
        super.onStartInputView(attribute, restarting)
        setInputView(onCreateInputView())
        sEditorInfo = attribute
    }

    private fun controlKeyUpdateView() {
        val nonNullKeyboardView = keyboardView ?: return
        val nonNullKeyboard = nonNullKeyboardView.keyboard ?: return

        val index = nonNullKeyboard.keys.indexOfFirst { it.label in TEXT_CONTROL.values }
        nonNullKeyboard.keys.getOrNull(index)?.label = TEXT_CONTROL[isCtrlOn]
        nonNullKeyboardView.invalidateKey(index)
    }

    private fun handleArrow(keyCode: Int) =
        when {
            isCtrlOn && shiftKeyPressHandler.isShiftOn -> currentInputConnection
                ?.sendKeyEventDownUpWithActionBetween(
                    KEYCODE_CTRL_LEFT,
                    MetaState.SHIFT_CONTROL_ON
                ) { moveSelection(keyCode) }
            shiftKeyPressHandler.isShiftOn -> moveSelection(keyCode)
            isCtrlOn -> currentInputConnection
                ?.sendKeyEventOnce(
                    KeyEvent.ACTION_DOWN,
                    keyCode,
                    MetaState.CONTROL_ON
                )
            else -> sendDownUpKeyEvents(keyCode)
        }

    private fun moveSelection(keyCode: Int) =
        currentInputConnection?.sendKeyEventDownUpWithActionBetween(
            KEYCODE_SHIFT_LEFT,
            MetaState.SHIFT_CONTROL_ON
        ) {
            val metaState = if (isCtrlOn) MetaState.SHIFT_CONTROL_ON else MetaState.SHIFT_ON
            currentInputConnection.sendKeyEventOnce(KeyEvent.ACTION_DOWN, keyCode, metaState)
        }

    @Suppress("DEPRECATION")
    private fun vibrate(durationMillis: Long) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(durationMillis)
    }

    private fun EditorInfo.isDroidEdit(): Boolean = imeOptions == DROID_EDIT_IME_OPTIONS

    enum class MetaState(val value: Int) {
        SHIFT_ON(META_CTRL_ON),
        CONTROL_ON(META_CTRL_ON),
        SHIFT_CONTROL_ON(META_SHIFT_ON or META_CTRL_ON)
    }

    companion object {
        private const val DROID_EDIT_IME_OPTIONS = 1342177286


        private val KEYCODE_TO_MENU_ACTION_MAP = mapOf(
            Keycode.SELECT_ALL to android.R.id.selectAll,
            Keycode.CUT to android.R.id.cut,
            Keycode.COPY to android.R.id.copy,
            Keycode.PASTE to android.R.id.paste,
            Keycode.UNDO to android.R.id.undo,
            Keycode.REDO to android.R.id.redo
        )

        private val KEYCODE_TO_SIMPLE_DOWN_UP_KEY_EVENT_MAP = mapOf(
            Keycode.DELETE to KeyEvent.KEYCODE_DEL,
            Keycode.DONE to KeyEvent.KEYCODE_ENTER,
            Keycode.TAB to KeyEvent.KEYCODE_TAB,
            Keycode.DPAD_DOWN to KeyEvent.KEYCODE_DPAD_DOWN,
            Keycode.DPAD_UP to KeyEvent.KEYCODE_DPAD_UP
        )

        private val DROID_EDIT_PROBLEM_KEY_CODES = mapOf(
            'A' to android.R.id.selectAll,
            'C' to android.R.id.copy,
            'V' to android.R.id.paste,
            'X' to android.R.id.cut,
            'z' to android.R.id.undo,
            'Z' to android.R.id.redo
        )

        private val CHAR_TO_KEYCODE_MAP = mapOf(
            'A' to KeyEvent.KEYCODE_A,
            'B' to KeyEvent.KEYCODE_B,
            'C' to KeyEvent.KEYCODE_C,
            'D' to KeyEvent.KEYCODE_D,
            'E' to KeyEvent.KEYCODE_E,
            'F' to KeyEvent.KEYCODE_F,
            'G' to KeyEvent.KEYCODE_G,
            'H' to KeyEvent.KEYCODE_H,
            'I' to KeyEvent.KEYCODE_I,
            'J' to KeyEvent.KEYCODE_J,
            'K' to KeyEvent.KEYCODE_K,
            'L' to KeyEvent.KEYCODE_L,
            'M' to KeyEvent.KEYCODE_M,
            'N' to KeyEvent.KEYCODE_N,
            'O' to KeyEvent.KEYCODE_O,
            'P' to KeyEvent.KEYCODE_P,
            'Q' to KeyEvent.KEYCODE_Q,
            'R' to KeyEvent.KEYCODE_R,
            'S' to KeyEvent.KEYCODE_S,
            'T' to KeyEvent.KEYCODE_T,
            'U' to KeyEvent.KEYCODE_U,
            'V' to KeyEvent.KEYCODE_V,
            'W' to KeyEvent.KEYCODE_W,
            'X' to KeyEvent.KEYCODE_X,
            'Y' to KeyEvent.KEYCODE_Y,
            'Z' to KeyEvent.KEYCODE_Z
        )

        private val TEXT_CONTROL = BooleanMap("CTRL", "Ctrl")
    }
}
