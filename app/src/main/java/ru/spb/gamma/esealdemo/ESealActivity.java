package ru.spb.gamma.esealdemo;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.content.Intent;
import android.os.Build;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

public class ESealActivity extends AppCompatActivity implements BleManagerCallbacks {

    private BluetoothGatt mBluetoothGatt;
    private EsealGattCallback mGattCallback;
    private BluetoothDevice mBluetoothDevice;
    private ESealManager mEsealManager;


    private TextView mDeviceNameView;
    private Button mConnectButton;
//    private ILogSession mLogSession;

    private boolean mDeviceConnected = false;
    private String mDeviceName;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eseal);
        Toolbar myChildToolbar =
                (Toolbar) findViewById(R.id.eseal_toolbar);
        setSupportActionBar(myChildToolbar);

        // Get a support ActionBar corresponding to this toolbar
        ActionBar ab = getSupportActionBar();
        ab.setTitle("");

        // Enable the Up button
        ab.setDisplayHomeAsUpEnabled(true);
        Intent intent = getIntent();
        BluetoothDevice device = (BluetoothDevice) intent.getParcelableExtra("BLE_DEVICE");
        if ( device != null ) {
            ab.setTitle(device.getName());
            mBluetoothDevice = device;
            mGattCallback = new EsealGattCallback();
            mBluetoothGatt = device.connectGatt(this, false, mGattCallback);
        }
    }
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.eseal, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // action with ID action_refresh was selected
            case R.id.action_eseal_disconnect:
                mGattCallback
                return true;
            // action with ID action_settings was selected
            default:
                return super.onOptionsItemSelected(item);
        }

    }

    @Override
    public void onDeviceConnecting(final BluetoothDevice device) {
        runOnUiThread(() -> {
            mDeviceNameView.setText(mDeviceName != null ? mDeviceName : getString(R.string.not_available));
            mConnectButton.setText(R.string.action_connecting);
        });
    }

    @Override
    public void onDeviceConnected(final BluetoothDevice device) {
        mDeviceConnected = true;
        runOnUiThread(() -> mConnectButton.setText(R.string.action_disconnect));
    }

    @Override
    public void onDeviceDisconnecting(final BluetoothDevice device) {
        runOnUiThread(() -> mConnectButton.setText(R.string.action_disconnecting));
    }

    @Override
    public void onDeviceDisconnected(final BluetoothDevice device) {
        mDeviceConnected = false;
        mBleManager.close();
        runOnUiThread(() -> {
            mConnectButton.setText(R.string.action_connect);
            mDeviceNameView.setText(getDefaultDeviceName());
        });
    }

    @Override
    public void onLinkLossOccurred(final BluetoothDevice device) {
        mDeviceConnected = false;
    }

    @Override
    public void onServicesDiscovered(final BluetoothDevice device, boolean optionalServicesFound) {
        // this may notify user or show some views
    }

    @Override
    public void onDeviceReady(final BluetoothDevice device) {
        // empty default implementation
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
    public void onBondingFailed(final BluetoothDevice device) {
        showToast(R.string.bonding_failed);
    }

    @Override
    public void onError(final BluetoothDevice device, final String message, final int errorCode) {
        DebugLogger.e(TAG, "Error occurred: " + message + ",  error code: " + errorCode);
        showToast(message + " (" + errorCode + ")");
    }

    @Override
    public void onDeviceNotSupported(final BluetoothDevice device) {
        showToast(R.string.not_supported);
    }


}
