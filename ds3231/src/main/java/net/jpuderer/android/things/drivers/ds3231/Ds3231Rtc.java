package net.jpuderer.android.things.drivers.ds3231;

import android.util.Log;

import com.google.android.things.device.TimeManager;
import com.google.android.things.pio.I2cDevice;
import com.google.android.things.pio.PeripheralManager;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class Ds3231Rtc implements AutoCloseable {
    private static final String TAG = Ds3231Rtc.class.getSimpleName();


    // I2C Slave Address
    private static final int I2C_ADDRESS = 0x68;

    // Start of 7 bytes of registers containing the time data
    private static final int DS3231_TIME_REGS = 0x0;

    // Start of 7 bytes of registers containing the alarm data
    private static final int DS3231_ALARM_REGS = 0x7;

    private I2cDevice mDevice;


    public Ds3231Rtc(String i2cDeviceName) throws IOException {
        // Attempt to access the I2C device
        try {
            PeripheralManager manager = PeripheralManager.getInstance();
            mDevice = manager.openI2cDevice(i2cDeviceName, I2C_ADDRESS);
        } catch (IOException e) {
            Log.e(TAG, "Unable to access I2C device", e);
            return;
        }
    }

    @Override
    public void close() throws IOException {
        mDevice.close();
    }

    private static int bcdToDec(byte bcd) {
        return ((bcd >>> 4) * 10) + (bcd & 0x0f);
    }

    private static byte decToBcd(int dec) {
        return (byte) (((dec / 10) << 4) + (dec % 10));
    }

    public LocalDateTime getUtcDateTime() throws IOException {
        byte[] data = new byte[7];
        mDevice.readRegBuffer(DS3231_TIME_REGS, data, data.length);

        TimeManager timeManager = TimeManager.getInstance();

        int second = bcdToDec(data[0]);
        int minute = bcdToDec(data[1]);
        int hour = bcdToDec(data[2]);
        int weekDay = data[3];
        int day = bcdToDec(data[4]);
        int month = bcdToDec((byte) (data[5] & 0x1F));
        int century = (data[5] & 0x80) >>> 7;
        int year = 1900 + (century * 100) + bcdToDec(data[6]);

        return LocalDateTime.of(year, month, day, hour, minute, second);
    }

    public long getEpochTimeMillis() throws IOException {
        return getUtcDateTime().toEpochSecond(ZoneOffset.UTC) * 1000;
    }

    public void setUtcDateTime(LocalDateTime localDateTime) throws IOException {
        byte[] data = new byte[7];

        data[0] = decToBcd(localDateTime.getSecond());
        data[1] = decToBcd(localDateTime.getMinute());
        data[2] = decToBcd(localDateTime.getHour());
        data[3] = decToBcd(localDateTime.getDayOfWeek().getValue());
        data[4] = decToBcd(localDateTime.getDayOfMonth());
        data[5] = decToBcd(localDateTime.getMonthValue());
        // FIXME:  Hmmm... Y2.1k bug in the making.
        // Not much I can do about it, since the hardware doesn't support larger values.
        // I could make the year 0 count from 2000 or something instead of 1900, but I
        // don't know if that would cause any weirdness. Would it?  I would need to carefully
        // pick the year to avoid calendar weirdness?
        data[5] += ((localDateTime.getYear() - 1900) / 100) << 7;
        data[6] = decToBcd(localDateTime.getYear() % 100);

        mDevice.writeRegBuffer(DS3231_TIME_REGS, data, data.length);
    }

    public void setEpochTimeMillis(long timestamp) throws IOException {
        LocalDateTime localDateTime =
            LocalDateTime.ofEpochSecond(timestamp / 1000, 0, ZoneOffset.UTC);
        setUtcDateTime(localDateTime);
    }
}
