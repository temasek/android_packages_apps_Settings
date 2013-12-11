/*
 * Copyright (C) 2012 Slimroms
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

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.provider.Settings;

import com.android.internal.util.temasek.DeviceUtils;

import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.temasek.quicksettings.QuickSettingsUtil;
import com.android.settings.R;
import com.android.settings.temasek.SeekBarPreference;

public class NotificationDrawerQsSettings extends SettingsPreferenceFragment
            implements OnPreferenceChangeListener  {

    public static final String TAG = "NotificationDrawerSettings";

    private static final String PREF_NOTIFICATION_HIDE_CARRIER =
            "notification_hide_carrier";
    private static final String PREF_NOTIFICATION_ALPHA =
            "notification_alpha";
    private static final String QUICK_PULLDOWN = "quick_pulldown";

    CheckBoxPreference mHideCarrier;
    SeekBarPreference mNotificationAlpha;
    ListPreference mQuickPulldown;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.notification_drawer_qs_settings);

        PreferenceScreen prefs = getPreferenceScreen();

        mHideCarrier = (CheckBoxPreference) findPreference(PREF_NOTIFICATION_HIDE_CARRIER);
        boolean hideCarrier = Settings.System.getInt(getActivity().getContentResolver(),
                Settings.System.NOTIFICATION_HIDE_CARRIER, 0) == 1;
        mHideCarrier.setChecked(hideCarrier);
        mHideCarrier.setOnPreferenceChangeListener(this);

        PackageManager pm = getPackageManager();
        boolean isMobileData = pm.hasSystemFeature(PackageManager.FEATURE_TELEPHONY);

        if (!DeviceUtils.isPhone(getActivity())
            || !DeviceUtils.deviceSupportsMobileData(getActivity())) {
            // Nothing for tablets, large screen devices and non mobile devices which doesn't show
            // information in notification drawer.....remove options
            prefs.removePreference(mHideCarrier);
        }

        float transparency;
        try{
            transparency = Settings.System.getFloat(getContentResolver(),
                    Settings.System.NOTIFICATION_ALPHA);
        } catch (Exception e) {
            transparency = 0;
            Settings.System.putFloat(getContentResolver(),
                    Settings.System.NOTIFICATION_ALPHA, 0.0f);
        }
        mNotificationAlpha = (SeekBarPreference) findPreference(PREF_NOTIFICATION_ALPHA);
        mNotificationAlpha.setInitValue((int) (transparency * 100));
        mNotificationAlpha.setProperty(Settings.System.NOTIFICATION_ALPHA);
        mNotificationAlpha.setOnPreferenceChangeListener(this);

        mQuickPulldown = (ListPreference) findPreference(QUICK_PULLDOWN);
        if (!DeviceUtils.isPhone(getActivity())) {
            prefs.removePreference(mQuickPulldown);
        } else {
            mQuickPulldown.setOnPreferenceChangeListener(this);
            int statusQuickPulldown = Settings.System.getInt(getContentResolver(),
                    Settings.System.QS_QUICK_PULLDOWN, 0);
            mQuickPulldown.setValue(String.valueOf(statusQuickPulldown));
            updatePulldownSummary();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        QuickSettingsUtil.updateAvailableTiles(getActivity());
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mHideCarrier) {
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.NOTIFICATION_HIDE_CARRIER,
                    (Boolean) newValue ? 1 : 0);
            return true;
        } else if (preference == mNotificationAlpha) {
            float valNav = Float.parseFloat((String) newValue);
            Settings.System.putFloat(getContentResolver(),
                    Settings.System.NOTIFICATION_ALPHA, valNav / 100);
            return true;
        } else if (preference == mQuickPulldown) {
            int statusQuickPulldown = Integer.valueOf((String) newValue);
            Settings.System.putInt(getContentResolver(), Settings.System.QS_QUICK_PULLDOWN,
                    statusQuickPulldown);
            updatePulldownSummary();
            return true;
        }
        return false;
    }

    private void updatePulldownSummary() {
        int summaryId;
        int directionId;
        summaryId = R.string.summary_quick_pulldown;
        String value = Settings.System.getString(getContentResolver(),
                Settings.System.QS_QUICK_PULLDOWN);
        String[] pulldownArray = getResources().getStringArray(R.array.quick_pulldown_values);
        if (pulldownArray[0].equals(value)) {
            directionId = R.string.quick_pulldown_off;
            mQuickPulldown.setValueIndex(0);
            mQuickPulldown.setSummary(getResources().getString(directionId));
        } else if (pulldownArray[1].equals(value)) {
            directionId = R.string.quick_pulldown_right;
            mQuickPulldown.setValueIndex(1);
            mQuickPulldown.setSummary(getResources().getString(directionId)
                    + " " + getResources().getString(summaryId));
        } else {
            directionId = R.string.quick_pulldown_left;
            mQuickPulldown.setValueIndex(2);
            mQuickPulldown.setSummary(getResources().getString(directionId)
                    + " " + getResources().getString(summaryId));
        }
    }

}
