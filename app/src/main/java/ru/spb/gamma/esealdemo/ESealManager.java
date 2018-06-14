package ru.spb.gamma.esealdemo;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;

public  class ESealManager {
    private final Object mLock = new Object();
    private final Context mContext;
    final Handler mHandler;
    private boolean mConnected;
    private int mConnectionState = BluetoothGatt.STATE_DISCONNECTED;
    protected EsealGattCallback mCallbacks;


    public ESealManager(@NonNull final Context context) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
    }

    @NonNull
    @SuppressLint("NewApi")
    public ConnectRequest connect(@NonNull final BluetoothDevice device) {
        return connect(device, 1 /* BluetoothDevice.PHY_LE_1M */);
    }

    /**
     * Connects to the Bluetooth Smart device.
     * <p>
     * This method returns the {@link ConnectRequest} which can be used to set completion
     * and failure callbacks. The completion callback will be called after the initialization
     * is complete, after {@link BleManagerCallbacks#onDeviceReady(BluetoothDevice)} has been
     * called.
     * <p>
     * Calling {@link ConnectRequest#await(int)} will make this request
     * synchronous (the callbacks set will be ignored, instead the synchronous method will
     * return or throw an exception).
     *
     * @param device       a device to connect to.
     * @param preferredPhy preferred PHY used in connection. Different PHY is available
     *                     on supported devices running Android Oreo or newer.
     * @return The connect request with allows to set a completion and failure callbacks.
     */
    @NonNull
    @RequiresApi(api = Build.VERSION_CODES.O)
    public ConnectRequest connect(@NonNull final BluetoothDevice device, final int preferredPhy) {
        if (mCallbacks == null) {
            throw new NullPointerException("You have to set callbacks using setGattCallbacks(E callbacks) before connecting");
        }
        if (mConnected || mConnectionState == BluetoothGatt.STATE_CONNECTING) {
            final ConnectRequest request = Request.connect();
            mHandler.post(() -> {
                final BluetoothDevice currentDevice = mBluetoothDevice;
                if (currentDevice != null && currentDevice.equals(device)) {
                    request.notifySuccess(device);
                } else {
                    request.notifyFail(device, FailCallback.REASON_REQUEST_FAILED);
                }
            });
            return request;
        }

        mConnectRequest = Request.connect();
        mConnectionState = BluetoothGatt.STATE_CONNECTING;
        runOnUiThread(() -> {
            synchronized (mLock) {
                if (mBluetoothGatt != null) {
                    // There are 2 ways of reconnecting to the same device:
                    // 1. Reusing the same BluetoothGatt object and calling connect() - this will force
                    //    the autoConnect flag to true
                    // 2. Closing it and reopening a new instance of BluetoothGatt object.
                    // The gatt.close() is an asynchronous method. It requires some time before it's
                    // finished and device.connectGatt(...) can't be called immediately or service
                    // discovery may never finish on some older devices (Nexus 4, Android 5.0.1).
                    // If shouldAutoConnect() method returned false we can't call gatt.connect() and
                    // have to close gatt and open it again.
                    if (!mInitialConnection) {
                        log(Level.DEBUG, "gatt.close()");
                        mBluetoothGatt.close();
                        mBluetoothGatt = null;
                        try {
                            log(Level.DEBUG, "wait(200)");
                            Thread.sleep(200); // Is 200 ms enough?
                        } catch (final InterruptedException e) {
                            // Ignore
                        }
                    } else {
                        // Instead, the gatt.connect() method will be used to reconnect to the same device.
                        // This method forces autoConnect = true even if the gatt was created with this
                        // flag set to false.
                        mInitialConnection = false;
                        log(Level.VERBOSE, "Connecting...");
                        mCallbacks.onDeviceConnecting(device);
                        log(Level.DEBUG, "gatt.connect()");
                        mBluetoothGatt.connect();
                        return;
                    }
                } else {
                    // Register bonding broadcast receiver
                    mContext.registerReceiver(mBluetoothStateBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
                    mContext.registerReceiver(mBondingBroadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
                    mContext.registerReceiver(mPairingRequestBroadcastReceiver, new IntentFilter("android.bluetooth.device.action.PAIRING_REQUEST"/*BluetoothDevice.ACTION_PAIRING_REQUEST*/));
                }
            }

            final boolean shouldAutoConnect = shouldAutoConnect();
            // We will receive Link Loss events only when the device is connected with autoConnect=true
            mUserDisconnected = !shouldAutoConnect;
            // The first connection will always be done with autoConnect = false to make the connection quick.
            // If the shouldAutoConnect() method returned true, the manager will automatically try to
            // reconnect to this device on link loss.
            if (shouldAutoConnect) {
                mInitialConnection = true;
            }
            mBluetoothDevice = device;
            mGattCallback = getGattCallback();
            mGattCallback.setHandler(mHandler);
            log(Level.VERBOSE, "Connecting...");
            mCallbacks.onDeviceConnecting(device);
            log(Level.DEBUG, "gatt = device.connectGatt(autoConnect = false)");
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback,
                        BluetoothDevice.TRANSPORT_LE, preferredPhy, mHandler);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback,
                        BluetoothDevice.TRANSPORT_LE);
            } else {
                mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback);
            }
        });
        return mConnectRequest;
    }

    /**
     * Disconnects from the device or cancels the pending connection attempt.
     * Does nothing if device was not connected.
     *
     * @return The disconnect request. The completion callback will be called after the device
     * has disconnected and the connection was closed. If the device was not connected,
     * the completion callback will be called immediately with device parameter set to null.
     */
    @NonNull
    public DisconnectRequest disconnect() {
        mUserDisconnected = true;
        mInitialConnection = false;

        final DisconnectRequest request = Request.disconnect();
        if (mBluetoothGatt != null) {
            mRequest = request;
            mConnectionState = BluetoothGatt.STATE_DISCONNECTING;
            log(Level.VERBOSE, mConnected ? "Disconnecting..." : "Cancelling connection...");
            runOnUiThread(() -> mCallbacks.onDeviceDisconnecting(mBluetoothGatt.getDevice()));
            final boolean wasConnected = mConnected;
            log(Level.DEBUG, "gatt.disconnect()");
            runOnUiThread(() -> mBluetoothGatt.disconnect());

            if (!wasConnected) {
                // There will be no callback, the connection attempt will be stopped
                mConnectionState = BluetoothGatt.STATE_DISCONNECTED;
                log(Level.INFO, "Disconnected");
                runOnUiThread(() -> mCallbacks.onDeviceDisconnected(mBluetoothGatt.getDevice()));
            }
        } else {
            mHandler.post(() -> request.notifySuccess(mBluetoothDevice));
        }
        return request;
    }

    /**
     * Returns the Bluetooth device object used in {@link #connect(BluetoothDevice)}.
     *
     * @return The Bluetooth device or null, if {@link #connect(BluetoothDevice)} wasn't called.
     */
    public BluetoothDevice getBluetoothDevice() {
        return mBluetoothDevice;
    }

    /**
     * This method returns true if the device is connected. Services could have not been
     * discovered yet.
     */
    public boolean isConnected() {
        return mConnected;
    }

    /**
     * Method returns the connection state:
     * {@link BluetoothGatt#STATE_CONNECTING STATE_CONNECTING},
     * {@link BluetoothGatt#STATE_CONNECTED STATE_CONNECTED},
     * {@link BluetoothGatt#STATE_DISCONNECTING STATE_DISCONNECTING},
     * {@link BluetoothGatt#STATE_DISCONNECTED STATE_DISCONNECTED}
     *
     * @return The connection state.
     */
    public int getConnectionState() {
        return mConnectionState;
    }



}
