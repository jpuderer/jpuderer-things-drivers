SHT1x Sensor Driver for Android Things
=============================================

This driver supports the [SHT1x][product_sht1x] particle sensor.  The sensor measures
both temperature and relative humidity.

How to use the driver
---------------------

### Gradle dependency

To use the `sht1x` driver, simply add the line below to your project's `build.gradle`,
where `<version>` matches the last version of the driver available on [jcenter][jcenter].

```
dependencies {
    implementation 'jpuderer.android.things.drivers:driver-sht1x:0.1'
    ...
}
```

### Sample usage

```java
import net.jpuderer.android.things.drivers.sht1x.Sht1xSensorDriver;

// SHT1x Temp/RH sensor
Sht1xSensor mSht1xSensor;

try {
    final String gpioData = "BCM17";
    final String gpioSck = "BCM27";
    final float vdd = 3.3f;
    mSht1xSensor = new Sht1xSensor(gpioData, gpioSck, vdd);
    mSht1xSensor.start();
} catch (IOException e) {
    // couldn't configure the device...
}

// Read temp and RH
try {
    float temperature = mSht1xSensor.readTemperature();
    float humidity = mSht1xSensor.readHumidity();
} catch (IOException e) {
    // error reading sensor
}

// Close the sensor when finished:

try {
    mSht1xSensor.close();
} catch (IOException e) {
    // error closing sensor
}
```

If you need to read sensor values continuously, you can register the Sht1x with the system and
listen for sensor values using the [Sensor APIs][sensors]:
```java
SensorManager mSensorManager = getSystemService(Context.SENSOR_SERVICE);
SensorEventListener mListener = ...;
Sht1xSensorDriver mSensorDriver;

mSensorManager.registerDynamicSensorCallback(new SensorManager.DynamicSensorCallback() {
    @Override
    public void onDynamicSensorConnected(Sensor sensor) {
        if (sensor.getType() == Sensor.TYPE_AMBIENT_TEMPERATURE) {
            mSensorManager.registerListener(mListener, sensor,
                    SensorManager.SENSOR_DELAY_NORMAL);    
    }
});

try {
    mSensorDriver = new Sht1xSensorDriver(dataPinName, sckPinName, vdd);
    mSensorDriver.registerTemperatureSensor();
} catch (IOException e) {
    // Error configuring sensor
}

// Unregister and close the driver when finished:

mSensorManager.unregisterListener(mListener);
mSensorDriver.unregisterTemperatureSensor();
try {
    mSensorDriver.close();
} catch (IOException e) {
    // error closing sensor
}
```

[product_sht1x]: https://www.sensirion.com/en/environmental-sensors/humidity-sensors/digital-humidity-sensors-for-accurate-measurements/
[jcenter]: https://bintray.com/jpuderer/jpuderer-things-drivers/jpuderer-things-driver-sht1x/_latestVersion
[sensors]: https://developer.android.com/guide/topics/sensors/sensors_overview.html
