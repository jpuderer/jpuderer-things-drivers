package net.jpuderer.android.things.drivers.hpm;

import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.UartDevice;
import com.google.android.things.pio.UartDeviceCallback;

import java.io.IOException;
import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import java.lang.Byte;

public class HpmSensor implements AutoCloseable {
    private static final String TAG = HpmSensor.class.getSimpleName();

    private static final byte[] CMD_ENABLE_AUTO_SEND = { 0x68, 0x01, 0x40, 0x57 };
    private static final byte[] CMD_STOP_AUTO_SEND = { 0x68, 0x01, 0x20, 0x77 };
    private static final byte[] CMD_START_PARTICLE_MEASUREMENT = { 0x68, 0x01, 0x01, (byte)0x96 };
    private static final byte[] CMD_STOP_PARTICLE_MEASUREMENT = { 0x68, 0x01, 0x02, (byte)0x95 };

    private static final short RESPONSE_ACK_OK = (short) 0xA5A5;
    private static final short RESPONSE_ACK_ERROR = (short) 0x9696;
    private static final short RESPONSE_DATA_FRAME = (short) 0x424d;

    private static final int LENGTH_DATA_FRAME = 32;

    private static final boolean DEBUG = false;

    public static final long HPM_MEASUREMENT_INTERVAL = TimeUnit.SECONDS.toMicros(1);

    public static final float HPM_PARTICLE_RESOLUTION = 1f;
    public static final float HPM_PARTICLE_MAX = 1000f;
    public static final float HPM_POWER_CONSUMPTION_UA = 80000;

    private UartDevice mDevice;

    private Handler mHandler;

    // Is the sensor started (making measurements)?
    private boolean mStarted;

    // If set, contains a stored exception to throw when user asks for data
    private IOException mLastException;

    private int mPm25;
    private int mPm10;

    private UartDeviceCallback mUartCallback = new UartDeviceCallback() {
        @Override
        public boolean onUartDeviceDataAvailable(UartDevice uart) {
            // Read available data from the UART device
            try {
                readUartBuffer(uart);
            } catch (IOException e) {
                Log.w(TAG, "Unable to access UART device", e);
            }

            // Continue listening for more interrupts
            return true;
        }

        @Override
        public void onUartDeviceError(UartDevice uart, int error) {
            Log.w(TAG, uart + ": Error event " + error);
        }
    };

    public HpmSensor(String uartName, Handler handler) throws IOException {
        mHandler = handler;

        // Open and setup UARTdevice
        PeripheralManager manager = PeripheralManager.getInstance();
        mDevice = manager.openUartDevice(uartName);
        mDevice.setBaudrate(9600);
        mDevice.setDataSize(8);
        mDevice.setParity(UartDevice.PARITY_NONE);
        mDevice.setStopBits(1);
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }

    @Override
    public void close() throws Exception {
        try {
            stop();
        } finally {
            if (mDevice != null) mDevice.close();
        }
    }

    public void start() throws IOException {
        if (DEBUG) Log.d(TAG, "Start");
        if (mStarted) return;

        // Keep an exception, in case data is requested before it is available;
        mLastException = new IOException("No data available");

        // Begin listening for interrupt events
        mDevice.registerUartDeviceCallback(mHandler, mUartCallback);

        // Turn on autosend (to get regular sensor readings)
        sendCommand(CMD_START_PARTICLE_MEASUREMENT);
        SystemClock.sleep(20);
        sendCommand(CMD_ENABLE_AUTO_SEND);
        mStarted = true;
    }

    public void stop() throws IOException {
        if (DEBUG) Log.d(TAG, "Stop");
        if (mDevice == null) return;
        // FIXME: If I stop the sensor, it doesn't seem to turn back on.
        sendCommand(CMD_STOP_PARTICLE_MEASUREMENT);
        SystemClock.sleep(20);
        sendCommand(CMD_STOP_AUTO_SEND);
        mDevice.unregisterUartDeviceCallback(mUartCallback);
        mStarted = false;
    }

