package com.android.settings.crdroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.view.Gravity;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class HoverSettings extends SettingsPreferenceFragment implements
        Preference.OnPreferenceChangeListener {
    private static final String TAG = "HoverSettings";

    private static final String PREF_HOVER_STATE = "hover_state";
    private static final String PREF_HOVER_LONG_FADE_OUT_DELAY = "hover_long_fade_out_delay";
    private static final String PREF_HOVER_EXCLUDE_NON_CLEARABLE = "hover_exclude_non_clearable";
    private static final String PREF_HOVER_EXCLUDE_LOW_PRIORITY = "hover_exclude_low_priority";
    private static final String PREF_HOVER_REQUIRE_FULLSCREEN_MODE = "hover_require_fullscreen_mode";
    private static final String PREF_HOVER_EXCLUDE_TOPMOST = "hover_exclude_topmost";
    private static final String PREF_HOVER_EXCLUDE_FROM_INSECURE_LOCK_SCREEN = "hover_exclude_from_insecure_lock_screen";

    CheckBoxPreference mHoverState;
    ListPreference mHoverLongFadeOutDelay;
    CheckBoxPreference mHoverExcludeNonClearable;
    CheckBoxPreference mHoverExcludeNonLowPriority;
    CheckBoxPreference mHoverRequireFullScreenMode;
    CheckBoxPreference mHoverExcludeTopmost;
    CheckBoxPreference mHoverExcludeFromInsecureLockScreen;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.hover_settings);
        PreferenceScreen prefSet = getPreferenceScreen();

        mHoverState = (CheckBoxPreference) findPreference(PREF_HOVER_STATE);
        mHoverState.setChecked(Settings.System.getIntForUser(getContentResolver(),
                Settings.System.HOVER_STATE, 0, UserHandle.USER_CURRENT) == 1);
        mHoverState.setOnPreferenceChangeListener(this);

        mHoverLongFadeOutDelay = (ListPreference) prefSet.findPreference(PREF_HOVER_LONG_FADE_OUT_DELAY);
        int hoverLongFadeOutDelay = Settings.System.getIntForUser(getContentResolver(),
                Settings.System.HOVER_LONG_FADE_OUT_DELAY, 5000, UserHandle.USER_CURRENT);
        mHoverLongFadeOutDelay.setValue(String.valueOf(hoverLongFadeOutDelay));
        mHoverLongFadeOutDelay.setSummary(mHoverLongFadeOutDelay.getEntry());
        mHoverLongFadeOutDelay.setOnPreferenceChangeListener(this);

        mHoverExcludeNonClearable = (CheckBoxPreference) findPreference(PREF_HOVER_EXCLUDE_NON_CLEARABLE);
        mHoverExcludeNonClearable.setChecked(Settings.System.getIntForUser(getContentResolver(),
                Settings.System.HOVER_EXCLUDE_NON_CLEARABLE, 0, UserHandle.USER_CURRENT) == 1);
        mHoverExcludeNonClearable.setOnPreferenceChangeListener(this);

        mHoverExcludeNonLowPriority = (CheckBoxPreference) findPreference(PREF_HOVER_EXCLUDE_LOW_PRIORITY);
        mHoverExcludeNonLowPriority.setChecked(Settings.System.getIntForUser(getContentResolver(),
                Settings.System.HOVER_EXCLUDE_LOW_PRIORITY, 0, UserHandle.USER_CURRENT) == 1);
        mHoverExcludeNonLowPriority.setOnPreferenceChangeListener(this);

        mHoverRequireFullScreenMode = (CheckBoxPreference) findPreference(PREF_HOVER_REQUIRE_FULLSCREEN_MODE);
        mHoverRequireFullScreenMode.setChecked(Settings.System.getIntForUser(getContentResolver(),
                Settings.System.HOVER_REQUIRE_FULLSCREEN_MODE, 0, UserHandle.USER_CURRENT) == 1);
        mHoverRequireFullScreenMode.setOnPreferenceChangeListener(this);

        mHoverExcludeTopmost = (CheckBoxPreference) findPreference(PREF_HOVER_EXCLUDE_TOPMOST);
        mHoverExcludeTopmost.setChecked(Settings.System.getIntForUser(getContentResolver(),
                Settings.System.HOVER_EXCLUDE_TOPMOST, 0, UserHandle.USER_CURRENT) == 1);
        mHoverExcludeTopmost.setOnPreferenceChangeListener(this);

        mHoverExcludeFromInsecureLockScreen = (CheckBoxPreference) findPreference(PREF_HOVER_EXCLUDE_FROM_INSECURE_LOCK_SCREEN);
        mHoverExcludeFromInsecureLockScreen.setChecked(Settings.System.getIntForUser(getContentResolver(),
                Settings.System.HOVER_EXCLUDE_FROM_INSECURE_LOCK_SCREEN, 0, UserHandle.USER_CURRENT) == 1);
        mHoverExcludeFromInsecureLockScreen.setOnPreferenceChangeListener(this);

        UpdateSettings();
    }

    @Override
    public void onResume() {
        super.onResume();
        UpdateSettings();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    public void UpdateSettings() {}

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        if (preference == mHoverState) {
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.HOVER_STATE,
                    (Boolean) objValue ? 1 : 0, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mHoverLongFadeOutDelay) {
            int index = mHoverLongFadeOutDelay.findIndexOfValue((String) objValue);
            int hoverLongFadeOutDelay = Integer.valueOf((String) objValue);
            Settings.System.putIntForUser(getContentResolver(),
                Settings.System.HOVER_LONG_FADE_OUT_DELAY,
                    hoverLongFadeOutDelay, UserHandle.USER_CURRENT);
            mHoverLongFadeOutDelay.setSummary(mHoverLongFadeOutDelay.getEntries()[index]);
            return true;
        } else if (preference == mHoverExcludeNonClearable) {
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.HOVER_EXCLUDE_NON_CLEARABLE,
                    (Boolean) objValue ? 1 : 0, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mHoverExcludeNonLowPriority) {
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.HOVER_EXCLUDE_LOW_PRIORITY,
                    (Boolean) objValue ? 1 : 0, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mHoverRequireFullScreenMode) {
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.HOVER_REQUIRE_FULLSCREEN_MODE,
                    (Boolean) objValue ? 1 : 0, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mHoverExcludeTopmost) {
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.HOVER_EXCLUDE_TOPMOST,
                    (Boolean) objValue ? 1 : 0, UserHandle.USER_CURRENT);
            return true;
        } else if (preference == mHoverExcludeFromInsecureLockScreen) {
            Settings.System.putIntForUser(getContentResolver(),
                    Settings.System.HOVER_EXCLUDE_FROM_INSECURE_LOCK_SCREEN,
                    (Boolean) objValue ? 1 : 0, UserHandle.USER_CURRENT);
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }
}
