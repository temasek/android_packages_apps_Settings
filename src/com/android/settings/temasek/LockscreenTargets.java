/*
 * Copyright (C) 2012 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.settings.temasek;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.DialogInterface;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.Intent.ShortcutIconResource;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.Toast;

import com.android.internal.util.temasek.AppHelper;
import com.android.internal.util.temasek.DeviceUtils;
import com.android.internal.util.temasek.LockscreenTargetUtils;
import com.android.internal.widget.multiwaveview.GlowPadView;
import com.android.internal.widget.multiwaveview.TargetDrawable;
import com.android.settings.R;
import com.android.settings.temasek.util.IconPicker;
import com.android.settings.temasek.util.IconPicker.OnIconPickListener;
import com.android.settings.temasek.util.ShortcutPickerHelper;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;

public class LockscreenTargets extends Fragment implements
        ShortcutPickerHelper.OnPickListener,
        GlowPadView.OnTriggerListener, OnIconPickListener {

    private static final String TAG = "LockscreenTargets";

    private static final int DLG_SETUP_TARGETS   = 0;
    private static final int DLG_RESET_TARGETS = 1;

    private Activity mActivity;
    private PackageManager mPm;
    private Resources mResources;
    private ShortcutPickerHelper mPicker;
    private IconPicker mIconPicker;

    private GlowPadView mWaveView;
    private ViewGroup mContainer;

    private ImageButton mDialogIcon;
    private Button mDialogLabel;

    private ArrayList<TargetInfo> mTargetStore = new ArrayList<TargetInfo>();
    private int mTargetOffset;
    private int mMaxTargets;

    private File mTemporaryImage;
    private int mTargetIndex = 0;
    private static String mEmptyLabel;

    private static final int MENU_RESET = Menu.FIRST;

    private Resources mKeyguardResources;

    private static class TargetInfo {
        String uri;
        String packageName;
        StateListDrawable icon;
        Drawable defaultIcon;
        String iconType;
        String iconSource;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mContainer = container;

        setHasOptionsMenu(true);

        mActivity = getActivity();
        mResources = getResources();

        mTargetOffset = LockscreenTargetUtils.getTargetOffset(mActivity);
        mMaxTargets = LockscreenTargetUtils.getMaxTargets(mActivity);

        mIconPicker = new IconPicker(mActivity, this);
        mPicker = new ShortcutPickerHelper(mActivity, this);

        mTemporaryImage = new File(mActivity.getCacheDir() + "/target.tmp");
        mEmptyLabel = mResources.getString(R.string.lockscreen_target_empty);

        mPm = mActivity.getPackageManager();
        try {
            mKeyguardResources = mPm.getResourcesForApplication("com.android.keyguard");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return inflater.inflate(R.layout.lockscreen_targets, container, false);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Drawable handle = LockscreenTargetUtils.getDrawableFromResources(mActivity,
                "com.android.keyguard", "ic_lockscreen_handle", false);

        mWaveView = (GlowPadView) mActivity.findViewById(R.id.lock_target);
        mWaveView.setHandleDrawable(handle);
        mWaveView.setOnTriggerListener(this);

        initializeView(Settings.System.getString(mActivity.getContentResolver(),
                Settings.System.LOCKSCREEN_TARGETS));
    }

    @Override
    public void onResume() {
        super.onResume();
        // If running on a phone, remove padding around container
        if (DeviceUtils.isPhone(mActivity)) {
            mContainer.setPadding(0, 0, 0, 0);
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        menu.add(0, MENU_RESET, 0, R.string.reset)
            .setIcon(R.drawable.ic_settings_backup) // use the backup icon
            .setAlphabeticShortcut('r')
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_RESET:
                showDialogInner(DLG_RESET_TARGETS, 0);
                return true;
            default:
                return false;
        }
    }

    private void initializeView(String input) {

        if (input == null) {
            input = GlowPadView.EMPTY_TARGET;
        }

        mTargetStore.clear();

        final Drawable activeBack = mKeyguardResources.getDrawable(
                mKeyguardResources.getIdentifier(
                "com.android.keyguard:drawable/ic_lockscreen_target_activated", null, null));
        final String[] targetStore = input.split("\\|");

        //Add the unlock icon
        Drawable unlockFront = mKeyguardResources.getDrawable(mKeyguardResources.getIdentifier(
                "com.android.keyguard:drawable/ic_lockscreen_unlock_normal", null, null));
        Drawable unlockBack = mKeyguardResources.getDrawable(mKeyguardResources.getIdentifier(
                "com.android.keyguard:drawable/ic_lockscreen_unlock_activated", null, null));
        TargetInfo unlockTarget = new TargetInfo();
        unlockTarget.icon = LockscreenTargetUtils.getLayeredDrawable(
                mActivity, unlockBack, unlockFront, 0, true);
        mTargetStore.add(unlockTarget);

        for (int i = 0; i < 8 - mTargetOffset - 1; i++) {
            if (i >= mMaxTargets) {
                mTargetStore.add(new TargetInfo());
                continue;
            }

            Drawable front = null;
            Drawable back = activeBack;
            boolean frontBlank = false;
            TargetInfo info = new TargetInfo();
            info.uri = i < targetStore.length ? targetStore[i] : GlowPadView.EMPTY_TARGET;

            if (!info.uri.equals(GlowPadView.EMPTY_TARGET)) {
                try {
                    Intent intent = Intent.parseUri(info.uri, 0);
                    if (intent.hasExtra(GlowPadView.ICON_FILE)) {
                        info.iconType = GlowPadView.ICON_FILE;
                        info.iconSource = intent.getStringExtra(GlowPadView.ICON_FILE);
                        front = LockscreenTargetUtils.getDrawableFromFile(mActivity,
                                info.iconSource);
                    } else if (intent.hasExtra(GlowPadView.ICON_RESOURCE)) {
                        info.iconType = GlowPadView.ICON_RESOURCE;
                        info.iconSource = intent.getStringExtra(GlowPadView.ICON_RESOURCE);
                        info.packageName = intent.getStringExtra(GlowPadView.ICON_PACKAGE);

                        if (info.iconSource != null) {
                            front = LockscreenTargetUtils.getDrawableFromResources(mActivity,
                                    info.packageName, info.iconSource, false);
                            back = LockscreenTargetUtils.getDrawableFromResources(mActivity,
                                    info.packageName, info.iconSource, true);
                            frontBlank = true;
                        }
                    }
                    if (front == null) {
                        info.iconType = null;
                        front = LockscreenTargetUtils.getDrawableFromIntent(mActivity, intent);
                    }
                } catch (URISyntaxException e) {
                    Log.w(TAG, "Invalid lockscreen target " + info.uri);
                }
            }

            if (back == null || front == null) {
                front = mResources.getDrawable(R.drawable.ic_empty);
            }

            int inset = LockscreenTargetUtils.getInsetForIconType(mActivity, info.iconType);
            info.icon = LockscreenTargetUtils.getLayeredDrawable(mActivity,
                    back, front, inset, frontBlank);
            info.defaultIcon = front;

            mTargetStore.add(info);
        }

        for (int i = 0; i < mTargetOffset; i++) {
            mTargetStore.add(new TargetInfo());
        }

        ArrayList<TargetDrawable> targetDrawables = new ArrayList<TargetDrawable>();
        for (TargetInfo i : mTargetStore) {
            targetDrawables.add(new TargetDrawable(mResources, i != null ? i.icon : null));
        }
        mWaveView.setTargetResources(targetDrawables);
    }

    /**
     * Save targets to settings provider
     */
    private void saveAll() {
        StringBuilder targetLayout = new StringBuilder();
        ArrayList<String> existingImages = new ArrayList<String>();
        boolean hasValidTargets = false;

        for (int i = mTargetOffset + 1; i <= mTargetOffset + mMaxTargets; i++) {
            TargetInfo info = mTargetStore.get(i);
            String uri = info.uri;

            if (info.iconSource != null) {
                existingImages.add(info.iconSource);
            }

            if (!TextUtils.equals(uri, GlowPadView.EMPTY_TARGET)) {
                try {
                    Intent intent = Intent.parseUri(info.uri, 0);
                    // make sure to remove any outdated icon references
                    intent.removeExtra(GlowPadView.ICON_RESOURCE);
                    intent.removeExtra(GlowPadView.ICON_FILE);
                    if (info.iconType != null) {
                        intent.putExtra(info.iconType, info.iconSource);
                    }
                    if (GlowPadView.ICON_RESOURCE.equals(info.iconType)
                            && info.packageName != null) {
                        intent.putExtra(GlowPadView.ICON_PACKAGE, info.packageName);
                    } else {
                        intent.removeExtra(GlowPadView.ICON_PACKAGE);
                    }

                    uri = intent.toUri(0);
                    hasValidTargets = true;
                } catch (URISyntaxException e) {
                    Log.w(TAG, "Invalid uri " + info.uri + " on save, ignoring");
                    uri = GlowPadView.EMPTY_TARGET;
                }
            }

            if (targetLayout.length() > 0) {
                targetLayout.append("|");
            }
            targetLayout.append(uri);
        }

        final String targets = hasValidTargets ? targetLayout.toString() : null;
        Settings.System.putString(mActivity.getContentResolver(),
                Settings.System.LOCKSCREEN_TARGETS, targets);

        for (File image : mActivity.getFilesDir().listFiles()) {
            if (image.getName().startsWith("lockscreen_")
                    && !existingImages.contains(image.getAbsolutePath())) {
                image.delete();
            }
        }
        initializeView(targets);

    }

    /**
     * Updates a target in the GlowPadView
     */
    private void setTarget(int position, String uri, Drawable drawable,
            String iconType, String iconSource, String packageName) {
        TargetInfo item = mTargetStore.get(position);
        StateListDrawable state = (StateListDrawable) item.icon;
        LayerDrawable inactiveLayer = (LayerDrawable) state.getStateDrawable(0);
        LayerDrawable activeLayer = (LayerDrawable) state.getStateDrawable(1);
        boolean hasBackground = false;

        inactiveLayer.setDrawableByLayerId(1, drawable);

        if (GlowPadView.ICON_RESOURCE.equals(iconType) && iconSource != null) {
            InsetDrawable empty = new InsetDrawable(
                    mResources.getDrawable(android.R.color.transparent), 0, 0, 0, 0);
            activeLayer.setDrawableByLayerId(1, empty);
            Drawable back = LockscreenTargetUtils.getDrawableFromResources(mActivity,
                    packageName, iconSource, true);
            if (back != null) {
                activeLayer.setDrawableByLayerId(0, back);
                hasBackground = true;
            }
        } else {
            activeLayer.setDrawableByLayerId(1, drawable);
        }

        if (!hasBackground) {
            final Drawable activeBack = mKeyguardResources.getDrawable(
                    mKeyguardResources.getIdentifier(
                    "com.android.keyguard:drawable/ic_lockscreen_target_activated", null, null));
            activeLayer.setDrawableByLayerId(0, new InsetDrawable(activeBack, 0, 0, 0, 0));
        }

        item.defaultIcon = getPickedIconFromDialog();
        item.uri = uri;
        item.iconType = iconType;
        item.iconSource = iconSource;
        item.packageName = packageName;

        saveAll();
    }

    private Drawable getPickedIconFromDialog() {
        return mDialogIcon.getDrawable().mutate();
    }

    private void setIconForDialog(Drawable icon) {
        // need to mutate the drawable here to not share drawable state with GlowPadView
        mDialogIcon.setImageDrawable(icon.getConstantState().newDrawable().mutate());
    }

    @Override
    public void shortcutPicked(String uri, String friendlyName, boolean isApplication) {
        if (uri == null) {
            return;
        }

        try {
            Intent intent = Intent.parseUri(uri, 0);
            Drawable icon = LockscreenTargetUtils.getDrawableFromIntent(mActivity, intent);

            mDialogLabel.setText(friendlyName);
            mDialogLabel.setTag(uri);
            // this is a fresh drawable, so we can assign it directly
            mDialogIcon.setImageDrawable(icon);
            mDialogIcon.setTag(null);
        } catch (URISyntaxException e) {
            Log.wtf(TAG, "Invalid uri " + uri + " on pick");
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        String shortcutName = null;
        if (data != null) {
            shortcutName = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME);
        }

        if (TextUtils.equals(shortcutName, mEmptyLabel)) {
            mDialogLabel.setText(mEmptyLabel);
            mDialogLabel.setTag(GlowPadView.EMPTY_TARGET);
            mDialogIcon.setImageResource(R.drawable.ic_empty);
            mDialogIcon.setTag(null);
        } else if (requestCode == IconPicker.REQUEST_PICK_SYSTEM
                || requestCode == IconPicker.REQUEST_PICK_GALLERY
                || requestCode == IconPicker.REQUEST_PICK_ICON_PACK) {
            mIconPicker.onActivityResult(requestCode, resultCode, data);
        } else if (requestCode != Activity.RESULT_CANCELED
                && resultCode != Activity.RESULT_CANCELED) {
            mPicker.onActivityResult(requestCode, resultCode, data);
        }
    }

    @Override
    public void onGrabbed(View v, int handle) {
    }

    @Override
    public void onReleased(View v, int handle) {
    }

