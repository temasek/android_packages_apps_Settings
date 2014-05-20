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

import static android.media.AudioManager.STREAM_MUSIC;
import static android.media.AudioManager.STREAM_NOTIFICATION;
import static android.media.AudioManager.STREAM_RING;
import static android.media.AudioManager.STREAM_SYSTEM;

import android.app.INotificationManager;
import android.app.Notification;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaMetadataRetriever;
import android.media.RemoteControlClient;
import android.media.RemoteController;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.provider.Contacts;
import android.provider.ContactsContract.PhoneLookup;
import android.service.notification.INotificationListener;
import android.service.notification.StatusBarNotification;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.speech.tts.TextToSpeech.OnUtteranceCompletedListener;
import android.telephony.SmsMessage;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import libcore.icu.ICU;

import com.android.settings.R;

public class NotifyService extends Service implements OnInitListener, Runnable {

       private static final String TAG = "VoiceService";

       private static final String ACTION_SPEAK_NOTIFICATION
                      = "com.android.settings.tts.action.SPEAK_NOTIFICATION";

       private static final String ACTION_SPEAK_MUSIC
                      = "com.android.settings.tts.action.SPEAK_MUSIC";

       private int mServiceStartId = -1;

       private final Date mCurrentTime = new Date();

       private Calendar mCalendar;
       private SimpleDateFormat mWeekdayFormat;
       private SimpleDateFormat mDateFormat;
       private String mLastText;

       private static final String[] PROJECTION =
            new String[] { PhoneLookup.DISPLAY_NAME };
       private static final String STREAM_SYSTEM_STR =
                   String.valueOf(AudioManager.STREAM_SYSTEM);
       private TextToSpeech mTts;
       private Intent mIntent;
       private boolean mIsReady;
       private int mSysVol;
       private AudioManager mAudioManager;
       private static final Object sLock = new Object();
       private Context mContext;
       private SharedPreferences mShareprefs;

       private BroadcastReceiver mReceiver = null;

       private boolean mActive = false;
       private boolean mMusicActive = false;
       private boolean mClientIdLost = true;
       private Metadata mMetadata = new Metadata();
       private String mTrackMusic;

       private RemoteController mRemoteController;

       private HashMap<String, Long> mAnnoyingNotifications = new HashMap<String, Long>();

       private INotificationManager mNM;
       private INotificationListenerWrapper mNotificationListener;
       private Set<String> mIncludedApps = new HashSet<String>();
       private String mNotificationText;
       private String mNotificationApp;

       /**
        * Simple class that listens to changes in notifications
        */
       private class INotificationListenerWrapper extends INotificationListener.Stub {
           @Override
           public void onNotificationPosted(final StatusBarNotification sbn) {
               if (isValidNotification(sbn)) {
                   showNotification(sbn);
               }
           }
           @Override
           public void onNotificationRemoved(final StatusBarNotification sbn) {
           }
       }

       private OnAudioFocusChangeListener mAudioFocusListener = new OnAudioFocusChangeListener() {
            public void onAudioFocusChange(int focusChange) {
                // hack to get Volume stream focus
            }
       };

       @Override
       public IBinder onBind(Intent intent) {
           return null;
       }

       @Override
       public void onCreate() {
           super.onCreate();
           mContext = this;
           if (mTts == null) {
               mTts = new TextToSpeech(this, this);
           }

           mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
           mRemoteController = new RemoteController(mContext, mRCClientUpdateListener);
           mAudioManager.registerRemoteController(mRemoteController);
           mNM = INotificationManager.Stub.asInterface(
                    ServiceManager.getService(Context.NOTIFICATION_SERVICE));
           mNotificationListener = new INotificationListenerWrapper();

           mShareprefs = PreferenceManager.getDefaultSharedPreferences(mContext);

           IntentFilter filter = new IntentFilter();
           filter.addAction(Intent.ACTION_BATTERY_CHANGED);
           filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
           filter.addAction(Intent.ACTION_LOCALE_CHANGED);
           filter.addAction(Intent.ACTION_SCREEN_ON);
           filter.addAction(Intent.ACTION_SCREEN_OFF);
           filter.addAction(Intent.ACTION_TIME_CHANGED);
           filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
           filter.addAction(ACTION_SPEAK_NOTIFICATION);
           filter.addAction(ACTION_SPEAK_MUSIC);
           filter.addAction(IntentReceiver.ACTION_RUN_DRIVEMODE);
           mReceiver = new IntentReceiver();
           Log.i(TAG, "register Receiver");
           registerReceiver(mReceiver, filter);
           registerNotificationListener();

           mCalendar = Calendar.getInstance();
           mCalendar.setTimeInMillis(System.currentTimeMillis());
       }

