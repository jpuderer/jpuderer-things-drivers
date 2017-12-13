package net.jpuderer.android.things.drivers.ds3231;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

import com.google.android.things.device.TimeManager;

import java.io.IOException;


public class RtcReceiver extends BroadcastReceiver {
    private static final String TAG = RtcReceiver.class.getSimpleName();

    @Override
    public void onReceive(Context context, Intent intent) {

        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            handleBootComplete(context);
        } else if (action.equals(Intent.ACTION_TIME_CHANGED)) {
            handleTimeChanged(context);
        } else {
            Log.e(TAG, "Unexpected broadcast action: " + action);
        }
    }

    private void handleBootComplete(Context context) {
        final String i2cDeviceName  = getI2cDeviceName(context);
        if (i2cDeviceName == null) return;

        final PendingResult result = goAsync();
        Thread thread = new Thread() {
            public void run() {
                try {
                    Ds3231Rtc rtc = new Ds3231Rtc(i2cDeviceName);
                    long rtcTimestamp = rtc.getEpochTimeMillis();
                    long systemTimeStamp = System.currentTimeMillis();

                    Log.d(TAG, "RTC timestamp: " + rtcTimestamp);
                    Log.d(TAG, "System timestamp: " + systemTimeStamp);

                    // If the RTC has a sane timestamp, set the system clock using the RTC.
                    // Otherwise, set the RTC using the system time if the system time appears sane
                    if (isSaneTimestamp(rtcTimestamp)) {
                        Log.i(TAG, "Setting system clock using RTC");
                        TimeManager timeManager = new TimeManager();
                        timeManager.setTime(rtcTimestamp);

                        // Re-enable NTP updates.  The call to setTime() disables them automatically,
                        // but that's what we use to update our RTC.
                        timeManager.setAutoTimeEnabled(true);
                    } else if (isSaneTimestamp(systemTimeStamp)) {
                        Log.i(TAG, "Setting RTC time using system clock");
                        rtc.setEpochTimeMillis(systemTimeStamp);
                    }
                    rtc.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error accessing RTC", e);
                } finally {
                    result.setResultCode(Activity.RESULT_OK);
                    result.finish();
                }
            }
        };
        thread.start();
    }

    private void handleTimeChanged(Context context) {
        final String i2cDeviceName  = getI2cDeviceName(context);
        if (i2cDeviceName == null) return;

        Thread thread = new Thread() {
            public void run() {
                try {
                    Ds3231Rtc rtc = new Ds3231Rtc(i2cDeviceName);
                    long timestamp = System.currentTimeMillis();
                    if (isSaneTimestamp(timestamp)) {
                        Log.i(TAG, "Time changed.  Setting RTC time using system clock");
                        rtc.setEpochTimeMillis(timestamp);
                    } else {
                        Log.w(TAG, "Time changed.  Ignoring non-sane timestamp");
                    }
                    rtc.close();
                } catch (IOException e) {
                    Log.e(TAG, "Error accessing RTC", e);
                }
            }
        };
        thread.start();
    }

    // Assume timestamp is not sane if the timestamp predates the build time
    // of this image.  Borrowed this logic from AlarmManagerService
    private boolean isSaneTimestamp(long timestamp) {
        final long systemBuildTime = Environment.getRootDirectory().lastModified();
        return timestamp >= systemBuildTime;
    }

    private String getI2cDeviceName(Context context) {
        String i2cDeviceName = null;
        try {
            ApplicationInfo info =
                    context.getPackageManager().getApplicationInfo(context.getPackageName(),
                            PackageManager.GET_META_DATA);
            Bundle bundle = info.metaData;
            i2cDeviceName = bundle.getString("rtc_i2c_device_name");
        } catch (PackageManager.NameNotFoundException e) {
            Log.e(TAG, "Failed to load meta-data, NameNotFound: " + e.getMessage());
        } catch (NullPointerException e) {
            Log.e(TAG, "Failed to load meta-data, NullPointer: " + e.getMessage());
        }
        if (i2cDeviceName == null) {
            Log.e(TAG, "RTC not available.");
        }
        return i2cDeviceName;
    }
}