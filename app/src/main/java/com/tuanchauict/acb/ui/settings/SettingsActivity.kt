package com.tuanchauict.acb.ui.settings

import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import com.tuanchauict.acb.Preferences
import com.tuanchauict.acb.R

class SettingsActivity : AppCompatActivity() {
    private val preferences: Preferences by lazy { Preferences(applicationContext) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)
        actionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        initLongPressMovingCursor()

        initSoundOnKeypress()
        initVibrationOnKeypress()
        initPopupOnKeypress()
        initLongPressDelay()
    }

    private fun initLongPressMovingCursor() {
        SettingSwitchItemController(
            findViewById(R.id.setting_long_press_moving_cursor),
            R.string.setting_long_press_moving_cursor_title,
            R.string.setting_long_press_moving_cursor_subtitle,
            preferences.isLongPressMovingCursor
        ) {
            preferences.isLongPressMovingCursor = !preferences.isLongPressMovingCursor
            setChecked(preferences.isLongPressMovingCursor)
        }
    }

    private fun initSoundOnKeypress() {
        val progressInfo = SeekbarDialog.ProgressInfo(0, 100, 50) { "$it %" }
        val volumeOnKeypress = SettingSimpleClickItem(
            findViewById(R.id.volume_on_keypress),
            R.string.setting_volume_on_keypress,
            progressInfo.toText(preferences.soundVolume)
        ) {
            SeekbarDialog(
                this@SettingsActivity,
                R.string.setting_volume_on_keypress,
                preferences.soundVolume,
                progressInfo
            ) {
                preferences.soundVolume = it
                setSubtitle(progressInfo.toText(it))
            }
        }
        volumeOnKeypress.setEnabled(preferences.isSoundOn)

        SettingSwitchItemController(
            findViewById(R.id.setting_sound_on_keypress),
            R.string.sound_on_keypress,
            isChecked = preferences.isSoundOn
        ) {
            preferences.isSoundOn = !preferences.isSoundOn
            setChecked(preferences.isSoundOn)
            volumeOnKeypress.setEnabled(preferences.isSoundOn)
        }
    }

    private fun initVibrationOnKeypress() {
        val progressInfo = SeekbarDialog.ProgressInfo(10, 100, 20) { "$it ms" }

        val vibrationStrength = SettingSimpleClickItem(
            findViewById(R.id.vibrate_strength_on_keypress),
            R.string.setting_vibrate_strength_on_keypress,
            progressInfo.toText(preferences.vibrationStrength)
        ) {
            SeekbarDialog(
                this@SettingsActivity,
                R.string.setting_vibrate_strength_on_keypress,
                preferences.vibrationStrength,
                progressInfo
            ) {
                preferences.vibrationStrength = it
                setSubtitle(progressInfo.toText(it))
            }
        }
        vibrationStrength.setEnabled(preferences.isVibrateOn)

        SettingSwitchItemController(
            findViewById(R.id.setting_vibrate_on_keypress),
            R.string.setting_vibrate_on_keypress,
            isChecked = preferences.isVibrateOn
        ) {
            preferences.isVibrateOn = !preferences.isVibrateOn
            setChecked(preferences.isVibrateOn)
            vibrationStrength.setEnabled(preferences.isVibrateOn)
        }
    }

    private fun initPopupOnKeypress() {
        SettingSwitchItemController(
            findViewById(R.id.setting_popup_on_keypress),
            R.string.setting_popup_on_keypress,
            isChecked = preferences.isPreviewEnabled
        ) {
            preferences.isPreviewEnabled = !preferences.isPreviewEnabled
            setChecked(preferences.isPreviewEnabled)
        }
    }

    private fun initLongPressDelay() {
        val progressInfo = SeekbarDialog.ProgressInfo(150, 1000, 300) { "$it ms" }

        SettingSimpleClickItem(
            findViewById(R.id.key_long_press_delay),
            R.string.setting_long_press_delay,
            progressInfo.toText(preferences.longPressDelay)
        ) {
            SeekbarDialog(
                this@SettingsActivity,
                R.string.setting_long_press_delay,
                preferences.longPressDelay,
                progressInfo
            ) {
                preferences.longPressDelay = it
                setSubtitle(progressInfo.toText(it))
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            onBackPressed()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
