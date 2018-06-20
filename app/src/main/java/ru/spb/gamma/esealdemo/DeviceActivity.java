package ru.spb.gamma.esealdemo;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothManager;
//import android.bluetooth.le.ScanFilter;
//import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.ParcelUuid;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

import no.nordicsemi.android.support.v18.scanner.BluetoothLeScannerCompat;
import no.nordicsemi.android.support.v18.scanner.ScanCallback;
import no.nordicsemi.android.support.v18.scanner.ScanFilter;
import no.nordicsemi.android.support.v18.scanner.ScanResult;
import no.nordicsemi.android.support.v18.scanner.ScanSettings;


public class DeviceActivity extends AppCompatActivity {
    private final static long SCAN_DURATION = 5000;
    private final static int REQUEST_PERMISSION_REQ_CODE = 34; // any 8-bit number
    protected static final int REQUEST_ENABLE_BT = 2;
    private final static String PARAM_UUID = "param_uuid";
//    private View mPermissionRationale;
    private DeviceListAdapter mAdapter;
    private MenuItem mScanButton;
    private boolean mIsScanning = false;
    private final Handler mHandler = new Handler();

//    private ILogSession mLogSession;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothDevice mBluetoothDevice;
//    private BleManagerGattCallback mGattCallback;
//    protected E mCallbacks;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_device);
        Toolbar myToolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(myToolbar);
        ActionBar ab = getSupportActionBar();

        // ensure that Bluetooth exists
        if (!ensureBLEExists())
            finish();

        if (!isBLEEnabled()) {
            showBLEDialog();
        }
        final ListView listview = (ListView) findViewById(R.id.device_list);

        listview.setEmptyView(findViewById(android.R.id.empty));
        listview.setAdapter(mAdapter = new DeviceListAdapter(this));

        listview.setOnItemClickListener((parent, view, position, id) -> {
            stopScan();
//            dialog.dismiss();
            final ExtendedBluetoothDevice d = (ExtendedBluetoothDevice) mAdapter.getItem(position);
/*            mListener.*/ onDeviceSelected(d.device);
        });

//        mPermissionRationale = dialogView.findViewById(R.id.permission_rationale); // this is not null only on API23+

//        mScanButton =  myToolbar.findViewById(R.menu.devices.;
//        mScanButton.setOnClickListener(v -> {
//            if (v.getId() == R.id.action_scan) {
//                if (mIsScanning) {
                    //dialog.cancel();
                    //stopScan();
                //} else {
//                    startScan();
//                }
//            }
//        });

        //addBoundDevices();
//        if (savedInstanceState == null)
//           startScan();

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.devices, menu);
        mScanButton = menu.findItem(R.id.action_scan);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // action with ID action_refresh was selected
            case R.id.action_scan:
//                Toast.makeText(this, "Refresh selected", Toast.LENGTH_SHORT)
//                        .show();
                //            if (v.getId() == R.id.action_scan) {
                if (mIsScanning) {
                //dialog.cancel();
                    stopScan();
                } else {
                    startScan();
                }