       @Override
       public void onStart(Intent intent, int startId) {
           super.onStart(intent, startId);
           mServiceStartId = startId;
           mIntent = intent;
           new Thread(this).start();

           createIncludedAppsSet(mShareprefs.getString(IntentReceiver.INCLUDE_NOTIFICATIONS, ""));
       }

       private String getSmallTime() {
           mCalendar.setTimeInMillis(System.currentTimeMillis());
           int mMinutes = mCalendar.get(mCalendar.MINUTE);
           int mHour = mCalendar.get(mCalendar.HOUR);
           String mNextH, mTimeH, mTimeString;
           String mOclock = mContext.getString(R.string.time_oclock);
           String mFivePast = mContext.getString(R.string.time_five_past);
           String mTenPast = mContext.getString(R.string.time_ten_past);
           String mQuarterPast = mContext.getString(R.string.time_quarter_past);
           String mTwentyPast = mContext.getString(R.string.time_twenty_past);
           String mTwentyFivePast = mContext.getString(R.string.time_twenty_five_past);
           String mHalfPast = mContext.getString(R.string.time_half_past);
           String mTwentyFiveTo = mContext.getString(R.string.time_twenty_five_to);
           String mTwentyTo = mContext.getString(R.string.time_twenty_to);
           String mQuarterTo = mContext.getString(R.string.time_quarter_to);
           String mTenTo = mContext.getString(R.string.time_ten_to);
           String mFiveTo = mContext.getString(R.string.time_five_to);
           String mOne = mContext.getString(R.string.time_one);
           String mTwo = mContext.getString(R.string.time_two);
           String mThree = mContext.getString(R.string.time_three);
           String mFour = mContext.getString(R.string.time_four);
           String mFive = mContext.getString(R.string.time_five);
           String mSix = mContext.getString(R.string.time_six);
           String mSeven = mContext.getString(R.string.time_seven);
           String mEight = mContext.getString(R.string.time_eight);
           String mNine = mContext.getString(R.string.time_nine);
           String mTen = mContext.getString(R.string.time_ten);
           String mEleven = mContext.getString(R.string.time_eleven);
           String mTwelve = mContext.getString(R.string.time_twelve);

           //hours
           if (mHour == 1) { mNextH = mTwo; mTimeH = mOne; }
           else if(mHour == 2) { mNextH = mThree; mTimeH = mTwo; }
           else if(mHour == 3) { mNextH = mFour; mTimeH = mThree; }
           else if(mHour == 4) { mNextH = mFive; mTimeH = mFour; }
           else if(mHour == 5) { mNextH = mSix; mTimeH = mFive; }
           else if(mHour == 6) { mNextH = mSeven; mTimeH = mSix; }
           else if(mHour == 7) { mNextH = mEight; mTimeH = mSeven; }
           else if(mHour == 8) { mNextH = mNine; mTimeH = mEight; }
           else if(mHour == 9) { mNextH = mTen; mTimeH = mNine; }
           else if(mHour == 10) { mNextH = mEleven; mTimeH = mTen; }
           else if(mHour == 11) { mNextH = mTwelve; mTimeH = mEleven; }
           else if(mHour == 12 || mHour == 0) { mNextH = mOne; mTimeH = mTwelve; }
           else { mNextH = mTimeH = mContext.getString(R.string.unknown_tts); }// { mNextH = mOne; mTimeH = mTwelve; }

           //minutes
           if ( 0  <= mMinutes && mMinutes <= 4  ) mTimeString = mTimeH + " " + mOclock;
           else if ( 5  <= mMinutes && mMinutes <= 9  ) mTimeString = mFivePast + " " + mTimeH;
           else if ( 10 <= mMinutes && mMinutes <= 14 ) mTimeString = mTenPast + " " + mTimeH;
           else if ( 15 <= mMinutes && mMinutes <= 19 ) mTimeString = mQuarterPast + " " + mTimeH;
           else if ( 20 <= mMinutes && mMinutes <= 24 ) mTimeString = mTimeH + " " + mTwentyPast;
           else if ( 25 <= mMinutes && mMinutes <= 29 ) mTimeString = mTwentyFivePast + " " + mTimeH;
           else if ( 30 <= mMinutes && mMinutes <= 34 ) mTimeString = mHalfPast + " " + mTimeH;
           else if ( 35 <= mMinutes && mMinutes <= 39 ) mTimeString = mTwentyFiveTo + " " + mNextH;
           else if ( 40 <= mMinutes && mMinutes <= 43 ) mTimeString = mTwentyTo + " " + mNextH;
           else if ( 44 <= mMinutes && mMinutes <= 47 ) mTimeString = mQuarterTo + " " + mNextH;
           else if ( 48 <= mMinutes && mMinutes <= 51 ) mTimeString = mTenTo + " " + mNextH;
           else if ( 52 <= mMinutes && mMinutes <= 55 ) mTimeString = mFiveTo + " " + mNextH;
           else if ( 56 <= mMinutes && mMinutes <= 60 ) mTimeString = mNextH + " " + mOclock;
           else { mTimeString = mContext.getString(R.string.unknown_tts); }

           return mContext.getString(R.string.voice_tts_time) + " " + mTimeString;
       }

