package ru.spb.gamma.esealdemo;

import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;


    // DO NOT EDIT THIS FILE UNLESS NECESSARY!
    /**
     * The ESealManagerCallbacks should be overridden in your app and all the 'high level' callbacks
     * should be added there. See examples in Android nRF Blinky or Android nRF Toolbox.
     * <p>
     * Keeping this file as is (and {@link BleManager} as well) will allow to quickly update it when
     * an update is posted here.
     */
    @SuppressWarnings({"DeprecatedIsStillUsed", "unused"})
    public interface ESealManagerCallbacks {

        /**
         * Called when the Android device started connecting to given device.
         * The {@link #onDeviceConnected(BluetoothDevice)} will be called when the device is connected,
         * or {@link #onError(BluetoothDevice, String, int)} in case of error.
         *
         * @param device the device that got connected
         */
        void onDeviceConnecting(final BluetoothDevice device);

        /**
         * Called when the device has been connected. This does not mean that the application may start communication.
         * A service discovery will be handled automatically after this call. Service discovery
         * may ends up with calling {@link #onServicesDiscovered(BluetoothDevice, boolean)} or
         * {@link #onDeviceNotSupported(BluetoothDevice)} if required services have not been found.
         *
         * @param device the device that got connected
         */
        void onDeviceConnected(final BluetoothDevice device);

        /**
         * Called when user initialized disconnection.
         *
         * @param device the device that gets disconnecting
         */
        void onDeviceDisconnecting(final BluetoothDevice device);

        /**
         * Called when the device has disconnected (when the callback returned
         * {@link BluetoothGattCallback#onConnectionStateChange(BluetoothGatt, int, int)} with state DISCONNECTED),
         * but ONLY if the {@link BleManager#shouldAutoConnect()} method returned false for this device when it was connecting.
         * Otherwise the {@link #onLinkLossOccurred(BluetoothDevice)} method will be called instead.
         *
         * @param device the device that got disconnected
         */
        void onDeviceDisconnected(final BluetoothDevice device);

        /**
         * This callback is invoked when the Ble Manager lost connection to a device that has been connected
         * with autoConnect option (see {@link BleManager#shouldAutoConnect()}.
         * Otherwise a {@link #onDeviceDisconnected(BluetoothDevice)} method will be called on such event.
         *
         * @param device the device that got disconnected due to a link loss
         */
        void onLinkLossOccurred(final BluetoothDevice device);

        /**
         * Called when service discovery has finished and primary services has been found.
         * This method is not called if the primary, mandatory services were not found during service discovery.
         * For example in the Blood Pressure Monitor, a Blood Pressure service is a primary service and
         * Intermediate Cuff Pressure service is a optional secondary service.
         * Existence of battery service is not notified by this call.
         * <p>After successful service discovery the service will initialize all services.
         * The {@link #onDeviceReady(BluetoothDevice)} method will be called when the initialization is complete.</p>
         *
         * @param device                the device which services got disconnected
         * @param optionalServicesFound if <code>true</code> the secondary services were also found on the device.
         */
        void onServicesDiscovered(final BluetoothDevice device, final boolean optionalServicesFound);

        /**
         * Method called when all initialization requests has been completed.
         *
         * @param device the device that get ready
         */
        void onDeviceReady(final BluetoothDevice device);

        /**
         * This method should return true if Battery Level notifications should be enabled on the target device.
         * If there is no Battery Service, or the Battery Level characteristic does not have NOTIFY property,
         * this method will not be called for this device.
         * <p>
         * This method may return true only if an activity is bound to the service (to display the information
         * to the user), always (e.g. if critical battery level is reported using notifications) or never, if
         * such information is not important or the manager wants to control Battery Level notifications on its own.</p>
         *
         * @param device target device
         * @return true to enabled battery level notifications after connecting to the device, false otherwise
         * @deprecated Use
         * <pre>{@code
         * enableNotifications(batteryLevelCharacteristic).with(new BatteryLevelDataCallback() {
         *     onBatteryLevelChanged(int batteryLevel) {
         *         ...
         *     }
         * })
         * }</pre>
         * in the {@link BleManager.BleManagerGattCallback#initialize() initialize(BluetoothDevice)} instead.
         */
        @Deprecated
        default boolean shouldEnableBatteryLevelNotifications(final BluetoothDevice device) {
            return false;
        }

        /**
         * Called when battery value has been received from the device.
         *
         * @param device the device from which the battery value has changed
         * @param value  the battery value in percent
         * @deprecated Use
         * <pre>{@code
         * enableNotifications(batteryLevelCharacteristic).with(new BatteryLevelDataCallback() {
         *     onBatteryLevelChanged(int batteryLevel) {
         *         ...
         *     }
         * })
         * }</pre>
         * in the {@link BleManager.BleManagerGattCallback#initialize() initialize(BluetoothDevice)} instead.
         */
        @Deprecated
        default void onBatteryValueReceived(final BluetoothDevice device, final int value) {
            // do nothing
        }

        /**
         * Called when an {@link BluetoothGatt#GATT_INSUFFICIENT_AUTHENTICATION} error occurred and the
         * device bond state is {@link BluetoothDevice#BOND_NONE}.
         *
         * @param device the device that requires bonding
         */
        void onBondingRequired(final BluetoothDevice device);

        /**
         * Called when the device has been successfully bonded.
         *
         * @param device the device that got bonded
         */
        void onBonded(final BluetoothDevice device);

        /**
         * Called when the bond state has changed from {@link BluetoothDevice#BOND_BONDING} to {@link BluetoothDevice#BOND_NONE}.
         *
         * @param device the device that failed to bond
         */
//        void onBondingFailed(final BluetoothDevice device);

        /**
         * Called when a BLE error has occurred
         *
         * @param message   the error message
         * @param errorCode the error code
         * @param device    the device that caused an error
         */
        void onError(final BluetoothDevice device, final String message, final int errorCode);

        /**
         * Called when service discovery has finished but the main services were not found on the device.
         *
         * @param device the device that failed to connect due to lack of required services
         */
        void onDeviceNotSupported(final BluetoothDevice device);
        void onDataReceived(final BluetoothDevice device, final String data);

        void onDataSent(final BluetoothDevice device, final String data);

}
