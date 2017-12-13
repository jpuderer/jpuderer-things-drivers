package net.jpuderer.android.things.example.sensors;

import android.app.Activity;
import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;
import android.view.WindowManager;

import com.google.android.things.pio.PeripheralManagerService;

import net.jpuderer.android.things.drivers.hpm.HpmSensorDriver;
import net.jpuderer.android.things.drivers.sht1x.Sht1xSensorDriver;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;

public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();

    private static final HashSet<Integer> SUPPORTED_SENSORS = new HashSet<Integer>();

    private static final int SAMPLE_INTERVAL_MS = 10000;

    static {
        SUPPORTED_SENSORS.add(Sensor.TYPE_AMBIENT_TEMPERATURE);
        SUPPORTED_SENSORS.add(Sensor.TYPE_RELATIVE_HUMIDITY);
        SUPPORTED_SENSORS.add(Sensor.TYPE_DEVICE_PRIVATE_BASE);
    }

    // SHT1x temperature and humidity sensor driver
    Sht1xSensorDriver mSht1xSensorDriver;

    // Honeywell HPM Partical Sensor
    private HpmSensorDriver mHpmDriver;

    // Handler for posting runnables to
    Handler mHandler;

    // Token for finding our delayed runnable to perform a sampling
    Object mDoSampleToken = new Object();

    // Instance of sensor manager
    private SensorManager mSensorManager;

    // Record recent sensor values and timestamps for these values
    // If the values are too old when we record data, we return a null
    // value (which is interpreted as data not available).
    private class SensorData {
        float temperature;
        long temperature_timestamp;

        float humidity;
        long humidity_timestamp;

        public int pm25;
        public int pm10;
        long particle_timestamp;
    };
    public SensorData mSensorData = new SensorData();

    private SensorEventListener mSensorEventListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            switch (sensorEvent.sensor.getType()) {
                case Sensor.TYPE_AMBIENT_TEMPERATURE:
                    mSensorData.temperature = sensorEvent.values[0];
                    mSensorData.temperature_timestamp = sensorEvent.timestamp;
                    break;
                case Sensor.TYPE_RELATIVE_HUMIDITY:
                    mSensorData.humidity = sensorEvent.values[0];
                    mSensorData.humidity_timestamp = sensorEvent.timestamp;
                    break;
                case Sensor.TYPE_DEVICE_PRIVATE_BASE:
                     if (HpmSensorDriver.SENSOR_STRING_TYPE.equals(sensorEvent.sensor.getStringType())) {
                        mSensorData.pm25 = (int) sensorEvent.values[0];
                        mSensorData.pm10 = (int) sensorEvent.values[1];
                        mSensorData.particle_timestamp = sensorEvent.timestamp;
                        break;
                    }
                default:
                    Log.w(TAG, "Unexpected sensor type: " + sensorEvent.sensor.getType());
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Create handler
        mHandler = new Handler();

        // Register sensors and start requesting data from them
        registerSensors();

        // Devices with a display should not go to sleep
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        Log.d(TAG, "Starting data collection...");
        startDataCollection();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopDataCollection();
        unregisterSensors();
    }

    private void registerSensors() {
        mSensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        mSensorManager.registerDynamicSensorCallback(new SensorManager.DynamicSensorCallback() {
            @Override
            public void onDynamicSensorConnected(Sensor sensor) {
                if (SUPPORTED_SENSORS.contains(sensor.getType())) {
                    mSensorManager.registerListener(mSensorEventListener, sensor,
                            SensorManager.SENSOR_DELAY_NORMAL);
                }
            }
        });

        // Register Temperature, Humidity, and Pressure sensor
        try {
            mSht1xSensorDriver =
                    new Sht1xSensorDriver("BCM17", "BCM27", 3.3f);
            mSht1xSensorDriver.registerTemperatureSensor();
            mSht1xSensorDriver.registerHumiditySensor();
        } catch (IOException e) {
            Log.e(TAG, "Error registering SHT1x sensor");
        }

        // Register particle sensor
        try {
            mHpmDriver = new HpmSensorDriver("UART1");
            mHpmDriver.registerParticleSensor();
        } catch (IOException e) {
            Log.e(TAG, "Error registering HPM sensor driver");
        }
    }

    private void unregisterSensors() {
        mSensorManager.unregisterListener(mSensorEventListener);
        if (mSht1xSensorDriver != null) {
            mSht1xSensorDriver.unregisterTemperatureSensor();
            mSht1xSensorDriver.unregisterHumiditySensor();
            try {
                mSht1xSensorDriver.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing SHT1x sensor");
            }
        }
        if (mHpmDriver != null) {
            mHpmDriver.unregisterParticleSensor();
            try {
                mHpmDriver.close();
            } catch (IOException e) {
                Log.e(TAG, "Error closing GPS driver");
            }
        }
    }

    private void startDataCollection() {
        final Runnable doDataCollection = new Runnable() {
            private boolean toOld(long timestamp) {
                return (SystemClock.uptimeMillis() - (timestamp / 1000000) > SAMPLE_INTERVAL_MS);
            }

            @Override
            public void run() {
                Float temperature = toOld(mSensorData.temperature_timestamp) ?
                        null : mSensorData.temperature;
                Float humidity = toOld(mSensorData.humidity_timestamp) ?
                        null : mSensorData.humidity;
                Integer pm25 = toOld(mSensorData.particle_timestamp) ?
                        null : mSensorData.pm25;
                Integer pm10 = toOld(mSensorData.particle_timestamp) ?
                        null : mSensorData.pm10;

                Log.d(TAG, String.format("Timestamp: %d\n" +
                                "\tTemperature: %.1f, Humidity: %.1f%%\n" +
                                "\tPM2.5, PM10: %d, %d\n",
                        System.currentTimeMillis(),
                        temperature,
                        humidity,
                        pm25,
                        pm10));
                mHandler.postAtTime(this, mDoSampleToken,
                        SystemClock.uptimeMillis() + SAMPLE_INTERVAL_MS);
            }
        };
        mHandler.postAtTime(doDataCollection, mDoSampleToken,
                SystemClock.uptimeMillis() + SAMPLE_INTERVAL_MS);
    }

    private void stopDataCollection() {
        mHandler.removeCallbacksAndMessages(mDoSampleToken);
    }
}
