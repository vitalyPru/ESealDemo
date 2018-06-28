package ru.spb.gamma.esealdemo;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.v4.util.Pair;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.Toolbar;

import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.google.android.gms.common.api.CommonStatusCodes;

import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;

public class ESealActivity extends AppCompatActivity implements ESealManagerCallbacks , LogFragment.OnListFragmentInteractionListener {

    private static final String SIS_CONNECTION_STATUS = "connection_status";
    private static final String SIS_OPEN_STATUS = "device_open_status";
    private static final String SIS_DEVICE = "device";
    private ESealManager mEsealManager;
    private boolean mDeviceConnected = false;
    private boolean mDeviceOpened = false;
    private BluetoothDevice mDevice;
    private View mLayout;
    private final Queue<String> mSendQueue = new LinkedList<>();
    private MenuItem mWaitingMenuItem;
    private static final int RC_WIRE_CAPTURE = 9003;
    private static final int RC_DOC_CAPTURE = 9004;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_eseal);
        Toolbar myChildToolbar =
                (Toolbar) findViewById(R.id.eseal_toolbar);
        setSupportActionBar(myChildToolbar);
        mLayout =  findViewById(R.id.eseal_data_layout);
        // Get a support ActionBar corresponding to this toolbar
        ActionBar ab = getSupportActionBar();
        ab.setDisplayOptions(ActionBar.DISPLAY_SHOW_HOME
                | ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);
        // Enable the Up button
        ab.setDisplayHomeAsUpEnabled(true);

        findViewById(R.id.arm_button).setOnClickListener(v -> {
            arm();
            send_next();

        });

        findViewById(R.id.disarm_button).setOnClickListener(v -> {
            disarm();
            send_next();

        });

        findViewById(R.id.set_wire_button).setOnClickListener(v -> {
            set_wire();
            send_next();

        });

        findViewById(R.id.set_doc_button).setOnClickListener(v -> {
            set_doc();
            send_next();
        });

        findViewById(R.id.wire_camera_button).setOnClickListener(v -> {
            Intent intent = new Intent(this, CameraActivity.class);
            intent.putExtra(CameraActivity.AutoFocus, true);
            intent.putExtra(CameraActivity.UseFlash, false);
            intent.putExtra(CameraActivity.BarCodeRecognition, false);

            startActivityForResult(intent, RC_WIRE_CAPTURE);
        });

        findViewById(R.id.doc_camera_button).setOnClickListener(v -> {
            Intent intent = new Intent(this, CameraActivity.class);
            intent.putExtra(CameraActivity.AutoFocus, true);
            intent.putExtra(CameraActivity.UseFlash, false);
            intent.putExtra(CameraActivity.BarCodeRecognition, true);

            startActivityForResult(intent, RC_DOC_CAPTURE);
        });

        mEsealManager = ESealManager.getInstance(getApplicationContext());
        mEsealManager.setGattCallbacks(this);
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
        ab.setSubtitle(mDevice.getAddress());
        if (!mDeviceConnected) {
            if (mDevice != null) {
                mEsealManager.connect(mDevice);
            }
        }
        if (mDeviceOpened) {
            mLayout.setVisibility(View.VISIBLE);
            prepare_all_data_request();
            send_next();
        } else {
            mLayout.setVisibility(View.INVISIBLE);
        }
    }

    private boolean send_next() {
        String e = mSendQueue.poll();
        if ( e != null ) {
            mEsealManager.send(e);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String currentDateandTime = sdf.format(new Date());
            LogContent.ITEMS.add(new LogContent.LogItem(currentDateandTime, e ,0));
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
                prepare_all_data_request();
                send_next();
                return true;
            case R.id.action_show_log:
                showLog();
                return true;
            case R.id.action_clear_flags:
                mSendQueue.offer("clear\r\n");
                send_next();
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
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if(requestCode == RC_WIRE_CAPTURE) {
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (data != null) {
                    String text = data.getStringExtra(CameraActivity.TextBlockObject);
//                    statusMessage.setText(R.string.ocr_success);
                    ((EditText)findViewById(R.id.wire_id)).setText(text);
                    Log.d("OCR", "Text read: " + text);
                } else {
//                    statusMessage.setText(R.string.ocr_failure);
                    Log.d("OCR", "No Text captured, intent data is null");
                }
            } else {
//                statusMessage.setText(String.format(getString(R.string.ocr_error),
//                        CommonStatusCodes.getStatusCodeString(resultCode)));
            }
        } else if(requestCode == RC_DOC_CAPTURE) {
            if (resultCode == CommonStatusCodes.SUCCESS) {
                if (data != null) {
                    String text = data.getStringExtra(CameraActivity.TextBlockObject);
                    ((EditText)findViewById(R.id.doc_id)).setText(text);
                    Log.d("OCR", "Text read: " + text);
                } else {
//                    statusMessage.setText(R.string.ocr_failure);
                    Log.d("OCR", "No Text captured, intent data is null");
                }
            } else {
//                statusMessage.setText(String.format(getString(R.string.ocr_error),
//                        CommonStatusCodes.getStatusCodeString(resultCode)));
            }
        }
        else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
    @Override
    public void onDataReceived(final BluetoothDevice device, final String data) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String currentDateandTime = sdf.format(new Date());
        LogContent.ITEMS.add(new LogContent.LogItem(currentDateandTime, data, 1));
        if(mDeviceOpened) {
            Queue<Pair<DispItem,String>> parsed_commands = ParserUtils.parse_data(data);
            for( Pair pair : parsed_commands ) {
                DispItem disp_item = (DispItem) pair.first;
                disp_item.display_data(this, (String) pair.second);
            }
        }

        if("getPair=125\r\n".equals(data)){
            runOnUiThread(() -> {
                mLayout.setVisibility(View.VISIBLE);
                mDeviceOpened = true;
                mWaitingMenuItem.collapseActionView();
                mWaitingMenuItem.setActionView(null);
            });
            prepare_all_data_request();
            send_next();
        }

    }

    private void prepare_all_data_request() {
        mSendQueue.offer("getInfo\r\n");
        mSendQueue.offer("getParams\r\n");
        mSendQueue.offer("getInvoice\r\n");
        mSendQueue.offer("getID\r\n");

    }

    private void arm() {
        String text_wire = ((EditText)findViewById(R.id.wire_id)).getText().toString();
        text_wire = text_wire.replaceAll("\\s+","");
        ((EditText)findViewById(R.id.wire_id)).setText(text_wire);
        String text_doc = ((EditText)findViewById(R.id.doc_id)).getText().toString();
        text_doc = text_doc.replaceAll("\\s+","");
        ((EditText)findViewById(R.id.doc_id)).setText(text_doc);
        mSendQueue.offer("setID="+text_wire+"\rsetInvoice=" + text_doc + "\rarm\r\n");

    }

    private void disarm(){
        mSendQueue.offer("disarm\r\n");
    }

    private void set_wire() {
        String text = ((EditText)findViewById(R.id.wire_id)).getText().toString();
        text = text.replaceAll("\\s+","");
        ((EditText)findViewById(R.id.wire_id)).setText(text);
        mSendQueue.offer("setID="+text+"\r\n");
    }

    private void set_doc() {
        String text = ((EditText)findViewById(R.id.doc_id)).getText().toString();
        text = text.replaceAll("\\s+","");
        ((EditText)findViewById(R.id.doc_id)).setText(text);
        mSendQueue.offer("setInvoice="+((EditText)findViewById(R.id.doc_id)).getText()+"\r\n");
    }


    protected void showLog() {
        runOnUiThread(() -> {
            final LogFragment dialog = LogFragment.newInstance(1);
            dialog.show(getSupportFragmentManager(), "scan_fragment");
        });
    }


    @Override
    public void onDataSent(final BluetoothDevice device, final String data) {
        send_next();
    }
    @Override
    public void onDeviceConnecting(final BluetoothDevice device) {
    }

    @Override
    public void onDeviceConnected(final BluetoothDevice device) {
        mDeviceConnected = true;
    }

    @Override
    public void onDeviceDisconnecting(final BluetoothDevice device) {
    }

    @Override
    public void onDeviceDisconnected(final BluetoothDevice device) {
        mDeviceConnected = false;
        mEsealManager.close();
    }

    @Override
    public void onLinkLossOccurred(final BluetoothDevice device) {
        mDeviceConnected = false;
    }

    @Override
    public void onError(final BluetoothDevice device, final String message, final int errorCode) {
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


    @Override
    public void onListFragmentInteraction(LogContent.LogItem item) {

    }
}