    private void sendCommand(byte[] command) throws IOException {
        if (DEBUG) Log.d(TAG, "sendCommand");
        int count = mDevice.write(command, command.length);
    }

    private void readUartBuffer(UartDevice uart) throws IOException {
        // Maximum amount of data to read at one time
        byte[] buffer = new byte[LENGTH_DATA_FRAME * 2];
        int count;
        while ((count = uart.read(buffer, buffer.length)) > 0) {
            processBuffer(buffer, count);
        }
    }

    /**
     * Traverse each buffer received from the UART, looking for
     * a valid message frame.
     */
    private ByteBuffer mMessageBuffer = ByteBuffer.allocate(LENGTH_DATA_FRAME * 16);
    private static int STATE_IDLE = 0;
    private static int STATE_READING_DATA_FRAME = 1;

    private int mState = STATE_IDLE;
    private void processBuffer(byte[] buffer, int count) {
        for (int i = 0; i < count; i++) {
            try {
                mMessageBuffer.put(buffer[i]);
            } catch (BufferOverflowException e) {
                // If we overrun our buffer, we have some catch up to do.  Just clear the data
                // and start again.  Hopefully we'll catch up to a valid frame shortly.
                mMessageBuffer.clear();
                return;
            }
        }
        if (mMessageBuffer.position() < 2)
            // We can't do anything until we have at least a word
            return;
        if (mState == STATE_IDLE) {
            short word = mMessageBuffer.getShort(0);
            if (word == RESPONSE_DATA_FRAME) {
                mState = STATE_READING_DATA_FRAME;
            } else if (word == RESPONSE_ACK_OK) {
                mMessageBuffer.clear();
            } else if (word == RESPONSE_ACK_ERROR) {
                Log.w(TAG, "Received ERROR command response from sensor.");
                mMessageBuffer.clear();
            } else {
                // Ignore bytes.  Empty buffer.
                Log.w(TAG, "Ignoring unexpected bytes from sensor.");
                mMessageBuffer.clear();
            }
        }
        if (mState == STATE_READING_DATA_FRAME) {
            if (mMessageBuffer.position() >= LENGTH_DATA_FRAME) {
                int extraBytes = mMessageBuffer.position() - LENGTH_DATA_FRAME;
                byte[] dataframe = new byte[LENGTH_DATA_FRAME];
                mMessageBuffer.position(0);
                mMessageBuffer.get(dataframe, 0, dataframe.length);
                processDataFrame(dataframe);
                mMessageBuffer.position(LENGTH_DATA_FRAME);
                mMessageBuffer.compact();
                mMessageBuffer.position(extraBytes);
                mState = STATE_IDLE;
            }
        }
    }

    void processDataFrame(byte[] dataframe) {
        if (DEBUG) Log.d(TAG, "dataframe: " + bytesToHex(dataframe));

        int checksum = (Byte.toUnsignedInt(dataframe[30]) << 8) + Byte.toUnsignedInt(dataframe[31]);
        int calculatedChecksum = 0;
        for (int i = 0; i < 30; i++) {
            calculatedChecksum += Byte.toUnsignedInt(dataframe[i]);
        }
        if (checksum != calculatedChecksum) {
            Log.e(TAG, "Checksum error in data frame.  Ignoring.");
            return;
        }
        // Assign PM2.5 and PM10 values
        mPm25 = (Byte.toUnsignedInt(dataframe[6]) << 8) + Byte.toUnsignedInt(dataframe[7]);
        mPm10 = (Byte.toUnsignedInt(dataframe[8]) << 8) + Byte.toUnsignedInt(dataframe[9]);

        // Clear exception
        mLastException = null;
    }

    public int readPm25() throws IOException {
        if (mLastException != null) throw mLastException;
        return mPm25;
    };

    public int readPm10() throws IOException {
        if (mLastException != null) throw mLastException;
        return mPm10;
    };
}
