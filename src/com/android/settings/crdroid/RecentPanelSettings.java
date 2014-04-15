package com.android.settings.crdroid;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface; 
import android.content.Intent;
import android.net.Uri;
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
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;
import com.android.settings.Utils;
import net.margaritov.preference.colorpicker.ColorPickerPreference;

import com.android.settings.util.Helpers;

import java.util.Date;

public class RecentPanelSettings extends SettingsPreferenceFragment implements OnPreferenceChangeListener {

    private static final String TAG = "RecentsPanelSettings";

    private static final String CUSTOM_RECENT_MODE = "custom_recent_mode";
    private static final String RECENT_MENU_CLEAR_ALL = "recent_menu_clear_all";
    private static final String RECENT_MENU_CLEAR_ALL_LOCATION = "recent_menu_clear_all_location";
    private static final String RAM_BAR_MODE = "ram_bar_mode";
    private static final String RAM_BAR_COLOR_APP_MEM = "ram_bar_color_app_mem";
    private static final String RAM_BAR_COLOR_CACHE_MEM = "ram_bar_color_cache_mem";
    private static final String RAM_BAR_COLOR_TOTAL_MEM = "ram_bar_color_total_mem";
    // Slim recent
    private static final String RECENT_PANEL_LEFTY_MODE = "recent_panel_lefty_mode";
    private static final String RECENT_PANEL_SCALE = "recent_panel_scale";
    private static final String RECENT_PANEL_EXPANDED_MODE = "recent_panel_expanded_mode";

    private static final int MENU_RESET = Menu.FIRST;
    private static final int MENU_HELP = MENU_RESET + 1; 

    static final int DEFAULT_MEM_COLOR = 0xff8d8d8d;
    static final int DEFAULT_CACHE_COLOR = 0xff00aa00;
    static final int DEFAULT_ACTIVE_APPS_COLOR = 0xff33b5e5;

    private CheckBoxPreference mRecentsCustom;
    private CheckBoxPreference mRecentClearAll;
    private ListPreference mRecentClearAllPosition;
    private ListPreference mRamBarMode;
    private ColorPickerPreference mRamBarAppMemColor;
    private ColorPickerPreference mRamBarCacheMemColor;
    private ColorPickerPreference mRamBarTotalMemColor;
    // Slim recent
    private CheckBoxPreference mRecentPanelLeftyMode;
    private ListPreference mRecentPanelScale;
    private ListPreference mRecentPanelExpandedMode;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        int intColor;
        String hexColor;

        addPreferencesFromResource(R.xml.recents_panel_settings);

        PreferenceScreen prefSet = getPreferenceScreen();

        boolean enableRecentsCustom = Settings.System.getBoolean(getContentResolver(),
                                      Settings.System.CUSTOM_RECENT_TOGGLE, false);
        mRecentsCustom = (CheckBoxPreference) findPreference(CUSTOM_RECENT_MODE);
        mRecentsCustom.setChecked(enableRecentsCustom);
        mRecentsCustom.setOnPreferenceChangeListener(this);

        mRecentClearAll = (CheckBoxPreference) prefSet.findPreference(RECENT_MENU_CLEAR_ALL);
        mRecentClearAll.setChecked(Settings.System.getInt(getActivity().getContentResolver(),
            Settings.System.SHOW_CLEAR_RECENTS_BUTTON, 1) == 1);
        mRecentClearAll.setOnPreferenceChangeListener(this);
        mRecentClearAllPosition = (ListPreference) prefSet.findPreference(RECENT_MENU_CLEAR_ALL_LOCATION);
        String recentClearAllPosition = Settings.System.getString(getActivity().getContentResolver(), 
            Settings.System.CLEAR_RECENTS_BUTTON_LOCATION);
        if (recentClearAllPosition != null) {
             mRecentClearAllPosition.setValue(recentClearAllPosition);
        }
        mRecentClearAllPosition.setOnPreferenceChangeListener(this);    

        mRamBarMode = (ListPreference) prefSet.findPreference(RAM_BAR_MODE);
        int ramBarMode = Settings.System.getInt(getActivity().getApplicationContext().getContentResolver(),
                Settings.System.RECENTS_RAM_BAR_MODE, 0);
        mRamBarMode.setValue(String.valueOf(ramBarMode));
        mRamBarMode.setSummary(mRamBarMode.getEntry());
        mRamBarMode.setOnPreferenceChangeListener(this);

        mRamBarAppMemColor = (ColorPickerPreference) findPreference(RAM_BAR_COLOR_APP_MEM);
        mRamBarAppMemColor.setOnPreferenceChangeListener(this);
        intColor = Settings.System.getInt(getActivity().getContentResolver(),
                    Settings.System.RECENTS_RAM_BAR_ACTIVE_APPS_COLOR, DEFAULT_ACTIVE_APPS_COLOR);
        hexColor = String.format("#%08x", (0xffffffff & intColor));
        mRamBarAppMemColor.setSummary(hexColor);
        mRamBarAppMemColor.setNewPreviewColor(intColor);

