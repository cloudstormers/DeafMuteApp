package telocate.de.deafmuteapp;

import android.Manifest;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;



import telocate.de.deafmuteapp.threads.InsecureConnectThread;
import telocate.de.deafmuteapp.threads.ReceiveThread;


/* Very useful app that was of great help for developing this project is official Android BluetoothChat app. */

public class MainActivity extends AppCompatActivity {



    // TODO: in future make this dynamic. For example he is looking for devices that have some prefix. The first one he finds he connects to e.g. ...
    /** Server is bound to looking for this device! */
    private static final String CLIENT_BLUETOOTH_NAME = "PersonWithoutDisability";

    /** Necessary permission for bluetooth devices discovery process. */
    private static final int RC_PERMISSION_ACCESS_COURSE_LOCATION = 23;


    /* Server threads */

    /** 1. Thread used for obtaining connection with device. */
    InsecureConnectThread mInsecureConnectThread;
    /** 2. Thread used for receiving data from the device, started upon successful connection. */
    ReceiveThread mReceiveThread;

    private BluetoothAdapter mBluetoothModule = null;
    public BluetoothSocket mBluetoothSocket;
    Context context = this;

    /** Flag upon which we decide should we start discovery process or not. */
    boolean shouldDoDiscovery = true;

    /** Holds connected device name. */
    String mConnectedDevice;

    /**
     * The Handler that gets information back from the InsecureConnectThread and from ReceiveThread.
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case Constants.CONNECTION_MADE: // Called from InsecureConnectThread.
                    /* Save the connected device's name. */
                    mConnectedDevice = msg.getData().getString(Constants.DEVICE_NAME);
                    Toast.makeText(context, "Connected to: " + mConnectedDevice, Toast.LENGTH_SHORT).show();
                    /* Obtain socket acquired by mInsecureConnectThread. */
                    mBluetoothSocket = (BluetoothSocket) msg.obj;
                    mInsecureConnectThread = null;
                    /* Spawn communication thread. */
                    mReceiveThread = new ReceiveThread(context, mBluetoothSocket, mHandler);
                    mReceiveThread.start();
                    break;

                case Constants.CONNECTION_LOST: // Called from both worker threads.
                    Toast.makeText(context, msg.getData().getString(Constants.TOAST), Toast.LENGTH_SHORT).show();
                    stopAllWorkerThreads();
                    shouldDoDiscovery = true;
                    doDiscovery();
                    break;

                case Constants.MESSAGE_TOAST:
                    Toast.makeText(MainActivity.this, msg.getData().getString(Constants.TOAST), Toast.LENGTH_SHORT).show();
                    break;

                case Constants.TEXT_MESSAGE:
                    String message = msg.getData().getString(Constants.TEXT);
                    textFromNormal.append(message + "\n\n");
                    break;

            }
        }
    };

    TextView textFromNormal;


    //---------------------------------------METHODS------------------------------------------------


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textFromNormal = (TextView) findViewById(R.id.textFromNormal);
        textFromNormal.setText("");

        // Get local Bluetooth adapter.
        mBluetoothModule = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothModule == null) {
            // If the adapter is null, then Bluetooth is not supported.
            Toast.makeText(context, getString(R.string.bt_not_available_message), Toast.LENGTH_LONG).show();
            finish();
        } else {
            // Else device has bluetooth module.
            if (!mBluetoothModule.isEnabled()) {
                // If bluetooth is not enabled enable it.
                Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivity(enableIntent);
            } else {
                // Else it is already enabled.
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                    requestAccessCoarseLocationPermission();
                } else {
                    doDiscovery();
                }
            }
        }
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {

            final String action = intent.getAction();

            if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
                final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
                switch (state) {
                    case BluetoothAdapter.STATE_ON:
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                            requestAccessCoarseLocationPermission();
                        } else {
                            doDiscovery();
                        }
                        break;
                    default:
                        break;
                }
            } else if (action.equals(BluetoothDevice.ACTION_FOUND)) {
                /* Get the BluetoothDevice object from the Intent. */
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device != null && device.getName() != null && device.getName().equals(CLIENT_BLUETOOTH_NAME)) {
                    Toast.makeText(context, "Found client: " + CLIENT_BLUETOOTH_NAME, Toast.LENGTH_SHORT).show();
                    /* If we're already discovering, stop it. This needs to be done because the discovery take to much resources. */
                    shouldDoDiscovery = false;
                    stopDiscovery();
                    /* Connect to found device. */
                    startInsecureConnectingThread(device);
                }
            } else if (action.equals(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)) {
                if (shouldDoDiscovery) {
                    doDiscovery();
                }
            }

        }
    };

    private void requestAccessCoarseLocationPermission() {
        // Should we show an explanation?
        if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_COARSE_LOCATION)) {
            new AlertDialog.Builder(this)
                    .setTitle("Inform and request")
                    .setMessage("You need to enable permissions for device discovery")
                    .setPositiveButton("ok", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, RC_PERMISSION_ACCESS_COURSE_LOCATION);
                        }
                    })
                    .show();
        } else {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, RC_PERMISSION_ACCESS_COURSE_LOCATION);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,  String permissions[], int[] grantResults) {
        switch (requestCode) {
            case RC_PERMISSION_ACCESS_COURSE_LOCATION: {
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    doDiscovery();
                } else {
                    Toast.makeText(context, "Permission not granted, exiting.", Toast.LENGTH_SHORT).show();
                    finish();
                }
                break;
            }
        }
    }

    private void startInsecureConnectingThread(BluetoothDevice device) {
        mInsecureConnectThread = new InsecureConnectThread(device, mHandler);
        mInsecureConnectThread.start();
    }

    private void stopDiscovery() {
        if (mBluetoothModule.isDiscovering()) {
            mBluetoothModule.cancelDiscovery();
        }
    }

    private void doDiscovery() {
        Toast.makeText(this, "Searching for person without disabilities.", Toast.LENGTH_SHORT).show();
        /* If we're already discovering, stop it. */
        if (mBluetoothModule.isDiscovering()) {
            mBluetoothModule.cancelDiscovery();
        }
        /* Request discover from BluetoothAdapter. */
        mBluetoothModule.startDiscovery();
        //Toast.makeText(context, "Discovery started.", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        /* Register broadcast receiver. */
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mReceiver, filter);
    }


    @Override
    protected void onStop() {
        super.onStop();
        unregisterReceiver(mReceiver);
    }

    private void stopAllWorkerThreads() {

        if (mInsecureConnectThread != null) {
            mInsecureConnectThread = null;
        }

        if (mReceiveThread != null) {
            mReceiveThread = null;
        }
    }


}