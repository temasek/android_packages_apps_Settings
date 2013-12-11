/*
 * Copyright (C) 2013 Slimroms
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.temasek;

import android.app.AlertDialog;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.util.Log;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;

public class InputMethodsSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "KeyboardInputSettings";

    private static final String PREF_DISABLE_FULLSCREEN_KEYBOARD = "disable_fullscreen_keyboard";
    private static final String KEY_IME_SWITCHER = "status_bar_ime_switcher";
    private static final String VOLUME_KEY_CURSOR_CONTROL = "volume_key_cursor_control";
    private static final String KEYBOARD_ROTATION_TOGGLE = "keyboard_rotation_toggle";
    private static final String KEYBOARD_ROTATION_TIMEOUT = "keyboard_rotation_timeout";
    private static final String SHOW_ENTER_KEY = "show_enter_key";

    private static final int KEYBOARD_ROTATION_TIMEOUT_DEFAULT = 5000; // 5s

    private CheckBoxPreference mDisableFullscreenKeyboard;
    private CheckBoxPreference mStatusBarImeSwitcher;
    private ListPreference mVolumeKeyCursorControl;
    private CheckBoxPreference mKeyboardRotationToggle;
    private ListPreference mKeyboardRotationTimeout;
    private CheckBoxPreference mShowEnterKey;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.input_methods_settings);

        mDisableFullscreenKeyboard =
            (CheckBoxPreference) findPreference(PREF_DISABLE_FULLSCREEN_KEYBOARD);
        mDisableFullscreenKeyboard.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.DISABLE_FULLSCREEN_KEYBOARD, 0) == 1);
        mDisableFullscreenKeyboard.setOnPreferenceChangeListener(this);

        // Enable or disable mStatusBarImeSwitcher based on boolean value: config_show_cmIMESwitcher
        final Preference keyImeSwitcherPref = findPreference(KEY_IME_SWITCHER);
        if (keyImeSwitcherPref != null) {
            if (!getResources().getBoolean(com.android.internal.R.bool.config_show_cmIMESwitcher)) {
                getPreferenceScreen().removePreference(keyImeSwitcherPref);
            } else {
                mStatusBarImeSwitcher = (CheckBoxPreference) keyImeSwitcherPref;
                mStatusBarImeSwitcher.setOnPreferenceChangeListener(this);
            }
        }

        mVolumeKeyCursorControl = (ListPreference) findPreference(VOLUME_KEY_CURSOR_CONTROL);
        if(mVolumeKeyCursorControl != null) {
            mVolumeKeyCursorControl.setValue(Integer.toString(Settings.System.getInt(
                    getContentResolver(), Settings.System.VOLUME_KEY_CURSOR_CONTROL, 0)));
            mVolumeKeyCursorControl.setSummary(mVolumeKeyCursorControl.getEntry());
            mVolumeKeyCursorControl.setOnPreferenceChangeListener(this);
        }

        mKeyboardRotationToggle = (CheckBoxPreference) findPreference(KEYBOARD_ROTATION_TOGGLE);
        mKeyboardRotationToggle.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.KEYBOARD_ROTATION_TIMEOUT, 0) > 0);
        mKeyboardRotationToggle.setOnPreferenceChangeListener(this);

        mKeyboardRotationTimeout = (ListPreference) findPreference(KEYBOARD_ROTATION_TIMEOUT);
        mKeyboardRotationTimeout.setOnPreferenceChangeListener(this);
        updateRotationTimeout(Settings.System.getInt(
                getContentResolver(), Settings.System.KEYBOARD_ROTATION_TIMEOUT,
                KEYBOARD_ROTATION_TIMEOUT_DEFAULT));

        mShowEnterKey = (CheckBoxPreference) findPreference(SHOW_ENTER_KEY);
        mShowEnterKey.setChecked(Settings.System.getInt(getContentResolver(),
                Settings.System.FORMAL_TEXT_INPUT, 0) == 1);
        mShowEnterKey.setOnPreferenceChangeListener(this);
    }

    public void updateRotationTimeout(int timeout) {
        if (timeout == 0)
            timeout = KEYBOARD_ROTATION_TIMEOUT_DEFAULT;
        mKeyboardRotationTimeout.setValue(Integer.toString(timeout));
        mKeyboardRotationTimeout.setSummary(
            getString(R.string.keyboard_rotation_timeout_summary,
            mKeyboardRotationTimeout.getEntry()));
    }

    @Override
    public void onResume() {
        super.onResume();

        if (mStatusBarImeSwitcher != null) {
            mStatusBarImeSwitcher.setChecked(Settings.System.getInt(getContentResolver(),
                    Settings.System.STATUS_BAR_IME_SWITCHER, 1) != 0);
        }

    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public void mKeyboardRotationDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setMessage(R.string.keyboard_rotation_dialog);
        builder.setCancelable(false);
        builder.setPositiveButton(getResources().getString(com.android.internal.R.string.ok), null);
        AlertDialog alert = builder.create();
        alert.show();
    }

    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mVolumeKeyCursorControl) {
            String volumeKeyCursorControl = (String) objValue;
            int val = Integer.parseInt(volumeKeyCursorControl);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.VOLUME_KEY_CURSOR_CONTROL, val);
            int index = mVolumeKeyCursorControl.findIndexOfValue(volumeKeyCursorControl);
            mVolumeKeyCursorControl.setSummary(mVolumeKeyCursorControl.getEntries()[index]);
            return true;
        } else if (preference == mDisableFullscreenKeyboard) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.DISABLE_FULLSCREEN_KEYBOARD,  (Boolean) objValue ? 1 : 0);
            return true;
        } else if (preference == mStatusBarImeSwitcher) {
            Settings.System.putInt(getContentResolver(),
                Settings.System.STATUS_BAR_IME_SWITCHER, (Boolean) objValue ? 1 : 0);
            return true;
        } else if (preference == mKeyboardRotationToggle) {
            boolean isAutoRotate = (Settings.System.getInt(getContentResolver(),
                        Settings.System.ACCELEROMETER_ROTATION, 0) == 1);
            if (isAutoRotate && mKeyboardRotationToggle.isChecked())
                mKeyboardRotationDialog();
            Settings.System.putInt(getContentResolver(),
                    Settings.System.KEYBOARD_ROTATION_TIMEOUT,
                    (Boolean) objValue ? KEYBOARD_ROTATION_TIMEOUT_DEFAULT : 0);
            updateRotationTimeout(KEYBOARD_ROTATION_TIMEOUT_DEFAULT);
            return true;
        } else if (preference == mShowEnterKey) {
            Settings.System.putInt(getContentResolver(),
                Settings.System.FORMAL_TEXT_INPUT, (Boolean) objValue ? 1 : 0);
            return true;
        } else if (preference == mKeyboardRotationTimeout) {
            int timeout = Integer.parseInt((String) objValue);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.KEYBOARD_ROTATION_TIMEOUT, timeout);
            updateRotationTimeout(timeout);
            return true;
        }
        return false;
    }
}
