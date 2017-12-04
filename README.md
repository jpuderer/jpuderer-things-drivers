jpuderer's Android Things user-space drivers [![Build Status](https://travis-ci.org/jpuderer/jpuderer-things-drivers.svg?branch=master)](https://travis-ci.org/jpuderer/jpuderer-things-drivers) 
=================================

Peripheral drivers for Android Things that I wrote and use in
my projects.

The general project layout was borrowed from the Android Things
contrib-project:
https://github.com/androidthings/contrib-drivers

Like the AndroidThings contrib drivers I offer no guarantee of 
correctness, completeness, robustness, or suitability for any particular
purpose.

How to use a driver
===================

And add the appropriate dependancy for the driver you want (this is for 
version 0.1 of the sht1x driver).

```
dependencies {
    implementation 'jpuderer.android.things.drivers:driver-sht1x:0.1'
    ...
}
```

Current drivers
----------------

<!-- DRIVER_LIST_START -->
Driver | Type | Usage (add to your gradle dependencies) | Note
:---:|:---:| --- | ---
[driver-sht1x](sht1x) | temperature and humidity sensor | `jpuderer.android.things.drivers:driver-sht1x:0.1` | [sample](https://github.com/jpuderer/jpuderer-things-drivers/tree/master/example) [changelog](sht1x/CHANGELOG.md)
[driver-hpm](hpm) | Honeywell Particle Sensor | `jpuderer.android.things.drivers:driver-hpm:0.1` | [sample](https://github.com/jpuderer/jpuderer-things-drivers/tree/master/example) [changelog](hpm/CHANGELOG.md)
<!-- DRIVER_LIST_END -->

