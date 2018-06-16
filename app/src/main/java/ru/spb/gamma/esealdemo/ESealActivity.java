package ru.spb.gamma.esealdemo;

import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;

import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import java.util.LinkedList;
import java.util.Queue;

public class ESealActivity extends AppCompatActivity implements ESealManagerCallbacks {

    private static final String SIS_CONNECTION_STATUS = "connection_status";
    private static final String SIS_OPEN_STATUS = "device_open_status";
    private static final String SIS_DEVICE = "device";
    private ESealManager mEsealManager;
    private boolean mDeviceConnected = false;
    private boolean mDeviceOpened = false;
    private BluetoothDevice mDevice;
    private View mLayout;
    private TextView mEsealInfo;
    private final Queue<String> mSendQueue = new LinkedList<>();
    private MenuItem mWaitingMenuItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eseal);
        Toolbar myChildToolbar =
                (Toolbar) findViewById(R.id.eseal_toolbar);
        setSupportActionBar(myChildToolbar);
        mLayout =  findViewById(R.id.eseal_data_layout);
        mEsealInfo = findViewById(R.id.board_prompt);
        // Get a support ActionBar corresponding to this toolbar
        ActionBar ab = getSupportActionBar();
        ab.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME
                | ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);

        //ab.setTitle("");

        // Enable the Up button
        ab.setDisplayHomeAsUpEnabled(true);


        mEsealManager = ESealManager.getInstance(getApplicationContext());
        mEsealManager.setGattCallbacks(this);
        // The onCreateView class should... create the view
//        onCreateView(savedInstanceState);
        if(savedInstanceState == null) {
            Intent intent = getIntent();
            mDevice = (BluetoothDevice) intent.getParcelableExtra("BLE_DEVICE");
            mDeviceOpened = false;
            mDeviceConnected = false;
        } else {
            mDeviceConnected = savedInstanceState.getBoolean(SIS_CONNECTION_STATUS);
            mDeviceOpened = savedInstanceState.getBoolean(SIS_OPEN_STATUS);
            mDevice = savedInstanceState.getParcelable(SIS_DEVICE);
        }
        if (!mDeviceConnected) {
            if (mDevice != null) {
                mEsealManager.connect(mDevice);
            }
        }
        if (mDeviceOpened) {
            mLayout.setVisibility(View.VISIBLE);
            mSendQueue.offer("getInfo\r\n");
            mSendQueue.offer("getParams\r\n");
            send_next();
        } else {
            mLayout.setVisibility(View.INVISIBLE);
        }
    }

    private boolean send_next() {
        String e = mSendQueue.poll();
        if ( e != null ) {
            mEsealManager.send(e);
            return true;
        }
        return false;
    }

    @Override
    protected void onSaveInstanceState(final Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SIS_CONNECTION_STATUS, mDeviceConnected);
        outState.putBoolean(SIS_OPEN_STATUS, mDeviceOpened);
        if(mDevice != null) {
            outState.putParcelable(SIS_DEVICE, mDevice);
        }
    }

    @Override
    protected void onRestoreInstanceState(@NonNull final Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.eseal, menu);
        mWaitingMenuItem = menu.findItem(R.id.action_eseal_waiting);
        if(!mDeviceOpened) {
            mWaitingMenuItem.setActionView(R.layout.waiting_view);
            mWaitingMenuItem.expandActionView();
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // action with ID action_refresh was selected
            case android.R.id.home:
                onBackPressed();
                return true;
            case R.id.action_eseal_waiting:
//                mEsealManager.disconnect();
//                mWaitingMenuItem = item;

                return true;
            // action with ID action_settings was selected
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    @Override
    public void onBackPressed() {
        mEsealManager.disconnect();
        super.onBackPressed();
    }


    @Override
    public void onDataReceived(final BluetoothDevice device, final String data) {
        if(mDeviceOpened) {
            Queue<Pair<Integer,String>> parsed_commands = ParserUtils.parse_data(data);
            for( Pair pair : parsed_commands ) {
                TextView control = findViewById((int)pair.first);
                runOnUiThread(() -> {
                    control.setText((String)pair.second);
                });
            }
        }

        if("getPair=125\r\n".equals(data)){
            runOnUiThread(() -> {
                mLayout.setVisibility(View.VISIBLE);
                mDeviceOpened = true;
                mWaitingMenuItem.collapseActionView();
                mWaitingMenuItem.setActionView(null);
            });
            mSendQueue.offer("getInfo\r\n");
            mSendQueue.offer("getParams\r\n");
            send_next();
        }

    }

    @Override
    public void onDataSent(final BluetoothDevice device, final String data) {
//        final Intent broadcast = new Intent(BROADCAST_UART_TX);
//        broadcast.putExtra(EXTRA_DEVICE, getBluetoothDevice());
//        broadcast.putExtra(EXTRA_DATA, data);
//        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast);
//        mEsealManager.send("getParams\r\n");
        send_next();
    }
    @Override
    public void onDeviceConnecting(final BluetoothDevice device) {
//        runOnUiThread(() -> {
//            mDeviceNameView.setText(mDeviceName != null ? mDeviceName : getString(R.string.not_available));
//            mConnectButton.setText(R.string.action_connecting);
//        });
    }

    @Override
    public void onDeviceConnected(final BluetoothDevice device) {
        mDeviceConnected = true;
 //       runOnUiThread(() -> mConnectButton.setText(R.string.action_disconnect));
    }

    @Override
    public void onDeviceDisconnecting(final BluetoothDevice device) {
 //       runOnUiThread(() -> mConnectButton.setText(R.string.action_disconnecting));
    }

    @Override
    public void onDeviceDisconnected(final BluetoothDevice device) {
        mDeviceConnected = false;
        mEsealManager.close();
//        runOnUiThread(() -> {
//            mConnectButton.setText(R.string.action_connect);
 //           mDeviceNameView.setText(getDefaultDeviceName());
 //           mBatteryLevelView.setText(R.string.not_available);
//        });
    }

    @Override
    public void onLinkLossOccurred(final BluetoothDevice device) {
        mDeviceConnected = false;
//       runOnUiThread(() -> {
//            if (mBatteryLevelView != null)
//                mBatteryLevelView.setText(R.string.not_available);
//        });
    }
@Override
public void onError(final BluetoothDevice device, final String message, final int errorCode) {
//    DebugLogger.e(TAG, "Error occurred: " + message + ",  error code: " + errorCode);
    showToast(message + " (" + errorCode + ")");
}

    @Override
    public void onDeviceNotSupported(final BluetoothDevice device) {
        showToast(R.string.not_supported);
    }

    @Override
    public void onBondingRequired(final BluetoothDevice device) {
        showToast(R.string.bonding);
    }

    @Override
    public void onBonded(final BluetoothDevice device) {
        showToast(R.string.bonded);
    }

    @Override
    public void onServicesDiscovered(final BluetoothDevice device, boolean optionalServicesFound) {
        // this may notify user or show some views
    }

    @Override
    public void onDeviceReady(final BluetoothDevice device) {
        // empty default implementation
        mEsealManager.send("getPair\r\n");
    }

    /**
     * Shows a message as a Toast notification. This method is thread safe, you can call it from any thread
     *
     * @param message a message to be shown
     */
    protected void showToast(final String message) {
        runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show());
    }

    /**
     * Shows a message as a Toast notification. This method is thread safe, you can call it from any thread
     *
     * @param messageResId an resource id of the message to be shown
     */
    protected void showToast(final int messageResId) {
        runOnUiThread(() -> Toast.makeText(this, messageResId, Toast.LENGTH_SHORT).show());
    }


}
