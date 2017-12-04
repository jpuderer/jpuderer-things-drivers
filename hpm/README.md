HPM Particle Sensor Driver for Android Things
=============================================

This driver supports the Honeywell [HPM][product_hpm] particle sensor.  The sensor counts both PM2.5 and
PM10 sized particles present in the air.

How to use the driver
---------------------

### Gradle dependency

To use the `hpm` driver, simply add the line below to your project's `build.gradle`,
where `<version>` matches the last version of the driver available on [jcenter][jcenter].

```
dependencies {
    implementation 'jpuderer.android.things.drivers:driver-hpm:0.2'
    ...
}
```

### Sample usage

```java
import net.jpuderer.android.things.drivers.hpm.HpmSensorDriver;

// Honeywell HPM Partical Sensor
HpmSensor mHpmSensor;

try {
    mHpmSensor = new HpmSensor(uartName);
    mHpmSensor.start();
} catch (IOException e) {
    // couldn't configure the device...
}

// Read PM2.5 and PM10 particle counts

try {
    int pm25 = mHpmSensor.readPm25();
    int pm10 = mHpmSensor.readPm10();
} catch (IOException e) {
    // error reading sensor
}

// Close the particle sensor when finished:

try {
    mHpmSensor.close();
} catch (IOException e) {
    // error closing sensor
}
```

If you need to read sensor values continuously, you can register the HPM with the system and
listen for sensor values using the [Sensor APIs][sensors]:
```java
SensorManager mSensorManager = getSystemService(Context.SENSOR_SERVICE);
SensorEventListener mListener = ...;
HpmSensorDriver mSensorDriver;

mSensorManager.registerDynamicSensorCallback(new SensorManager.DynamicSensorCallback() {
    @Override
    public void onDynamicSensorConnected(Sensor sensor) {
        if (sensor.getType() == Sensor.TYPE_DEVICE_PRIVATE_BASE && 
		(HpmSensorDriver.SENSOR_STRING_TYPE.equals(sensor.getStringType()))) {
            mSensorManager.registerListener(mListener, sensor,
                    SensorManager.SENSOR_DELAY_NORMAL);
        }
    }
});

try {
    mSensorDriver = new HpmSensorDriver(uartName);
    mSensorDriver.registerParticleSensor();
} catch (IOException e) {
    // Error configuring sensor
}

// Unregister and close the driver when finished:

mSensorManager.unregisterListener(mListener);
mSensorDriver.unregisterParticleSensor();
try {
    mSensorDriver.close();
} catch (IOException e) {
    // error closing sensor
}
```

[product_hpm]: https://sensing.honeywell.com/sensors/particle-sensors
[jcenter]: https://bintray.com/jpuderer/jpuderer-things-drivers/jpuderer-things-driver-hpm/_latestVersion
[sensors]: https://developer.android.com/guide/topics/sensors/sensors_overview.html