        mRamBarCacheMemColor = (ColorPickerPreference) findPreference(RAM_BAR_COLOR_CACHE_MEM);
        mRamBarCacheMemColor.setOnPreferenceChangeListener(this);
        intColor = Settings.System.getInt(getActivity().getContentResolver(),
                    Settings.System.RECENTS_RAM_BAR_CACHE_COLOR, DEFAULT_CACHE_COLOR);
        hexColor = String.format("#%08x", (0xffffffff & intColor));
        mRamBarCacheMemColor.setSummary(hexColor);
        mRamBarCacheMemColor.setNewPreviewColor(intColor);

        mRamBarTotalMemColor = (ColorPickerPreference) findPreference(RAM_BAR_COLOR_TOTAL_MEM);
        mRamBarTotalMemColor.setOnPreferenceChangeListener(this);
        intColor = Settings.System.getInt(getActivity().getContentResolver(),
                    Settings.System.RECENTS_RAM_BAR_MEM_COLOR, DEFAULT_MEM_COLOR);
        hexColor = String.format("#%08x", (0xffffffff & intColor));
        mRamBarTotalMemColor.setSummary(hexColor);
        mRamBarTotalMemColor.setNewPreviewColor(intColor);

        // Slim recent
        mRecentPanelLeftyMode = (CheckBoxPreference) findPreference(RECENT_PANEL_LEFTY_MODE);
        mRecentPanelLeftyMode.setOnPreferenceChangeListener(this);
        mRecentPanelScale = (ListPreference) findPreference(RECENT_PANEL_SCALE);
        mRecentPanelScale.setOnPreferenceChangeListener(this);

        mRecentPanelExpandedMode = (ListPreference) findPreference(RECENT_PANEL_EXPANDED_MODE);
        mRecentPanelExpandedMode.setOnPreferenceChangeListener(this);
        final int recentExpandedMode = Settings.System.getInt(getContentResolver(),
                Settings.System.RECENT_PANEL_EXPANDED_MODE, 0);
        mRecentPanelExpandedMode.setValue(recentExpandedMode + "");

