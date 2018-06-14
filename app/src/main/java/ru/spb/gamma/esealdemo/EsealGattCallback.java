package ru.spb.gamma.esealdemo;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothProfile;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.util.Log;

import java.util.logging.Level;

abstract class EsealGattCallback extends BluetoothGattCallback {

    private Handler mHandler;
    private boolean mConnected;
    private int mConnectionState = BluetoothGatt.STATE_DISCONNECTED;


    void setHandler(final Handler handler) {
        mHandler = handler;
    }

    private void runOnUiThread(final Runnable runnable) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            mHandler.post(runnable);
        } else {
            runnable.run();
        }
    }

//    abstract void onConnectionStateChangeSafe(@NonNull final BluetoothGatt gatt, final int status,
//                                              final int newState);
//    @Override
    final void onConnectionStateChangeSafe(@NonNull final BluetoothGatt gatt, final int status, final int newState) {
//        log(Level.DEBUG, "[Callback] Connection state changed with status: " +
//                status + " and new state: " + newState + " (" + stateToString(newState) + ")");

        if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
            // Notify the parent activity/service
//            log(Level.INFO, "Connected to " + gatt.getDevice().getAddress());
            mConnected = true;
            mConnectionState = BluetoothGatt.STATE_CONNECTED;
//            mCallbacks.onDeviceConnected(gatt.getDevice());

            /*
             * The onConnectionStateChange event is triggered just after the Android connects to a device.
             * In case of bonded devices, the encryption is reestablished AFTER this callback is called.
             * Moreover, when the device has Service Changed indication enabled, and the list of services has changed (e.g. using the DFU),
             * the indication is received few hundred milliseconds later, depending on the connection interval.
             * When received, Android will start performing a service discovery operation on its own, internally,
             * and will NOT notify the app that services has changed.
             *
             * If the gatt.discoverServices() method would be invoked here with no delay, if would return cached services,
             * as the SC indication wouldn't be received yet.
             * Therefore we have to postpone the service discovery operation until we are (almost, as there is no such callback) sure,
             * that it has been handled.
             * TODO: Please calculate the proper delay that will work in your solution.
             * It should be greater than the time from LLCP Feature Exchange to ATT Write for Service Change indication.
             * If your device does not use Service Change indication (for example does not have DFU) the delay may be 0.
             */
            final boolean bonded = gatt.getDevice().getBondState() == BluetoothDevice.BOND_BONDED;
            final int delay = bonded ? 1600 : 0; // around 1600 ms is required when connection interval is ~45ms.
            if (delay > 0)
//                log(Level.DEBUG, "wait(" + delay + ")");
            mHandler.postDelayed(() -> {
                // Some proximity tags (e.g. nRF PROXIMITY) initialize bonding automatically when connected.
                if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_BONDING) {
//                    log(Level.VERBOSE, "Discovering Services...");
//                    log(Level.DEBUG, "gatt.discoverServices()");
                    gatt.discoverServices();
                }
            }, delay);
        } else {
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                if (status != BluetoothGatt.GATT_SUCCESS)
//                    log(Level.WARNING, "Error: (0x" + Integer.toHexString(status) + "): " +
//                            GattError.parseConnectionError(status));

                mOperationInProgress = true; // no more calls are possible
                mInitQueue = null;
                mTaskQueue.clear();

                // Signal all threads waiting synchronously for a notification
                for (final ValueChangedCallback callback : mNotificationCallbacks.values()) {
                    callback.notifyDeviceDisconnected(gatt.getDevice());
                }
                // Signal the current request, if any
                if (mRequest != null && mRequest.type != Request.Type.DISCONNECT) {
                    mRequest.notifyFail(gatt.getDevice(), FailCallback.REASON_DEVICE_DISCONNECTED);
                }
                if (mConnectRequest != null) {
                    mConnectRequest.notifyFail(gatt.getDevice(), FailCallback.REASON_DEVICE_DISCONNECTED);
                    mConnectRequest = null;
                }

                // Store the current value of the mConnected flag...
                final boolean wasConnected = mConnected;
                // ...because this method sets the mConnected flag to false
                notifyDeviceDisconnected(gatt.getDevice());

                // Try to reconnect if the initial connection was lost because of a link loss or timeout,
                // and shouldAutoConnect() returned true during connection attempt.
                // This time it will set the autoConnect flag to true (gatt.connect() forces autoConnect true)
                if (mInitialConnection) {
                    connect(gatt.getDevice());
                }

                if (wasConnected || status == BluetoothGatt.GATT_SUCCESS)
                    return;
            } else {
                if (status != BluetoothGatt.GATT_SUCCESS)
                    log(Level.ERROR, "Error (0x" + Integer.toHexString(status) + "): " +
                            GattError.parseConnectionError(status));
            }
            mCallbacks.onError(gatt.getDevice(), ERROR_CONNECTION_STATE_CHANGE, status);
        }
    }


    @Override
    public final void onConnectionStateChange(final BluetoothGatt gatt, final int status,
                                              final int newState) {
        runOnUiThread(() -> onConnectionStateChangeSafe(gatt, status, newState));
    }

//    @Override
//    public void onConnectionStateChange(android.bluetooth.BluetoothGatt gatt, int status, int newState) {
//        super.onConnectionStateChange(gatt,status,newState);
//        Log.d("MY_BLE", "Connection changed " + status +" state " + newState );
//    }

}
