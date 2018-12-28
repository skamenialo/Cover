package com.skamenialo.cover;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.CountDownTimer;
import android.os.Handler;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.v7.app.NotificationCompat;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.skamenialo.cover.Helpers.BroadcastReceivedListener;
import com.skamenialo.cover.Helpers.CallStateListener;
import com.skamenialo.cover.Helpers.PermissionCheckedListener;
import com.skamenialo.cover.Helpers.Utils;

public class CoverService
        extends android.app.Service
        implements SensorEventListener, BroadcastReceivedListener, CallStateListener{

    //region Fields

    static final String TAG = "SKAMENIALO.SERVICE";
    static final String TAG_UNLOCK_TIMER = "SKAMENIALO.UNLOCK";
    static final String TAG_LOCK_TIMER = "SKAMENIALO.LOCK";

    static CoverService mInstance;
    static PermissionCheckedListener mListener;

    CoverBinder mBinder;
    boolean mInitialized = false;
    boolean mRegistered = false;

    DevicePolicyManager mPolicyManager;
    PowerManager mPowerManager;
    PowerManager.WakeLock mWakeLock;

    CountDownTimer mLockTimer,
            mUnlockTimer;
    int mRetryCount = 0;
    boolean mLockPosted = false,
            mUnlockPosted = false,
            mLockTimerStarted = false,
            mUnlockTimerStarted = false;

    Object mSync;

    SensorManager mSensorManager;
    Sensor mSensor;
    long mLastUpdateSensorProximity;
    float mLastSensorProximity;

    CoverBroadcastReceiver mCoverBroadcastReceiver;
    IntentFilter mFilter;
    boolean mScreenLocked;

    CoverPhoneStateListener mPhoneStateListener;

    //endregion

    //region Constructor

    public CoverService() {
        mBinder = new CoverBinder();
        mSync = new Object();
        mLockTimer = new CountDownTimer(7000, 1000) {
            @Override
            public void onTick(long l) {
                synchronized (mSync) {
                    if (mScreenLocked) {
                        Log.i(TAG_LOCK_TIMER, "Cancel timer 1");
                        stopLockTimer();
                    } else {
                        if (mLastSensorProximity >= 1) {
                            Log.i(TAG_LOCK_TIMER, "Cancel timer 2");
                            stopLockTimer();
                        }
                    }
                }
            }

            @Override
            public void onFinish() {
                synchronized (mSync) {
                    if (!mScreenLocked && mLastSensorProximity < 1) {
                        Log.i(TAG_LOCK_TIMER, "Lock screen");
                        lockScreen();
                    } else {
                        Log.i(TAG_LOCK_TIMER, "Cancel timer 3");
                        stopLockTimer();
                    }
                }
            }
        };
        mUnlockTimer = new CountDownTimer(1, 1) {
            @Override
            public void onTick(long l) {
                synchronized (mSync) {
                    if (mScreenLocked) {
                        if (mLastSensorProximity < 1) {
                            Log.i(TAG_UNLOCK_TIMER, "Cancel timer 1");
                            stopUnlockTimer();
                        }
                    } else {
                        Log.i(TAG_UNLOCK_TIMER, "Cancel timer 2");
                        stopUnlockTimer();
                    }
                }
            }

            @Override
            public void onFinish() {
                synchronized (mSync) {
                    if (mScreenLocked && mLastSensorProximity >= 1) {
                        Log.i(TAG_UNLOCK_TIMER, "Unlock screen");
                        Intent broadcast = new Intent();
                        broadcast.setAction(Utils.WAKE_LOCK);
                        sendBroadcast(broadcast);
                    } else {
                        Log.i(TAG_UNLOCK_TIMER, "Cancel timer 3");
                        stopUnlockTimer();
                    }
                }
            }
        };
        Log.i(TAG, "Constructor");
    }

    //endregion

    //region Private methods

    private void initialize() {
        if (mInitialized)
            return;
        try {
            mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
            mSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_PROXIMITY);
            mLastUpdateSensorProximity = System.currentTimeMillis();
            mLastSensorProximity = 0;

            mFilter = new IntentFilter(Intent.ACTION_SCREEN_ON);
            mFilter.addAction(Intent.ACTION_SCREEN_OFF);
            mFilter.addAction(Utils.WAKE_LOCK);
            mCoverBroadcastReceiver = new CoverBroadcastReceiver();
            mCoverBroadcastReceiver.SetBroadcastReceivedListener(this);
            mScreenLocked = false;

            mPolicyManager = (DevicePolicyManager) getSystemService(Context.DEVICE_POLICY_SERVICE);
            mPowerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
            mWakeLock = mPowerManager.newWakeLock(PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.FULL_WAKE_LOCK, Utils.WAKE_LOCK);

            mPhoneStateListener = new CoverPhoneStateListener();
            mPhoneStateListener.setCallStateListener(this);
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        mInitialized = true;
        Log.i(TAG, "Initialized");
    }

    private void register(boolean all) {
        if (mRegistered)
            return;
        try {
            registerReceiver(mCoverBroadcastReceiver, mFilter);
            Log.i(TAG, "CoverBroadcastReceiver registered");
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        try{
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);
            Log.i(TAG, "CoverPhoneStateListener registered");
        }catch(Exception e){
            Log.e(TAG, e.toString());
        }
        if(all)
            registerSensor();
        mRegistered = true;
    }

    private void unregister(boolean all) {
        if (!mRegistered)
            return;
        try {
            unregisterReceiver(mCoverBroadcastReceiver);
            Log.i(TAG, "CoverBroadcastReceiver unregistered");
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
        try{
            TelephonyManager telephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
            telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_NONE);
            Log.i(TAG, "CoverPhoneStateListener unregistered");
        }catch(Exception e){
            Log.e(TAG, e.toString());
        }
        if(all)
            unregisterSensor();
        mRegistered = false;
    }

    private void registerSensor(){
        try {
            mSensorManager.registerListener(this, mSensor, SensorManager.SENSOR_DELAY_NORMAL);
            Log.i(TAG, "Sensor listener registered");
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private void unregisterSensor(){
        try {
            mSensorManager.unregisterListener(this, mSensor);
            Log.i(TAG, "Sensor listener unregistered");
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    private boolean checkPermission() {
        ComponentName adminComponent = new ComponentName(mInstance, PermissionReceiver.class);

        if (mPolicyManager.isAdminActive(adminComponent)) {
            Log.i(TAG, "Admin active");
            if (mListener != null)
                mListener.onPermissionChecked(adminComponent, true);
            return true;
        } else {
            Log.i(TAG, "Admin inactive");
            if (mListener != null)
                mListener.onPermissionChecked(adminComponent, false);
            return false;
        }
    }

    private void lockScreen() {
        final Handler handler = new Handler(getMainLooper());

        if (!mLockPosted) {
            mLockPosted = true;
            handler.post(new Runnable() {
                @Override
                public void run() {
                    if (mPowerManager.isInteractive()) {
                        if (mRetryCount++ <= Utils.MAX_RETRY_COUNT) {
                            Log.i(TAG, "Try lock");
                            mPolicyManager.lockNow();
                            handler.postDelayed(this, Utils.RETRY_DELAY * mRetryCount);
                            return;
                        } else {
                            Log.i(TAG, "Lock not able");
                        }
                    } else {
                        Log.i(TAG, "Already locked");
                    }
                    mLockPosted = false;
                    mRetryCount = 0;
                }
            });
        }
    }

    private void unlockScreen() {
        final Handler handler = new Handler(getMainLooper());

        if (!mUnlockPosted) {
            mUnlockPosted = true;
            Log.i(TAG, "Acquire wake lock");
            try {
                mWakeLock.acquire();
            }catch(Exception e){
                Log.e(TAG, e.toString());
            }
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (mWakeLock.isHeld()) {
                        Log.i(TAG, "Release wake lock");
                        mWakeLock.release();
                    }
                    mUnlockPosted = false;
                }
            }, 5000);
        }
    }

    private void startLockTimer(){
        if(!mLockTimerStarted) {
            mLockTimerStarted = true;
            mLockTimer.start();
        }
    }

    private void startUnlockTimer(){
        if(!mUnlockTimerStarted) {
            mUnlockTimerStarted = true;
            mUnlockTimer.start();
        }
    }

    private void stopLockTimer(){
        if(mLockTimerStarted) {
            mLockTimer.cancel();
            mLockTimerStarted = false;
        }
    }

    private void stopUnlockTimer(){
        if(mUnlockTimerStarted) {
            mUnlockTimer.cancel();
            mUnlockTimerStarted = false;
        }
    }

    private void startNotification(){
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new NotificationCompat.Builder(this)
                .setContentTitle(getString(R.string.notification_title))
                .setContentText(getString(R.string.notification_message))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setColor(Color.parseColor("#0099cc"))
                .setAutoCancel(false)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
        startForeground(Utils.NOTIFICATION_ID, notification);
        Log.i(TAG, "Notification started");
    }

    private void stopNotification(){
        NotificationManager notificationManager = ((NotificationManager)getSystemService(NOTIFICATION_SERVICE));
        notificationManager.cancel(Utils.NOTIFICATION_ID);
        Log.i(TAG, "Notification stopped");
    }

    //endregion

    //region Service methods

    @Override
    public void onCreate() {
        super.onCreate();
        mInstance = this;
        initialize();
        Log.i(TAG, "Service created");
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (checkPermission()) {
            register(true);
            startNotification();
        }
        Log.i(TAG, "Service started command");
        return START_STICKY_COMPATIBILITY;
    }

    @Override
    public void onDestroy() {
        unregister(true);
        stopNotification();
        mInitialized = false;
        mInstance = null;
        Log.i(TAG, "Service destroyed");
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    //endregion

    //region Listeners methods

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        try {
            if (sensorEvent.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                /*if ((System.currentTimeMillis() - mLastUpdateSensorProximity) < Utils.MIN_SENSOR_TIMEOUT) {
                    return;
                }*/
                float currentProximity = sensorEvent.values[0];
                if(currentProximity == mLastSensorProximity)
                    return;

                float proximityDelta = Math.abs(mLastSensorProximity - currentProximity);
                Log.i(TAG, String.format("last=%f, current=%f, delta=%f", mLastSensorProximity, currentProximity, proximityDelta));
                synchronized (mSync) {
                    if (mScreenLocked) {
                        stopLockTimer();
                        if (currentProximity >= 1) {
                            Log.i(TAG, "start unlock timer");
                            startUnlockTimer();
                        } else {
                            Log.i(TAG, "stop unlock timer");
                            stopUnlockTimer();
                        }
                    } else {
                        stopUnlockTimer();
                        if (currentProximity < 1) {
                            Log.i(TAG, "start lock timer");
                            startLockTimer();
                        } else {
                            Log.i(TAG, "stop lock timer");
                            stopLockTimer();
                        }
                    }
                    mLastUpdateSensorProximity = System.currentTimeMillis();
                    mLastSensorProximity = currentProximity;
                }
            }
        } catch (Exception e) {
            Log.e(TAG, e.toString());
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {

    }

    @Override
    public void onBroadcastReceived(String action, Intent intent) {
        synchronized (mSync) {
            switch (action) {
                case Intent.ACTION_SCREEN_ON:
                    Log.i(TAG, "SCREEN_ON");
                    mScreenLocked = false;
                    stopUnlockTimer();
                    break;
                case Intent.ACTION_SCREEN_OFF:
                    Log.i(TAG, "SCREEN_OFF");
                    mScreenLocked = true;
                    stopLockTimer();
                    if (mWakeLock.isHeld())
                        mWakeLock.release();
                    break;
                case Utils.WAKE_LOCK:
                    Log.i(TAG, "WAKE_LOCK");
                    unlockScreen();
                    break;
            }
        }
    }

    @Override
    public void onCallStateChanged(int state) {
        if(state == TelephonyManager.CALL_STATE_IDLE){
            registerSensor();
        }else{
            unregisterSensor();
        }
    }

    //endregion

    //region Public methods

    public static CoverService getInstance() {
        return mInstance;
    }

    public static ComponentName getAdminComponent() {
        if (mInstance == null)
            return null;
        return new ComponentName(mInstance, PermissionReceiver.class);
    }

    public static void setOnPermissionChecked(PermissionCheckedListener listener) {
        mListener = listener;
    }

    //endregion

    //region Helpers

    public class CoverBinder extends Binder {
        CoverService getService() {
            return CoverService.this;
        }
    }

    //endregion
}