        updateRecentsOptions();
        setHasOptionsMenu(true);

    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MENU_RESET, 0, R.string.ram_bar_button_reset)
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
        alertDialog.setTitle(R.string.ram_bar_reset);
        alertDialog.setMessage(R.string.ram_bar_reset_message);
        alertDialog.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                ramBarColorReset();
            }
        });
        alertDialog.setNegativeButton(R.string.cancel, null);
        alertDialog.create().show();
    } 

    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mRecentsCustom) { // Enable||disbale Slim Recent
            Settings.System.putBoolean(getActivity().getContentResolver(),
                    Settings.System.CUSTOM_RECENT_TOGGLE,
                    ((Boolean) newValue) ? true : false);
            updateRecentsOptions();
            Helpers.restartSystemUI();
            return true;
        } else if (preference == mRecentClearAll) {
            boolean value = (Boolean) newValue;
            Settings.System.putInt(getActivity().getContentResolver(), 
                 Settings.System.SHOW_CLEAR_RECENTS_BUTTON, value ? 1 : 0);
            return true;
        } else if (preference == mRecentClearAllPosition) {
            String value = (String) newValue;
            Settings.System.putString(getActivity().getContentResolver(), 
                 Settings.System.CLEAR_RECENTS_BUTTON_LOCATION, value);
            return true;
        } else if (preference == mRamBarMode) {
            int ramBarMode = Integer.valueOf((String) newValue);
            int index = mRamBarMode.findIndexOfValue((String) newValue);
            Settings.System.putInt(getActivity().getApplicationContext().getContentResolver(),
                    Settings.System.RECENTS_RAM_BAR_MODE, ramBarMode);
            mRamBarMode.setSummary(mRamBarMode.getEntries()[index]);
            updateRecentsOptions();
            return true;
        } else if (preference == mRamBarAppMemColor) {
            String hex = ColorPickerPreference.convertToARGB(Integer
                    .valueOf(String.valueOf(newValue)));
            preference.setSummary(hex);

            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.RECENTS_RAM_BAR_ACTIVE_APPS_COLOR, intHex);
            return true;
        } else if (preference == mRamBarCacheMemColor) {
            String hex = ColorPickerPreference.convertToARGB(Integer
                    .valueOf(String.valueOf(newValue)));
            preference.setSummary(hex);

            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.RECENTS_RAM_BAR_CACHE_COLOR, intHex);
            return true;
        } else if (preference == mRamBarTotalMemColor) {
            String hex = ColorPickerPreference.convertToARGB(Integer
                    .valueOf(String.valueOf(newValue)));
            preference.setSummary(hex);

            int intHex = ColorPickerPreference.convertToColorInt(hex);
            Settings.System.putInt(getActivity().getContentResolver(),
                    Settings.System.RECENTS_RAM_BAR_MEM_COLOR, intHex);
            return true;
        } else if (preference == mRecentPanelScale) {
            int value = Integer.parseInt((String) newValue);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.RECENT_PANEL_SCALE_FACTOR, value);
            return true;
        } else if (preference == mRecentPanelLeftyMode) {
            Settings.System.putInt(getContentResolver(),
                    Settings.System.RECENT_PANEL_GRAVITY,
                    ((Boolean) newValue) ? Gravity.LEFT : Gravity.RIGHT);
            return true;
        } else if (preference == mRecentPanelExpandedMode) {
            int value = Integer.parseInt((String) newValue);
            Settings.System.putInt(getContentResolver(),
                    Settings.System.RECENT_PANEL_EXPANDED_MODE, value);
            return true;
        }
        return false;
    }

    private void ramBarColorReset() {
        Settings.System.putInt(getActivity().getContentResolver(),
                Settings.System.RECENTS_RAM_BAR_ACTIVE_APPS_COLOR, DEFAULT_ACTIVE_APPS_COLOR);
        Settings.System.putInt(getActivity().getContentResolver(),
                Settings.System.RECENTS_RAM_BAR_CACHE_COLOR, DEFAULT_CACHE_COLOR);
        Settings.System.putInt(getActivity().getContentResolver(),
                Settings.System.RECENTS_RAM_BAR_MEM_COLOR, DEFAULT_MEM_COLOR);

        mRamBarAppMemColor.setNewPreviewColor(DEFAULT_ACTIVE_APPS_COLOR);
        mRamBarCacheMemColor.setNewPreviewColor(DEFAULT_CACHE_COLOR);
        mRamBarTotalMemColor.setNewPreviewColor(DEFAULT_MEM_COLOR);
        String hexColor = String.format("#%08x", (0xffffffff & DEFAULT_ACTIVE_APPS_COLOR));
        mRamBarAppMemColor.setSummary(hexColor);
        hexColor = String.format("#%08x", (0xffffffff & DEFAULT_ACTIVE_APPS_COLOR));
        mRamBarCacheMemColor.setSummary(hexColor);
        hexColor = String.format("#%08x", (0xffffffff & DEFAULT_MEM_COLOR));
        mRamBarTotalMemColor.setSummary(hexColor);
    }


    private void updateRecentsOptions() {
        int ramBarMode = Settings.System.getInt(getActivity().getContentResolver(),
               Settings.System.RECENTS_RAM_BAR_MODE, 0);
        boolean recentsStyle = Settings.System.getBoolean(getActivity().getContentResolver(),
               Settings.System.CUSTOM_RECENT_TOGGLE, false);

        // Slim recent
        final boolean recentLeftyMode = Settings.System.getInt(getContentResolver(),
                Settings.System.RECENT_PANEL_GRAVITY, Gravity.RIGHT) == Gravity.LEFT;
        mRecentPanelLeftyMode.setChecked(recentLeftyMode);
        final int recentScale = Settings.System.getInt(getContentResolver(),
                Settings.System.RECENT_PANEL_SCALE_FACTOR, 100);
        mRecentPanelScale.setValue(recentScale + "");

        if (recentsStyle) {
            mRecentClearAll.setEnabled(false);
            mRamBarMode.setEnabled(false);
            mRamBarAppMemColor.setEnabled(false);
            mRamBarCacheMemColor.setEnabled(false);
            mRamBarTotalMemColor.setEnabled(false);
        } else {
            mRecentClearAll.setEnabled(true);
            mRamBarMode.setEnabled(true);
            if (ramBarMode == 0) {
                mRamBarAppMemColor.setEnabled(false);
                mRamBarCacheMemColor.setEnabled(false);
                mRamBarTotalMemColor.setEnabled(false);
            } else if (ramBarMode == 1) {
                mRamBarAppMemColor.setEnabled(true);
                mRamBarCacheMemColor.setEnabled(false);
                mRamBarTotalMemColor.setEnabled(false);
            } else if (ramBarMode == 2) {
                mRamBarAppMemColor.setEnabled(true);
                mRamBarCacheMemColor.setEnabled(true);
                mRamBarTotalMemColor.setEnabled(false);
            } else {
                mRamBarAppMemColor.setEnabled(true);
                mRamBarCacheMemColor.setEnabled(true);
                mRamBarTotalMemColor.setEnabled(true);
            }
        }
    }
}
