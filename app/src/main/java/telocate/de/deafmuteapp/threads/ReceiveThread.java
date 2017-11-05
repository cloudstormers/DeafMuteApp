package telocate.de.deafmuteapp.threads;

import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import telocate.de.deafmuteapp.Constants;


/**
 * Created by aleks on 8/8/2017.
 */

public class ReceiveThread extends Thread {

    private static final String RECEIVING_THREAD_NAME = "ServerReceivingThread";

    private static final String TAG = "ServerReceivingThread";

    /** Thread inner state. */
    private int mState;
    // Constants that indicate the current connection state.
    public static final int STATE_NONE = 0;       // we're doing nothing
    public static final int STATE_CONNECTED = 1;  // now connected to a remote device

    private final BluetoothSocket mBluetoothSocket;
    private final InputStream mmInStream;
    private final OutputStream mmOutStream;

    private Handler mHandler;

    Context mContext;



    public ReceiveThread(Context context, BluetoothSocket bluetoothSocket, Handler handler) {

        mContext = context;
        mHandler = handler;
        mBluetoothSocket = bluetoothSocket;
        InputStream tmpIn = null;
        OutputStream tmpOut = null;

        // Get the BluetoothSocket input and output streams
        try {
            tmpIn = bluetoothSocket.getInputStream();
            tmpOut = bluetoothSocket.getOutputStream();
        } catch (IOException e) {
            sendToastMessage("ReceiveThread: this should never happen!");
        }

        mmInStream = tmpIn;
        mmOutStream = tmpOut;
        mState = STATE_CONNECTED;
    }

    public void run() {

        setName(RECEIVING_THREAD_NAME);

        byte[] buffer = new byte[1024];
        int bytes = 0;

        // Keep listening to the InputStream while connected
        while (mState == STATE_CONNECTED) {

            // Read from the InputStream
            try {
                bytes = mmInStream.read(buffer);
            } catch (IOException e) {
                connectionLost();
                e.printStackTrace();
            }
            displayMessage(new String(buffer, 0, bytes));
        }
    }

    /**
     * Indicate that the connection was lost and notify the UI Activity.
     */
    private void connectionLost() {
        // This stops the thread and cleans everything up.
        cleanUp();
        // Send message that connection error occurred.
        notifyConnectionLost();
    }

    private void notifyConnectionLost() {
        Message msg = mHandler.obtainMessage(Constants.CONNECTION_LOST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, "ReceiveSendThread: Device connection was lost.");
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    public void cleanUp() {
        try {
            mState = STATE_NONE;
            mBluetoothSocket.close();
        } catch (IOException e) {
            Log.e(TAG, "close() of connect socket failed", e);
        }
    }


    private void sendToastMessage(String toast) {
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, toast);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }

    private void displayMessage(String message) {
        Message msg = mHandler.obtainMessage(Constants.TEXT_MESSAGE);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TEXT, message);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
    }


}
