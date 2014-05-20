/*
 * Copyright (C) 2014 The OmniROM Project
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

package com.android.settings.crdroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceCategory;
import android.preference.PreferenceScreen;
import android.preference.PreferenceManager;
import android.preference.SwitchPreference;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.settings.crdroid.SeekBarPreferenceCHOS;
import com.android.settings.crdroid.AppMultiSelectListPreference;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.tts.IntentReceiver;
import com.android.settings.R;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class TtsNotification extends SettingsPreferenceFragment implements
        OnPreferenceChangeListener {

    private static final String TAG = "TtsNotification";
    private static final String KEY_VOICE_TTS = "voice_tts";
    private static final String KEY_VOICE_TTS_VOLUME = "voice_tts_volume";
    private static final String KEY_VOICE_TTS_CALL = "voice_tts_call";
    private static final String KEY_VOICE_TTS_SMS = "voice_tts_sms";
    private static final String KEY_VOICE_TTS_SMS_READ = "voice_tts_sms_read";
    private static final String KEY_VOICE_TTS_CHARGE_FULL = "voice_tts_charge_full";
    private static final String KEY_VOICE_TTS_CHARGE_ON = "voice_tts_charge_on";
    private static final String KEY_VOICE_TTS_CHARGE_OFF = "voice_tts_charge_off";
    private static final String KEY_VOICE_TTS_CLOCK = "voice_tts_clock";
    private static final String KEY_VOICE_TTS_DATE = "voice_tts_date";
    private static final String KEY_VOICE_TTS_MUSIC = "voice_tts_music";
    private static final String KEY_VOICE_TTS_NOTIF = "voice_tts_notif";
    private static final String KEY_VOICE_TTS_NOTIF_READ = "voice_tts_notif_read";
    private static final String KEY_VOICE_TTS_INCLUDED_APPS = "voice_tts_whitelist";
    private static final String KEY_VOICE_TTS_ANNOY_NOTIF = "voice_tts_annoy_notif";

    private static final int MULTIPLIER_VOLUME = 10;

    private static final int MENU_RESET = Menu.FIRST;

    private SwitchPreference mEnableVoiceTTS;
    private SeekBarPreferenceCHOS mVoiceVolume;
    private CheckBoxPreference mEnableVoiceTTScall;
    private CheckBoxPreference mEnableVoiceTTSsms;
    private CheckBoxPreference mEnableVoiceTTSsmsRead;
    private CheckBoxPreference mEnableVoiceTTSchargeFull;
    private CheckBoxPreference mEnableVoiceTTSchargeOn;
    private CheckBoxPreference mEnableVoiceTTSchargeOff;
    private CheckBoxPreference mEnableVoiceTTSclock;
    private CheckBoxPreference mEnableVoiceTTSdate;
    private CheckBoxPreference mEnableVoiceTTSmusic;
    private CheckBoxPreference mEnableVoiceTTSnotif;
    private CheckBoxPreference mEnableVoiceTTSnotifRead;
    private AppMultiSelectListPreference mIncludedAppsPref;
    private ListPreference mAnnoyingNotifications;

    private boolean isEngineReady;
    private SharedPreferences mShareprefs;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.tts_notif_settings);

        Context context = getActivity();
        PreferenceScreen prefSet = getPreferenceScreen();

        mShareprefs = PreferenceManager.getDefaultSharedPreferences(context);

        mEnableVoiceTTS = (SwitchPreference) prefSet.findPreference(KEY_VOICE_TTS);
        mEnableVoiceTTS.setChecked(mShareprefs.getBoolean(IntentReceiver.ENABLED, false));
        mEnableVoiceTTS.setOnPreferenceChangeListener(this);

        mVoiceVolume = (SeekBarPreferenceCHOS) prefSet.findPreference(KEY_VOICE_TTS_VOLUME);
        mVoiceVolume.setValue((mShareprefs.getInt(IntentReceiver.VOICE_VOLUME, 8) * MULTIPLIER_VOLUME));
        mVoiceVolume.setOnPreferenceChangeListener(this);

        mEnableVoiceTTScall = (CheckBoxPreference) prefSet.findPreference(KEY_VOICE_TTS_CALL);
        mEnableVoiceTTScall.setChecked(mShareprefs.getBoolean(IntentReceiver.ENABLED_CALL, false));
        mEnableVoiceTTScall.setOnPreferenceChangeListener(this);

        mEnableVoiceTTSsms = (CheckBoxPreference) prefSet.findPreference(KEY_VOICE_TTS_SMS);
        mEnableVoiceTTSsms.setChecked(mShareprefs.getBoolean(IntentReceiver.ENABLED_SMS, false));
        mEnableVoiceTTSsms.setOnPreferenceChangeListener(this);

        mEnableVoiceTTSsmsRead = (CheckBoxPreference) prefSet.findPreference(KEY_VOICE_TTS_SMS_READ);
        mEnableVoiceTTSsmsRead.setChecked(mShareprefs.getBoolean(IntentReceiver.ENABLED_SMS_READ, false));
        mEnableVoiceTTSsmsRead.setOnPreferenceChangeListener(this);

        mEnableVoiceTTSchargeFull = (CheckBoxPreference) prefSet.findPreference(KEY_VOICE_TTS_CHARGE_FULL);
        mEnableVoiceTTSchargeFull.setChecked(mShareprefs.getBoolean(IntentReceiver.ENABLED_CHARGE_FULL, false));
        mEnableVoiceTTSchargeFull.setOnPreferenceChangeListener(this);

        mEnableVoiceTTSchargeOn = (CheckBoxPreference) prefSet.findPreference(KEY_VOICE_TTS_CHARGE_ON);
        mEnableVoiceTTSchargeOn.setChecked(mShareprefs.getBoolean(IntentReceiver.ENABLED_CHARGE_ON, false));
        mEnableVoiceTTSchargeOn.setOnPreferenceChangeListener(this);

        mEnableVoiceTTSchargeOff = (CheckBoxPreference) prefSet.findPreference(KEY_VOICE_TTS_CHARGE_OFF);
        mEnableVoiceTTSchargeOff.setChecked(mShareprefs.getBoolean(IntentReceiver.ENABLED_CHARGE_OFF, false));
        mEnableVoiceTTSchargeOff.setOnPreferenceChangeListener(this);

        mEnableVoiceTTSclock = (CheckBoxPreference) prefSet.findPreference(KEY_VOICE_TTS_CLOCK);
        mEnableVoiceTTSclock.setChecked(mShareprefs.getBoolean(IntentReceiver.ENABLED_CLOCK, false));
        mEnableVoiceTTSclock.setOnPreferenceChangeListener(this);

        mEnableVoiceTTSdate = (CheckBoxPreference) prefSet.findPreference(KEY_VOICE_TTS_DATE);
        mEnableVoiceTTSdate.setChecked(mShareprefs.getBoolean(IntentReceiver.ENABLED_DATE, false));
        mEnableVoiceTTSdate.setOnPreferenceChangeListener(this);

        mEnableVoiceTTSmusic = (CheckBoxPreference) prefSet.findPreference(KEY_VOICE_TTS_MUSIC);
        mEnableVoiceTTSmusic.setChecked(mShareprefs.getBoolean(IntentReceiver.ENABLED_MUSIC, false));
        mEnableVoiceTTSmusic.setOnPreferenceChangeListener(this);

        mEnableVoiceTTSnotif = (CheckBoxPreference) prefSet.findPreference(KEY_VOICE_TTS_NOTIF);
        mEnableVoiceTTSnotif.setChecked(mShareprefs.getBoolean(IntentReceiver.ENABLED_NOTIF, false));
        mEnableVoiceTTSnotif.setOnPreferenceChangeListener(this);

        mEnableVoiceTTSnotifRead = (CheckBoxPreference) prefSet.findPreference(KEY_VOICE_TTS_NOTIF_READ);
        mEnableVoiceTTSnotifRead.setChecked(mShareprefs.getBoolean(IntentReceiver.ENABLED_NOTIF_READ, false));
        mEnableVoiceTTSnotifRead.setOnPreferenceChangeListener(this);

        mIncludedAppsPref = (AppMultiSelectListPreference) prefSet.findPreference(KEY_VOICE_TTS_INCLUDED_APPS);
        Set<String> includedApps = getIncludedApps();
        if (includedApps != null) mIncludedAppsPref.setValues(includedApps);
        mIncludedAppsPref.setOnPreferenceChangeListener(this);

        mAnnoyingNotifications = (ListPreference) prefSet.findPreference(KEY_VOICE_TTS_ANNOY_NOTIF);
        mAnnoyingNotifications.setValue(Integer.toString(
                   mShareprefs.getInt(IntentReceiver.ANNOYING_NOTIFICATION, 0)));
        mAnnoyingNotifications.setOnPreferenceChangeListener(this);

        if (!mShareprefs.getBoolean(IntentReceiver.FIRST_BOOT_INUSE, false)) {
            Intent checkIntent = new Intent();
            checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
            startActivityForResult(checkIntent, 1);
        }

        checkForEngineReady();

        setHasOptionsMenu(true);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                mShareprefs.edit().putBoolean(IntentReceiver.ENGINE_READY, true).commit();
                checkForEngineReady();
                mShareprefs.edit().putBoolean(IntentReceiver.FIRST_BOOT_INUSE, true).commit();
            } else {
                // missing data, install it
                Intent installIntent = new Intent();
                installIntent.setAction(
                    TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
                startActivity(installIntent);
                mShareprefs.edit().putBoolean(IntentReceiver.ENGINE_READY, false).commit();
                checkForEngineReady();
            }
        }
    }

    private void checkForEngineReady() {
        isEngineReady = mShareprefs.getBoolean(IntentReceiver.ENGINE_READY, false);
        mEnableVoiceTTS.setEnabled(isEngineReady);
        mVoiceVolume.setEnabled(isEngineReady);
        mEnableVoiceTTScall.setEnabled(isEngineReady);
        mEnableVoiceTTSsms.setEnabled(isEngineReady);
        mEnableVoiceTTSsmsRead.setEnabled(isEngineReady);
        mEnableVoiceTTSchargeFull.setEnabled(isEngineReady);
        mEnableVoiceTTSchargeOn.setEnabled(isEngineReady);
        mEnableVoiceTTSchargeOff.setEnabled(isEngineReady);
        mEnableVoiceTTSclock.setEnabled(isEngineReady);
        mEnableVoiceTTSdate.setEnabled(isEngineReady);
        mEnableVoiceTTSmusic.setEnabled(isEngineReady);
        mEnableVoiceTTSnotif.setEnabled(isEngineReady);
        mEnableVoiceTTSnotifRead.setEnabled(isEngineReady);
        mIncludedAppsPref.setEnabled(isEngineReady);
        mAnnoyingNotifications.setEnabled(isEngineReady);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MENU_RESET, 0, R.string.reset)
                .setIcon(R.drawable.ic_settings_backup) // use the backup icon
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RESET:
                resetToDefault();
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    private void resetToDefault() {
        AlertDialog.Builder alertDialog = new AlertDialog.Builder(getActivity());
        alertDialog.setTitle(R.string.reset);
        alertDialog.setMessage(R.string.tts_settings_reset_message);
        alertDialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                mShareprefs.edit().putBoolean(IntentReceiver.ENABLED, false).commit();
                mEnableVoiceTTS.setChecked(false);
                mShareprefs.edit().putInt(IntentReceiver.VOICE_VOLUME, 8).commit();
                mVoiceVolume.setValue(80);
                mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_CALL, false).commit();
                mEnableVoiceTTScall.setChecked(false);
                mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_SMS, false).commit();
                mEnableVoiceTTSsms.setChecked(false);
                mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_SMS_READ, false).commit();
                mEnableVoiceTTSsmsRead.setChecked(false);
                mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_CHARGE_FULL, false).commit();
                mEnableVoiceTTSchargeFull.setChecked(false);
                mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_CHARGE_ON, false).commit();
                mEnableVoiceTTSchargeOn.setChecked(false);
                mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_CHARGE_OFF, false).commit();
                mEnableVoiceTTSchargeOff.setChecked(false);
                mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_CLOCK, false).commit();
                mEnableVoiceTTSclock.setChecked(false);
                mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_DATE, false).commit();
                mEnableVoiceTTSdate.setChecked(false);
                mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_MUSIC, false).commit();
                mEnableVoiceTTSmusic.setChecked(false);
                mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_NOTIF, false).commit();
                mEnableVoiceTTSnotif.setChecked(false);
                mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_NOTIF_READ, false).commit();
                mEnableVoiceTTSnotifRead.setChecked(false);
                mShareprefs.edit().putString(IntentReceiver.INCLUDE_NOTIFICATIONS, "").commit();
                mIncludedAppsPref.setClearValues();
                mShareprefs.edit().putInt(IntentReceiver.ANNOYING_NOTIFICATION, 0).commit();
                mAnnoyingNotifications.setValue("0");
            }
        });
        alertDialog.setNegativeButton(R.string.cancel, null);
        alertDialog.create().show();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mEnableVoiceTTS) {
            boolean value = (Boolean) newValue;
            mShareprefs.edit().putBoolean(IntentReceiver.ENABLED, value ? true : false).commit();
            if (value) {
                Intent intent = new Intent(IntentReceiver.ACTION_RUN_DRIVEMODE);
                getActivity().getApplicationContext().sendBroadcast(intent);
            }
            return true;
        } else if (preference == mVoiceVolume) {
            int value = ((Integer)newValue).intValue();
            int val = (value / MULTIPLIER_VOLUME);
            mShareprefs.edit().putInt(IntentReceiver.VOICE_VOLUME, val).commit();
            return true;
        } else if (preference == mEnableVoiceTTScall) {
            boolean value = (Boolean) newValue;
            mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_CALL, value ? true : false).commit();
            return true;
        } else if (preference == mEnableVoiceTTSsms) {
            boolean value = (Boolean) newValue;
            mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_SMS, value ? true : false).commit();
            return true;
        } else if (preference == mEnableVoiceTTSsmsRead) {
            boolean value = (Boolean) newValue;
            mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_SMS_READ, value ? true : false).commit();
            return true;
        } else if (preference == mEnableVoiceTTSchargeFull) {
            boolean value = (Boolean) newValue;
            mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_CHARGE_FULL, value ? true : false).commit();
            return true;
        } else if (preference == mEnableVoiceTTSchargeOn) {
            boolean value = (Boolean) newValue;
            mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_CHARGE_ON, value ? true : false).commit();
            return true;
        } else if (preference == mEnableVoiceTTSchargeOff) {
            boolean value = (Boolean) newValue;
            mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_CHARGE_OFF, value ? true : false).commit();
            return true;
        } else if (preference == mEnableVoiceTTSclock) {
            boolean value = (Boolean) newValue;
            mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_CLOCK, value ? true : false).commit();
            return true;
        } else if (preference == mEnableVoiceTTSdate) {
            boolean value = (Boolean) newValue;
            mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_DATE, value ? true : false).commit();
            return true;
        } else if (preference == mEnableVoiceTTSmusic) {
            boolean value = (Boolean) newValue;
            mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_MUSIC, value ? true : false).commit();
            return true;
        } else if (preference == mEnableVoiceTTSnotif) {
            boolean value = (Boolean) newValue;
            mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_NOTIF, value ? true : false).commit();
            return true;
        } else if (preference == mEnableVoiceTTSnotifRead) {
            boolean value = (Boolean) newValue;
            mShareprefs.edit().putBoolean(IntentReceiver.ENABLED_NOTIF_READ, value ? true : false).commit();
            return true;
        } else if (preference == mIncludedAppsPref) {
            storeIncludedApps((Set<String>) newValue);
            return true;
        } else if (preference == mAnnoyingNotifications) {
            int value = Integer.valueOf((String) newValue);
            mShareprefs.edit().putInt(IntentReceiver.ANNOYING_NOTIFICATION, value).commit();
            return true;
        }
        return false;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        // If we didn't handle it, let preferences handle it.
        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    private Set<String> getIncludedApps() {
        String included = mShareprefs.getString(IntentReceiver.INCLUDE_NOTIFICATIONS, "");
        if (TextUtils.isEmpty(included))
            return null;

        return new HashSet<String>(Arrays.asList(included.split("\\|")));
    }

    private void storeIncludedApps(Set<String> values) {
        StringBuilder builder = new StringBuilder();
        String delimiter = "";
        for (String value : values) {
            builder.append(delimiter);
            builder.append(value);
            delimiter = "|";
        }
        mShareprefs.edit().putString(IntentReceiver.INCLUDE_NOTIFICATIONS, builder.toString()).commit();
    }
}
