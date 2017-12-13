/*
 * Copyright 2017, James Puderer
 * Copyright 2017, The Android Open Source Project
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

package net.jpuderer.android.things.example.rtcsample;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.format.DateFormat;
import android.view.WindowManager;
import android.widget.TextView;

import java.util.Date;

/**
 * Very simple activity with bits borrowed from:
 *   https://github.com/androidthings/sample-bluetooth-le-gattserver
 *
 * Just shows the current time.  The actual work of setting the time is
 * being done in the RtcReceiver code in the ds3231 driver library.
 *
 * The manifest just needs to have the appropriate meta tag:
 *        <!-- Provide the bus name the RTC device is attached to -->
 *        <meta-data android:name="rtc_i2c_device_name" android:value="I2C1" />
 */
public class MainActivity extends Activity {
    private TextView mLocalTimeView;

    @Override
    protected void onCreate (Bundle savedInstanceState){
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mLocalTimeView = (TextView) findViewById(R.id.text_time);

        // Devices with a display should not go to sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Register for system clock events
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(mTimeReceiver, filter);
    }

    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mTimeReceiver);
    }

    private BroadcastReceiver mTimeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            long timestamp = System.currentTimeMillis();
            Date date = new Date(timestamp);
            String displayDate = DateFormat.getMediumDateFormat(getApplicationContext()).format(date)
                    + "\n"
                    + DateFormat.getTimeFormat(getApplicationContext()).format(date);
            mLocalTimeView.setText(displayDate);
        }
    };
}