        private String getDateString() {
            if (mDateFormat == null) {
                final String weekdayFormat = mContext.getString(R.string.tts_weekday_pattern);
                final String dateFormat = mContext.getString(R.string.tts_date_pattern);
                final Locale l = Locale.getDefault();
                String weekdayFmt = ICU.getBestDateTimePattern(weekdayFormat, l.toString());
                String dateFmt = ICU.getBestDateTimePattern(dateFormat, l.toString());

                mDateFormat = new SimpleDateFormat(dateFmt, l);
                mWeekdayFormat = new SimpleDateFormat(weekdayFmt, l);
            }

            mCurrentTime.setTime(System.currentTimeMillis());
            final String text = mContext.getString(R.string.voice_tts_date)
                 + " " + mWeekdayFormat.format(mCurrentTime)
                 + " " + mDateFormat.format(mCurrentTime);
            if (!text.equals(mLastText)) {
                mLastText = text;
                return text;
            }
            return mLastText;
        }

        @Override
        public void onInit(int status) {
            mIsReady = true;
            mTts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                     @Override
                     public void onStart(String utteranceId) {}

                     @Override
                     public void onError(String utteranceId) {}

                     @Override
                     public void onDone(String utteranceId) {
                         resetVolume(utteranceId);
                     }
            });
            synchronized (sLock) {
                 sLock.notify();
            }
        }

        private void resetVolume(String utteranceId) {
            synchronized (sLock) {
                 if (utteranceId.equals("call")) {
                     mAudioManager.setStreamMute(STREAM_RING, false);
                     mAudioManager.setStreamVolume(STREAM_SYSTEM, mSysVol, 0);
                 } else if (utteranceId.equals("sms")) {
                     if (mMusicActive) {
                         mAudioManager.setStreamMute(STREAM_MUSIC, false);
                     } else {
                         mAudioManager.setStreamMute(STREAM_NOTIFICATION, false);
                     }
                     mAudioManager.setStreamVolume(STREAM_SYSTEM, mSysVol, 0);
                 } else if (utteranceId.equals("system")) {
                     if (mMusicActive) {
                         mAudioManager.setStreamMute(STREAM_MUSIC, false);
                     } else {
                         mAudioManager.setStreamMute(STREAM_NOTIFICATION, false);
                     }
                     mAudioManager.setStreamVolume(STREAM_SYSTEM, mSysVol, 0);
                 } else if (utteranceId.equals("music")) {
                     mAudioManager.setStreamMute(STREAM_MUSIC, false);
                     mAudioManager.setStreamVolume(STREAM_SYSTEM, mSysVol, 0);
                 }
                 mAudioManager.abandonAudioFocus(mAudioFocusListener);
            }
        }

        @Override
        public void run() {
            while (!mIsReady) {
                   synchronized (sLock) {
                        try {
                             sLock.wait();
                        } catch (InterruptedException e) { }
                   }
            }

            final String action = mIntent.getAction();
            if (action == null ) {
                return;
            }

            if (action.equals(IntentReceiver.ACTION_RUN_BOOTCOMPLETE)) {
                Log.i(TAG, "Drive Mode Boot Complete");
                return;
            }

            if (mAudioManager.isSilentMode()) {
                Log.i(TAG, "Not speaking - Silent Mode");
                return;
            }

            mMusicActive = mAudioManager.isMusicActive();

            if (mAudioManager.isStreamMute(STREAM_RING)) {
                mAudioManager.setStreamMute(STREAM_RING, false);
            } else if (mAudioManager.isStreamMute(STREAM_NOTIFICATION)) {
                mAudioManager.setStreamMute(STREAM_NOTIFICATION, false);
            } else if (mAudioManager.isStreamMute(STREAM_MUSIC)) {
                mAudioManager.setStreamMute(STREAM_MUSIC, false);
            }

            if (mMusicActive) {
                mAudioManager.requestAudioFocus(mAudioFocusListener, STREAM_SYSTEM,
                     AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
            } else {
                mAudioManager.requestAudioFocus(mAudioFocusListener, STREAM_SYSTEM,
                     AudioManager.AUDIOFOCUS_GAIN);
            }

            final int voiceVolume = mShareprefs.getInt(IntentReceiver.VOICE_VOLUME, 8);

            if (action.equals(IntentReceiver.ACTION_PHONE_STATE)) {
                if (mTts.isSpeaking() || !mShareprefs.getBoolean(IntentReceiver.ENABLED_CALL, false)) return;
                Log.i(TAG, "Speak Phone");
                TelephonyManager tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
                switch (tm.getCallState()) {
                        case TelephonyManager.CALL_STATE_RINGING:
                             final AudioManager am = mAudioManager;
                             final int mode = am.getRingerMode();
                             if (mode == AudioManager.RINGER_MODE_SILENT ||
                                 mode == AudioManager.RINGER_MODE_VIBRATE) {
                                 Log.i(TAG, "Not speaking - volume is 0");
                                 return;
                             }

                             final String name = findContactFromNumber(
                                        mIntent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER));

                             synchronized (sLock) {
                                  am.setStreamMute(STREAM_RING, true);
                                  mSysVol = am.getStreamVolume(STREAM_SYSTEM);
                                  am.setStreamVolume(STREAM_SYSTEM,
                                           voiceVolume, 0);
                                  HashMap<String, String> ops = new HashMap<String, String>();
                                  ops.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "call");
                                  ops.put(TextToSpeech.Engine.KEY_PARAM_STREAM, STREAM_SYSTEM_STR);
                                  mTts.speak(mContext.getString(R.string.voice_tts_new_call) + name, TextToSpeech.QUEUE_FLUSH, ops);
                             }
                             break;
                 }
             } else if (action.equals(IntentReceiver.ACTION_SMS_RECEIVED)) {
                 if (mTts.isSpeaking() || !mShareprefs.getBoolean(IntentReceiver.ENABLED_SMS, false)) return;
                 Log.i(TAG, "Speak Sms");
                 final AudioManager am = mAudioManager;
                 if (am.getStreamVolume(STREAM_NOTIFICATION) == 0) {
                     Log.i(TAG, "Not speaking - volume is 0");
                     return;
                 }

                 Object[] pdusObj = (Object[]) mIntent.getExtras().get("pdus");
                 SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdusObj[0]);
                 final String from = msg.getOriginatingAddress();
                 final String message = msg.getMessageBody();
                 final boolean readSms = mShareprefs.getBoolean(IntentReceiver.ENABLED_SMS_READ, false);
                 final boolean isMusicActive = mMusicActive;
                 synchronized (sLock) {
                      if (isMusicActive) {
                          am.setStreamMute(STREAM_MUSIC, true);
                      } else {
                          am.setStreamMute(STREAM_NOTIFICATION, true);
                      }
                      HashMap<String, String> ops = new HashMap<String, String>();
                      mSysVol = am.getStreamVolume(STREAM_SYSTEM);
                      am.setStreamVolume(STREAM_SYSTEM,
                               voiceVolume, 0);
                      ops.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "sms");
                      ops.put(TextToSpeech.Engine.KEY_PARAM_STREAM, STREAM_SYSTEM_STR);
                      if (readSms) {
                          mTts.speak(mContext.getString(R.string.voice_tts_new_sms) + findContactFromNumber(from) + message,
                                 TextToSpeech.QUEUE_FLUSH, ops);
                      } else {
                          mTts.speak(mContext.getString(R.string.voice_tts_new_sms) + findContactFromNumber(from),
                                 TextToSpeech.QUEUE_FLUSH, ops);
                      }
                 }
             } else if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                 if (mTts.isSpeaking() || !mShareprefs.getBoolean(IntentReceiver.ENABLED_CHARGE_FULL, false)) return;
                 Log.i(TAG, "Speak Battery");
                 final int status = mIntent.getIntExtra(BatteryManager.EXTRA_STATUS,
                          BatteryManager.BATTERY_STATUS_UNKNOWN);
                 if (status != BatteryManager.BATTERY_STATUS_FULL) return;
                 final AudioManager am = mAudioManager;
                 if (am.getStreamVolume(STREAM_NOTIFICATION) == 0) {
                     Log.i(TAG, "Not speaking - volume is 0");
                     return;
                 }

                 final boolean isMusicActive = mMusicActive;
                 synchronized (sLock) {
                      if (isMusicActive) {
                          am.setStreamMute(STREAM_MUSIC, true);
                      } else {
                          am.setStreamMute(STREAM_NOTIFICATION, true);
                      }
                      HashMap<String, String> ops = new HashMap<String, String>();
                      mSysVol = am.getStreamVolume(STREAM_SYSTEM);
                      am.setStreamVolume(STREAM_SYSTEM,
                               voiceVolume, 0);
                      ops.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "system");
                      ops.put(TextToSpeech.Engine.KEY_PARAM_STREAM, STREAM_SYSTEM_STR);
                      mTts.speak(mContext.getString(R.string.voice_tts_charge_full),
                                 TextToSpeech.QUEUE_FLUSH, ops);
                 }
             } else if (action.equals(Intent.ACTION_POWER_CONNECTED)) {
                 if (mTts.isSpeaking() || !mShareprefs.getBoolean(IntentReceiver.ENABLED_CHARGE_ON, false)) return;
                 Log.i(TAG, "Speak Charger connected");
                 final AudioManager am = mAudioManager;
                 if (am.getStreamVolume(STREAM_NOTIFICATION) == 0) {
                     Log.i(TAG, "Not speaking - volume is 0");
                     return;
                 }

                 final boolean isMusicActive = mMusicActive;
                 synchronized (sLock) {
                      if (isMusicActive) {
                          am.setStreamMute(STREAM_MUSIC, true);
                      } else {
                          am.setStreamMute(STREAM_NOTIFICATION, true);
                      }
                      HashMap<String, String> ops = new HashMap<String, String>();
                      mSysVol = am.getStreamVolume(STREAM_SYSTEM);
                      am.setStreamVolume(STREAM_SYSTEM,
                               voiceVolume, 0);
                      ops.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "system");
                      ops.put(TextToSpeech.Engine.KEY_PARAM_STREAM, STREAM_SYSTEM_STR);
                      mTts.speak(mContext.getString(R.string.voice_tts_charge_on),
                                 TextToSpeech.QUEUE_FLUSH, ops);
                 }
             } else if (action.equals(Intent.ACTION_POWER_DISCONNECTED)) {
                 if (mTts.isSpeaking() || !mShareprefs.getBoolean(IntentReceiver.ENABLED_CHARGE_OFF, false)) return;
                 Log.i(TAG, "Speak Charger disconnected");
                 final AudioManager am = mAudioManager;
                 if (am.getStreamVolume(STREAM_NOTIFICATION) == 0) {
                     Log.i(TAG, "Not speaking - volume is 0");
                     return;
                 }

                 final boolean isMusicActive = mMusicActive;
                 synchronized (sLock) {
                      if (isMusicActive) {
                          am.setStreamMute(STREAM_MUSIC, true);
                      } else {
                          am.setStreamMute(STREAM_NOTIFICATION, true);
                      }
                      HashMap<String, String> ops = new HashMap<String, String>();
                      mSysVol = am.getStreamVolume(STREAM_SYSTEM);
                      am.setStreamVolume(STREAM_SYSTEM,
                               voiceVolume, 0);
                      ops.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "system");
                      ops.put(TextToSpeech.Engine.KEY_PARAM_STREAM, STREAM_SYSTEM_STR);
                      mTts.speak(mContext.getString(R.string.voice_tts_charge_off),
                                 TextToSpeech.QUEUE_FLUSH, ops);
                 }
             } else if (action.equals(Intent.ACTION_SCREEN_ON)) {
                 if (mTts.isSpeaking()) return;
                 final boolean speakClock = mShareprefs.getBoolean(IntentReceiver.ENABLED_CLOCK, false);
                 final boolean speakDate = mShareprefs.getBoolean(IntentReceiver.ENABLED_DATE, false);
                 if (!speakClock && !speakDate) return;

                 Log.i(TAG, "Speak Clock or Date screen on");
                 final AudioManager am = mAudioManager;
                 if (am.getStreamVolume(STREAM_NOTIFICATION) == 0) {
                     Log.i(TAG, "Not speaking - volume is 0");
                     return;
                 }

                 final boolean isMusicActive = mMusicActive;
                 synchronized (sLock) {
                      if (isMusicActive) {
                          am.setStreamMute(STREAM_MUSIC, true);
                      } else {
                          am.setStreamMute(STREAM_NOTIFICATION, true);
                      }
                      HashMap<String, String> ops = new HashMap<String, String>();
                      mSysVol = am.getStreamVolume(STREAM_SYSTEM);
                      am.setStreamVolume(STREAM_SYSTEM,
                               voiceVolume, 0);
                      ops.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "system");
                      ops.put(TextToSpeech.Engine.KEY_PARAM_STREAM, STREAM_SYSTEM_STR);
                      if (speakClock && !speakDate) {
                          mTts.speak(getSmallTime(), TextToSpeech.QUEUE_FLUSH, ops);
                      } else if (!speakClock && speakDate) {
                          mTts.speak(getDateString(), TextToSpeech.QUEUE_FLUSH, ops);
                      } else if (speakClock && speakDate) {
                          mTts.speak(getSmallTime() + getDateString(), TextToSpeech.QUEUE_FLUSH, ops);
                      }
                 }
             } else if (action.equals(ACTION_SPEAK_NOTIFICATION)) {
                 if (mTts.isSpeaking() || !mShareprefs.getBoolean(IntentReceiver.ENABLED_NOTIF, false)) return;
                 Log.i(TAG, "Speak Notifications");
                 final AudioManager am = mAudioManager;
                 if (am.getStreamVolume(STREAM_NOTIFICATION) == 0) {
                     Log.i(TAG, "Not speaking - volume is 0");
                     return;
                 }

                 if (mNotificationText == null || mNotificationApp == null) {
                     return;
                 }

                 final boolean readNotif = mShareprefs.getBoolean(IntentReceiver.ENABLED_NOTIF_READ, false);
                 final boolean isMusicActive = mMusicActive;
                 synchronized (sLock) {
                      if (isMusicActive) {
                          am.setStreamMute(STREAM_MUSIC, true);
                      } else {
                          am.setStreamMute(STREAM_NOTIFICATION, true);
                      }
                      HashMap<String, String> ops = new HashMap<String, String>();
                      mSysVol = am.getStreamVolume(STREAM_SYSTEM);
                      am.setStreamVolume(STREAM_SYSTEM,
                               voiceVolume, 0);
                      ops.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "system");
                      ops.put(TextToSpeech.Engine.KEY_PARAM_STREAM, STREAM_SYSTEM_STR);
                      if (readNotif) {
                          mTts.speak(mContext.getString(R.string.voice_tts_new_notif) + mNotificationApp + mNotificationText,
                                 TextToSpeech.QUEUE_FLUSH, ops);
                      } else {
                          mTts.speak(mContext.getString(R.string.voice_tts_new_notif) + mNotificationApp,
                                 TextToSpeech.QUEUE_FLUSH, ops);
                      }
                 }
             } else if (action.equals(ACTION_SPEAK_MUSIC)) {
                 if (mTts.isSpeaking() || !mShareprefs.getBoolean(IntentReceiver.ENABLED_MUSIC, false)) return;
                 Log.i(TAG, "Speak Music");
                 final AudioManager am = mAudioManager;
                 if (am.getStreamVolume(STREAM_MUSIC) == 0) {
                     Log.i(TAG, "Not speaking - volume is 0");
                     return;
                 }

                 if (mTrackMusic == null) {
                     return;
                 }

                 final String trackMusic = mTrackMusic;
                 synchronized (sLock) {
                      am.setStreamMute(STREAM_MUSIC, true);
                      HashMap<String, String> ops = new HashMap<String, String>();
                      mSysVol = am.getStreamVolume(STREAM_SYSTEM);
                      am.setStreamVolume(STREAM_SYSTEM,
                               voiceVolume, 0);
                      ops.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "music");
                      ops.put(TextToSpeech.Engine.KEY_PARAM_STREAM, STREAM_SYSTEM_STR);
                      mTts.speak(mContext.getString(R.string.voice_tts_music) + trackMusic, TextToSpeech.QUEUE_FLUSH, ops);
                 }
             } else if (action.equals(IntentReceiver.ACTION_RUN_DRIVEMODE)) {
                 if (mTts.isSpeaking()) return;
                 Log.i(TAG, "Speak DriveMode OK");
                 final AudioManager am = mAudioManager;
                 if (am.getStreamVolume(STREAM_NOTIFICATION) == 0) {
                     Log.i(TAG, "Not speaking - volume is 0");
                     return;
                 }

                 final boolean isMusicActive = mMusicActive;
                 synchronized (sLock) {
                      if (isMusicActive) {
                          am.setStreamMute(STREAM_MUSIC, true);
                      } else {
                          am.setStreamMute(STREAM_NOTIFICATION, true);
                      }
                      HashMap<String, String> ops = new HashMap<String, String>();
                      mSysVol = am.getStreamVolume(STREAM_SYSTEM);
                      am.setStreamVolume(STREAM_SYSTEM,
                               am.getStreamMaxVolume(STREAM_SYSTEM), 0);
                      ops.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "system");
                      ops.put(TextToSpeech.Engine.KEY_PARAM_STREAM, STREAM_SYSTEM_STR);
                      mTts.speak(mContext.getString(R.string.voice_tts_engine_started), TextToSpeech.QUEUE_FLUSH, ops);
                 }
             }
        }

        @Override
        public void onDestroy() {
            super.onDestroy();
            if (mTts != null) {
                mTts.shutdown();
                mTts = null;
            }
            mAudioManager.abandonAudioFocus(mAudioFocusListener);
            mDateFormat = null;
            if (mReceiver != null) unregisterReceiver(mReceiver);
            unregisterNotificationListener();
        }

        private String findContactFromNumber(String number) {
            if (number == null) {
                return mContext.getString(R.string.unknown_number_tts);
            }
            String name = filteredQuery(number);
            if (name == null && number.charAt(0) == '+') {
                //convert +<international-code><number> to readable format
                //<number> is normally 10 digits
                number = "0" + number.substring(number.length() - 10);
                name = filteredQuery(number);
            }
            return name == null ? number : name;
        }

        private String filteredQuery(String number) {
            String result = null;
            final Uri uri =  PhoneLookup.CONTENT_FILTER_URI;
            final Uri filtered = Uri.withAppendedPath(uri, number);
            Cursor nameCursor = getContentResolver().query(filtered,
                          PROJECTION, null, null, null);
            final String column = PhoneLookup.DISPLAY_NAME;
            if (nameCursor.moveToFirst()) {
                result = nameCursor.getString(
                          nameCursor.getColumnIndex(column));
            }
            nameCursor.close();
            return result;
        }

        private void showNotification(StatusBarNotification sbn) {
            mNotificationText = getNotificationText(sbn).toString();
            mNotificationApp = getAppName(sbn.getPackageName());
            Intent intent = new Intent(ACTION_SPEAK_NOTIFICATION);
            mContext.sendBroadcast(intent);
        }

        private boolean isValidNotification(StatusBarNotification sbn) {
            return mIncludedApps.contains(sbn.getPackageName())
                   && !notificationIsAnnoying(sbn.getPackageName());
        }

        private void createIncludedAppsSet(String includedApps) {
            if (TextUtils.isEmpty(includedApps))
                return;
            String[] appsToInclude = includedApps.split("\\|");
            mIncludedApps = new HashSet<String>(Arrays.asList(appsToInclude));
        }

        private String getAppName(String packageName) {
            PackageManager packageManager = mContext.getPackageManager();
            ApplicationInfo applicationInfo = null;
            try {
                 applicationInfo = packageManager.getApplicationInfo(packageName, 0);
            } catch (NameNotFoundException nnfe) {}
            return (String)((applicationInfo != null) ? packageManager.getApplicationLabel(applicationInfo) : "???");
        }

        private CharSequence getNotificationText(StatusBarNotification sbn) {
            final Notification notificiation = sbn.getNotification();
            CharSequence tickerText = notificiation.tickerText;
            if (tickerText == null) {
                Bundle extras = notificiation.extras;
                if (extras != null)
                    tickerText = extras.getCharSequence(Notification.EXTRA_TITLE, null);
            }
            return tickerText != null ? tickerText : "";
        }

        private boolean notificationIsAnnoying(String pkg) {
            int getLongThreshold = mShareprefs.getInt(IntentReceiver.ANNOYING_NOTIFICATION, 0);
            final long annoyingNotificationThreshold = ((long) getLongThreshold);

            if (annoyingNotificationThreshold == 0) {
                return false;
            }

            if ("android".equals(pkg)) {
                return false;
            }

            long currentTime = System.currentTimeMillis();
            if (mAnnoyingNotifications.containsKey(pkg)
                && (currentTime - mAnnoyingNotifications.get(pkg)
                        < annoyingNotificationThreshold)) {
                // less than threshold; it's an annoying notification!!
                return true;
            } else {
                // not in map or time to re-add
                mAnnoyingNotifications.put(pkg, currentTime);
                return false;
            }
        }

        private void registerNotificationListener() {
            ComponentName cn = new ComponentName(mContext, getClass().getName());
            try {
                 mNM.registerListener(mNotificationListener, cn, UserHandle.USER_ALL);
            } catch (RemoteException e) {
                 Log.e(TAG, "registerNotificationListener()", e);
            }
        }

        private void unregisterNotificationListener() {
            if (mNotificationListener != null) {
                try {
                     mNM.unregisterListener(mNotificationListener, UserHandle.USER_ALL);
                } catch (RemoteException e) {
                     Log.e(TAG, "registerNotificationListener()", e);
                }
            }
        }

        private void updateMusicResources() {
            mTrackMusic = mMetadata.trackTitle;
            if (mTrackMusic == null || !mActive) return;
            Intent intent = new Intent(ACTION_SPEAK_MUSIC);
            mContext.sendBroadcast(intent);
        }

        private void playbackStateUpdate(int state) {
            boolean active;
            switch (state) {
                 case RemoteControlClient.PLAYSTATE_PLAYING:
                      active = true;
                      break;
                 case RemoteControlClient.PLAYSTATE_ERROR:
                 case RemoteControlClient.PLAYSTATE_PAUSED:
                 default:
                      active = false;
                      break;
            }
            if (active != mActive) {
                mActive = active;
                updateMusicResources();
            }
        }

        private RemoteController.OnClientUpdateListener mRCClientUpdateListener =
                   new RemoteController.OnClientUpdateListener() {

            private String mCurrentTrack = null;

            @Override
            public void onClientChange(boolean clearing) {
                if (clearing) {
                    mMetadata.clear();
                    mCurrentTrack = null;
                    mActive = false;
                    mClientIdLost = true;
                    updateMusicResources();
                }
            }

            @Override
            public void onClientPlaybackStateUpdate(int state, long stateChangeTimeMs,
                      long currentPosMs, float speed) {
                mClientIdLost = false;
                playbackStateUpdate(state);
            }

            @Override
            public void onClientPlaybackStateUpdate(int state) {
                mClientIdLost = false;
                playbackStateUpdate(state);
            }

            @Override
            public void onClientMetadataUpdate(RemoteController.MetadataEditor data) {
                mMetadata.trackTitle = data.getString(MediaMetadataRetriever.METADATA_KEY_TITLE,
                       mMetadata.trackTitle);
                mClientIdLost = false;
                if ((mMetadata.trackTitle != null
                       && !mMetadata.trackTitle.equals(mCurrentTrack))) {
                    mCurrentTrack = mMetadata.trackTitle;
                    updateMusicResources();
                }
            }

            @Override
            public void onClientTransportControlUpdate(int transportControlFlags) {
            }
        };


        class Metadata {
            private String trackTitle;

            public void clear() {
                trackTitle = null;
            }
        }
}
