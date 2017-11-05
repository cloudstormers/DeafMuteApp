package telocate.de.deafmuteapp.threads;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;

import java.io.IOException;
import java.util.UUID;

import telocate.de.deafmuteapp.Constants;


/**
 * Created by aleks on 8/8/2017.
 */

public class InsecureConnectThread extends Thread {

    private static final String CONNECTING_THREAD_NAME = "InsecureConnectingThread";

    /** Common UUID for this sever app and client app. */
    private static final UUID MY_UUID_INSECURE = UUID.fromString("0c3d9152-2d18-47ed-abed-f207ec3e31b7");

    /** Device to which we are trying to connect. */
    private final BluetoothDevice mDevice;
    /** Used for communication with the UI thread. */
    private Handler mHandler;
    /** When we successfully connect this instance is passed to ReceiveThread via MainActivity. */
    private final BluetoothSocket mBluetoothSocket;
    boolean failed = false;


    //---------------------------------------METHODS------------------------------------------------


    public InsecureConnectThread(BluetoothDevice device, Handler handler) {
        mDevice = device;
        mHandler = handler;

        BluetoothSocket tmp = null;

        // Get a BluetoothSocket for a connection with the given BluetoothDevice.
        try {
            tmp = mDevice.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
        } catch (IOException e) {
            // Happens when the thread is started with bluetooth off.
            failed = true;
        }

        mBluetoothSocket = tmp;
    }

    public void run() {

        if (!failed) {

            setName(CONNECTING_THREAD_NAME);

            /* This is a blocking call and will only return on a successful connection or an exception. */
            try {
                mBluetoothSocket.connect();
            } catch (IOException e) {
            /* 1. Connection lost. */
                notifyConnectionLost();
                try {
                    mBluetoothSocket.close();
                } catch (IOException e2) {
                    e2.printStackTrace();
                }
                return;
            }

        /* 2. Connection made. */
            connectionMade();
        } else {
            notifyConnectionLost();
        }

    } // End of run().

    private void notifyConnectionLost() {
        Message msg = mHandler.obtainMessage(Constants.CONNECTION_LOST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "InsecureConnectThread: Failed to connect to device.");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    private void connectionMade() {
        final String connectedDeviceName = mDevice.getName();
        Message msg = Message.obtain();
        msg.what = Constants.CONNECTION_MADE;
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, connectedDeviceName);
        msg.setData(bundle);
        msg.obj = mBluetoothSocket;
        mHandler.sendMessage(msg);
    }

} // End of InsecureConnectThread.