//    @Override
//    public void onTargetChange(View v, int whichHandle) {
//    }

    @Override
    public void onTrigger(View v, final int target) {
        if (target == mTargetOffset) {
            mWaveView.reset(true);
            return;
        }
        showDialogInner(DLG_SETUP_TARGETS, target);
    }

    private View createShortcutDialogView(int target) {
        View view = View.inflate(mActivity, R.layout.lockscreen_shortcut_dialog, null);
        view.findViewById(R.id.icon).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mDialogLabel.getText().equals(mEmptyLabel)) {
                    try {
                        mTemporaryImage.createNewFile();
                        mTemporaryImage.setWritable(true, false);
                        mIconPicker.pickIcon(getId(), mTemporaryImage);
                    } catch (IOException e) {
                        Log.d(TAG, "Could not create temporary icon", e);
                    }
                }
            }
        });
        view.findViewById(R.id.label).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mPicker.pickShortcut(getId());
            }
        });

        mDialogIcon = (ImageButton) view.findViewById(R.id.icon);
        mDialogLabel = (Button) view.findViewById(R.id.label);

        TargetInfo item = mTargetStore.get(target);
        setIconForDialog(item.defaultIcon);

        TargetInfo icon = new TargetInfo();
        icon.iconType = item.iconType;
        icon.iconSource = item.iconSource;
        icon.packageName = item.packageName;
        mDialogIcon.setTag(icon);

        if (TextUtils.equals(item.uri, GlowPadView.EMPTY_TARGET)) {
            mDialogLabel.setText(mEmptyLabel);
        } else {
            mDialogLabel.setText(AppHelper.getFriendlyNameForUri(mActivity, mPm, item.uri));
        }
        mDialogLabel.setTag(item.uri);

        return view;
    }

    @Override
    public void onGrabbedStateChange(View v, int handle) {
    }

    @Override
    public void iconPicked(int requestCode, int resultCode, Intent intent) {
        TargetInfo icon = new TargetInfo();
        Drawable iconDrawable = null;

        if (requestCode == IconPicker.REQUEST_PICK_GALLERY) {
            if (resultCode == Activity.RESULT_OK) {
                File imageFile = new File(mActivity.getFilesDir(),
                        "/lockscreen_" + System.currentTimeMillis() + ".png");
                if (mTemporaryImage.exists()) {
                    mTemporaryImage.renameTo(imageFile);
                }
                imageFile.setReadable(true, false);

                icon.iconType = GlowPadView.ICON_FILE;
                icon.iconSource = imageFile.getAbsolutePath();
                iconDrawable = LockscreenTargetUtils.getDrawableFromFile(
                        mActivity, icon.iconSource);
            } else {
                if (mTemporaryImage.exists()) {
                    mTemporaryImage.delete();
                }
                return;
            }
        } else if (requestCode == IconPicker.REQUEST_PICK_SYSTEM) {
            icon.packageName = intent.getStringExtra(IconPicker.PACKAGE_NAME);
            icon.iconType = GlowPadView.ICON_RESOURCE;
            icon.iconSource = intent.getStringExtra(IconPicker.RESOURCE_NAME);
            iconDrawable = LockscreenTargetUtils.getDrawableFromResources(mActivity,
                    icon.packageName, icon.iconSource, false);
        } else if (requestCode == IconPicker.REQUEST_PICK_ICON_PACK
                && resultCode == Activity.RESULT_OK) {
            icon.packageName = intent.getStringExtra(IconPicker.PACKAGE_NAME);
            icon.iconType = GlowPadView.ICON_RESOURCE;
            icon.iconSource = intent.getStringExtra(IconPicker.RESOURCE_NAME);
            iconDrawable = LockscreenTargetUtils.getDrawableFromResources(mActivity,
                    icon.packageName, icon.iconSource, false);
        } else {
            return;
        }

        if (iconDrawable != null) {
            mDialogIcon.setTag(icon);
            setIconForDialog(iconDrawable);
        } else {
            Log.w(TAG, "Could not fetch icon, keeping old one (type=" + icon.iconType
                    + ", source=" + icon.iconSource + ", package= " + icon.packageName + ")");
        }
    }

    @Override
    public void onFinishFinalAnimation() {
    }

    private void showDialogInner(int id, int target) {
        DialogFragment newFragment = MyAlertDialogFragment.newInstance(id, target);
        newFragment.setTargetFragment(this, 0);
        newFragment.show(getFragmentManager(), "dialog " + id);
    }

    public static class MyAlertDialogFragment extends DialogFragment {

        public static MyAlertDialogFragment newInstance(int id, int target) {
            MyAlertDialogFragment frag = new MyAlertDialogFragment();
            Bundle args = new Bundle();
            args.putInt("id", id);
            args.putInt("target", target);
            frag.setArguments(args);
            return frag;
        }

        LockscreenTargets getOwner() {
            return (LockscreenTargets) getTargetFragment();
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            int id = getArguments().getInt("id");
            final int target = getArguments().getInt("target");
            switch (id) {
                case DLG_SETUP_TARGETS:
                    return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.lockscreen_target_edit_title)
                    .setView(getOwner().createShortcutDialogView(target))
                    .setNegativeButton(R.string.cancel, null)
                    .setCancelable(false)
                    .setPositiveButton(R.string.dlg_ok,
                        new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            getOwner().mTargetIndex = target;
                            TargetInfo info = (TargetInfo) getOwner().mDialogIcon.getTag();
                            String type = info != null ? info.iconType : null;
                            String source = info != null ? info.iconSource : null;
                            String packageName = info != null ? info.packageName : null;
                            int inset = LockscreenTargetUtils
                                    .getInsetForIconType(getOwner().mActivity, type);

                            InsetDrawable drawable = new InsetDrawable(
                                    getOwner().getPickedIconFromDialog(),
                                    inset, inset, inset, inset);
                            getOwner().setTarget(target,
                                    getOwner().mDialogLabel.getTag().toString(),
                                    drawable, type, source, packageName);
                        }
                    })
                    .create();
                case DLG_RESET_TARGETS:
                    return new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.reset)
                    .setMessage(R.string.lockscreen_target_reset_message)
                    .setNegativeButton(R.string.cancel, null)
                    .setPositiveButton(R.string.dlg_ok,
                        new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            getOwner().initializeView(null);
                            Settings.System.putString(getOwner().mActivity.getContentResolver(),
                                    Settings.System.LOCKSCREEN_TARGETS, null);
                            for (File pic : getOwner().mActivity.getFilesDir().listFiles()) {
                                if (pic.getName().startsWith("lockscreen_")) {
                                    pic.delete();
                                }
                            }
                            Toast.makeText(getOwner().mActivity, R.string.lockscreen_target_reset,
                                    Toast.LENGTH_LONG).show();
                        }
                    })
                    .create();
            }
            throw new IllegalArgumentException("unknown id " + id);
        }

        @Override
        public void onCancel(DialogInterface dialog) {

        }
    }

}