//            }

                break;
            // action with ID action_settings was selected
            case R.id.action_about:
                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("ESealDemo");
                builder.setMessage("Версия: 1.0");
                builder.setPositiveButton("OK",       new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog,
                                                int which) {   }});

                AlertDialog alertDialog = builder.create();
                alertDialog.show();
                break;
            default:
                break;
        }

        return true;
    }

    @Override
    public void onRequestPermissionsResult(final int requestCode, final @NonNull String[] permissions, final @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_PERMISSION_REQ_CODE: {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // We have been granted the Manifest.permission.ACCESS_COARSE_LOCATION permission. Now we may proceed with scanning.
                    startScan();
                } else {
//                    mPermissionRationale.setVisibility(View.VISIBLE);
                    Toast.makeText(this, R.string.no_required_permission, Toast.LENGTH_SHORT).show();
                }
                break;
            }
        }
    }

    private boolean ensureBLEExists() {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, R.string.no_ble, Toast.LENGTH_LONG).show();
            return false;
        }
        return true;
    }

    protected boolean isBLEEnabled() {
        final BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        final BluetoothAdapter adapter = bluetoothManager.getAdapter();
        return adapter != null && adapter.isEnabled();
    }

    protected void showBLEDialog() {
        final Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
        startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
    }

    /**
     * Scan for 5 seconds and then stop scanning when a BluetoothLE device is found then mLEScanCallback
     * is activated This will perform regular scan for custom BLE Service UUID and then filter out.
     * using class ScannerServiceParser
     */
    private void startScan() {
        // Since Android 6.0 we need to obtain either Manifest.permission.ACCESS_COARSE_LOCATION or Manifest.permission.ACCESS_FINE_LOCATION to be able to scan for
        // Bluetooth LE devices. This is related to beacons as proximity devices.
        // On API older than Marshmallow the following code does nothing.
        if (ContextCompat.checkSelfPermission(this.getApplicationContext(), Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            this.requestPermissions(new String[]{Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_PERMISSION_REQ_CODE);
            return;
        }

        // Hide the rationale message, we don't need it anymore.
//        if (mPermissionRationale != null)
//            mPermissionRationale.setVisibility(View.GONE);

        mAdapter.clearDevices();
        mScanButton.setTitle(R.string.scanner_action_cancel);

        final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
        final ScanSettings settings = new ScanSettings.Builder()
                .setLegacy(false)
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).setReportDelay(1000).setUseHardwareBatchingIfSupported(false).build();
        final List<ScanFilter> filters = new ArrayList<>();
        UUID uuid =  UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
        final Bundle args = new Bundle();
        args.putParcelable(PARAM_UUID, new ParcelUuid(uuid));
        ParcelUuid mUuid = args.getParcelable(PARAM_UUID);
        filters.add(new ScanFilter.Builder().setServiceUuid(mUuid).build());
        scanner.startScan(filters, settings, scanCallback);

        mIsScanning = true;
        mHandler.postDelayed(() -> {
            if (mIsScanning) {
                stopScan();
            }
        }, SCAN_DURATION);
    }

    /**
     * Stop scan if user tap Cancel button
     */
    private void stopScan() {
        if (mIsScanning) {
            mScanButton.setTitle(R.string.scan);

            final BluetoothLeScannerCompat scanner = BluetoothLeScannerCompat.getScanner();
            scanner.stopScan(scanCallback);

            mIsScanning = false;
        }
    }

    private ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(final int callbackType, final ScanResult result) {
            // do nothing
        }

        @Override
        public void onBatchScanResults(final List<ScanResult> results) {

            mAdapter.update(results);
        }

        @Override
        public void onScanFailed(final int errorCode) {
            // should never be called
        }
    };

    private void onDeviceSelected(BluetoothDevice device) {
        final Intent newIntent = new Intent(this, ESealActivity.class);
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
        newIntent.putExtra("BLE_DEVICE", device);

        // Handle NFC message, if app was opened using NFC AAR record
        startActivity(newIntent);
//        mBluetoothDevice = device;
//        mGattCallback = getGattCallback();
//        mGattCallback.setHandler(mHandler);
//        log(Level.VERBOSE, "Connecting...");
//        mCallbacks.onDeviceConnecting(device);
//        log(Level.DEBUG, "gatt = device.connectGatt(autoConnect = false)");
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//            mBluetoothGatt = device.connectGatt(this, false, /* mGattCallback*/ this,
//                    BluetoothDevice.TRANSPORT_LE, preferredPhy, mHandler);
//        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//            mBluetoothGatt = device.connectGatt(this, false, /*mGattCallback*/ this,
//                    BluetoothDevice.TRANSPORT_LE);
//        } else {
//            mBluetoothGatt = device.connectGatt(this, false, /*mGattCallback*/ this);
//        }

    }
}
