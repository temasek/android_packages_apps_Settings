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

package com.android.settings.tts;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.preference.PreferenceManager;
import android.util.Log;

public class IntentReceiver extends BroadcastReceiver {

       private static final String TAG = "DriveModeBootReceiver";

       public static final String ACTION_PHONE_STATE = "android.intent.action.PHONE_STATE";
       public static final String ACTION_SMS_RECEIVED = "android.provider.Telephony.SMS_RECEIVED";
       public static final String ACTION_RUN_DRIVEMODE = "com.android.settings.tts.action.RUN_DRIVEMODE";
       public static final String ACTION_RUN_BOOTCOMPLETE = "android.intent.action.BOOT_COMPLETED";

       public static final String FIRST_BOOT_INUSE = "voice_tts_firstboot";
       public static final String ENABLED = "voice_tts_enabled";
       public static final String VOICE_VOLUME = "voice_tts_volume_sound";
       public static final String ENGINE_READY = "engine_tts_ready";
       public static final String ENABLED_CALL = "voice_tts_call_enabled";
       public static final String ENABLED_SMS = "voice_tts_sms_enabled";
       public static final String ENABLED_SMS_READ = "voice_tts_sms_read_enabled";
       public static final String ENABLED_CHARGE_FULL = "voice_tts_chargefull_enabled";
       public static final String ENABLED_CHARGE_ON = "voice_tts_chargeon_enabled";
       public static final String ENABLED_CHARGE_OFF = "voice_tts_chargeoff_enabled";
       public static final String ENABLED_CLOCK = "voice_tts_clock_enabled";
       public static final String ENABLED_DATE = "voice_tts_date_enabled";
       public static final String INCLUDE_NOTIFICATIONS = "voice_tts_apps_include";
       public static final String ANNOYING_NOTIFICATION = "voice_tts_annoying_notif";
       public static final String ENABLED_NOTIF = "voice_tts_notif_enabled";
       public static final String ENABLED_NOTIF_READ = "voice_tts_notif_read_enabled";
       public static final String ENABLED_MUSIC = "voice_tts_music_enabled";

       @Override
       public void onReceive(Context context, Intent intent) {
           if (!PreferenceManager.getDefaultSharedPreferences(context)
                      .getBoolean(ENGINE_READY, false)) {
               return;
           }
           if (!PreferenceManager.getDefaultSharedPreferences(context)
                      .getBoolean(ENABLED, false)) {
               return;
           }

           Log.i(TAG, "Started");
           Intent serv = new Intent(context, NotifyService.class);
           serv.setAction(intent.getAction());
           serv.putExtras(intent);
           context.startService(serv);
       }
}
