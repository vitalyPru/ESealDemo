package ru.spb.gamma.esealdemo;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.text.TextUtils;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;
import java.util.logging.Logger;

public  class ESealManager {
    /** Nordic UART Service UUID */
    private final static UUID CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final static UUID BATTERY_SERVICE = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    private final static UUID BATTERY_LEVEL_CHARACTERISTIC = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");

    private final static UUID GENERIC_ATTRIBUTE_SERVICE = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb");
    private final static UUID SERVICE_CHANGED_CHARACTERISTIC = UUID.fromString("00002A05-0000-1000-8000-00805f9b34fb");
    private final static UUID UART_SERVICE_UUID = UUID.fromString("6E400001-B5A3-F393-E0A9-E50E24DCCA9E");
    /** RX characteristic UUID */
    private final static UUID UART_RX_CHARACTERISTIC_UUID = UUID.fromString("6E400002-B5A3-F393-E0A9-E50E24DCCA9E");
    /** TX characteristic UUID */
    private final static UUID UART_TX_CHARACTERISTIC_UUID = UUID.fromString("6E400003-B5A3-F393-E0A9-E50E24DCCA9E");
    /** The maximum packet size is 20 bytes. */
    private static final int MAX_PACKET_SIZE = 20;
    private final Handler mHandler;

    private BluetoothGattCharacteristic mRXCharacteristic, mTXCharacteristic;
    private byte[] mOutgoingBuffer;
    private int mBufferOffset;
    private final Object mLock = new Object();
    private final Context mContext;

    private boolean mInitialConnection;
    private boolean mUserDisconnected;
    protected BluetoothDevice mBluetoothDevice;

    private int mMtu = 23;
    private int mBatteryValue = -1;

    protected ESealManagerCallbacks mCallbacks;
    private BluetoothGatt mBluetoothGatt;

    private boolean mConnected;
    private int mConnectionState = BluetoothGatt.STATE_DISCONNECTED;


    public ESealManager(final Context context, boolean connected) {
        /*super(context);*/
        mHandler = new Handler();
        mContext = context;
        mConnected = connected;
    }

    public void setGattCallbacks(@NonNull ESealManagerCallbacks  callbacks) {
        mCallbacks = callbacks;
    }


    /**
     * BluetoothGatt callbacks for connection/disconnection, service discovery, receiving notification, etc
     */
    private final EsealGattCallback mGattCallback = new EsealGattCallback() {

        @Override
        protected Deque<Request> initGatt(final BluetoothGatt gatt) {
            final LinkedList<Request> requests = new LinkedList<>();
            requests.add(Request.newEnableNotificationsRequest(mTXCharacteristic));
 //           requests.add(Request.newWriteRequest(mRXCharacteristic, "getPair\r\n".getBytes(), 0, 9));
;
            return requests;
        }

        @Override
        public boolean isRequiredServiceSupported(final BluetoothGatt gatt) {
            final BluetoothGattService service = gatt.getService(UART_SERVICE_UUID);
            if (service != null) {
                mRXCharacteristic = service.getCharacteristic(UART_RX_CHARACTERISTIC_UUID);
                mTXCharacteristic = service.getCharacteristic(UART_TX_CHARACTERISTIC_UUID);
            }

            boolean writeRequest = false;
            boolean writeCommand = false;
            if (mRXCharacteristic != null) {
                final int rxProperties = mRXCharacteristic.getProperties();
                writeRequest = (rxProperties & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0;
                writeCommand = (rxProperties & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0;

                // Set the WRITE REQUEST type when the characteristic supports it. This will allow to send long write (also if the characteristic support it).
                // In case there is no WRITE REQUEST property, this manager will divide texts longer then 20 bytes into up to 20 bytes chunks.
                if (writeRequest)
                    mRXCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            }

            return mRXCharacteristic != null && mTXCharacteristic != null && (writeRequest || writeCommand);
        }

        @Override
        protected void onDeviceDisconnected() {
            mRXCharacteristic = null;
            mTXCharacteristic = null;
        }

        @Override
        public void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // When the whole buffer has been sent
            final byte[] buffer = mOutgoingBuffer;
            if (mBufferOffset == buffer.length) {
                try {
                    final String data = new String(buffer, "UTF-8");
                    Log.d("BT MANAGER", "\"" + data + "\" sent");
                    mOutgoingBuffer = null;
                    mCallbacks.onDataSent(gatt.getDevice(), data);
                } catch (final UnsupportedEncodingException e) {
                    mOutgoingBuffer = null;
                    // do nothing
                }
            } else { // Otherwise...
                final int length = Math.min(buffer.length - mBufferOffset, MAX_PACKET_SIZE);
                enqueue(Request.newWriteRequest(mRXCharacteristic, buffer, mBufferOffset, length));
                mBufferOffset += length;
            }
        }

        @Override
        public void onCharacteristicNotified(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            final String data = characteristic.getStringValue(0);
            Log.d("BT MANAGER", "\"" + data + "\" received");
            mCallbacks.onDataReceived(gatt.getDevice(), data);
        }
    };

    protected boolean shouldAutoConnect() {
        // We want the connection to be kept
        return false;
    }

    public void connect(@NonNull final BluetoothDevice device) {
        if (mCallbacks == null) {
            throw new NullPointerException("You have to set callbacks using setGattCallbacks(E callbacks) before connecting");
        }
        if (mConnected) {
            return;
        }

        synchronized (mLock) {
            if (mBluetoothGatt != null) {
                // There are 2 ways of reconnecting to the same device:
                // 1. Reusing the same BluetoothGatt object and calling connect() - this will force the autoConnect flag to true
                // 2. Closing it and reopening a new instance of BluetoothGatt object.
                // The gatt.close() is an asynchronous method. It requires some time before it's finished and
                // device.connectGatt(...) can't be called immediately or service discovery
                // may never finish on some older devices (Nexus 4, Android 5.0.1).
                // If shouldAutoConnect() method returned false we can't call gatt.connect() and have to close gatt and open it again.
                if (!mInitialConnection) {
                    Log.d("BT MANAGER", "gatt.close()");
                    mBluetoothGatt.close();
                    mBluetoothGatt = null;
                    try {
                        Log.d("BT MANAGER", "wait(200)");
                        Thread.sleep(200); // Is 200 ms enough?
                    } catch (final InterruptedException e) {
                        // Ignore
                    }
                } else {
                    // Instead, the gatt.connect() method will be used to reconnect to the same device.
                    // This method forces autoConnect = true even if the gatt was created with this flag set to false.
                    mInitialConnection = false;
                    Log.d("BT MANAGER", "Connecting...");
                    mConnectionState = BluetoothGatt.STATE_CONNECTING;
                    mCallbacks.onDeviceConnecting(device);
                    Log.d("BT MANAGER", "gatt.connect()");
                    mBluetoothGatt.connect();
                    return;
                }
            } else {
                // Register bonding broadcast receiver
//                mContext.registerReceiver(mBluetoothStateBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
//                mContext.registerReceiver(mBondingBroadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
//                mContext.registerReceiver(mPairingRequestBroadcastReceiver, new IntentFilter("android.bluetooth.device.action.PAIRING_REQUEST"/*BluetoothDevice.ACTION_PAIRING_REQUEST*/));
            }
        }

        final boolean shouldAutoConnect = shouldAutoConnect();
        mUserDisconnected = !shouldAutoConnect; // We will receive Linkloss events only when the device is connected with autoConnect=true
        // The first connection will always be done with autoConnect = false to make the connection quick.
        // If the shouldAutoConnect() method returned true, the manager will automatically try to reconnect to this device on link loss.
        if (shouldAutoConnect)
            mInitialConnection = true;
        mBluetoothDevice = device;
        Log.d("BT MANAGER", "Connecting...");
        mConnectionState = BluetoothGatt.STATE_CONNECTING;
        mCallbacks.onDeviceConnecting(device);
        Log.d("BT MANAGER", "gatt = device.connectGatt(autoConnect = false)");
        mBluetoothGatt = device.connectGatt(mContext, false, mGattCallback/* = getGattCallback()*/);
    }

    /**
     * Disconnects from the device or cancels the pending connection attempt. Does nothing if device was not connected.
     *
     * @return true if device is to be disconnected. False if it was already disconnected.
     */
    public boolean disconnect() {
        mUserDisconnected = true;
        mInitialConnection = false;

        if (mBluetoothGatt != null) {
            mConnectionState = BluetoothGatt.STATE_DISCONNECTING;
            Log.d("BT MANAGER", mConnected ? "Disconnecting..." : "Cancelling connection...");
            mCallbacks.onDeviceDisconnecting(mBluetoothGatt.getDevice());
            final boolean wasConnected = mConnected;
            Log.d("BT MANAGER", "gatt.disconnect()");
            mBluetoothGatt.disconnect();

            if (!wasConnected) {
                // There will be no callback, the connection attempt will be stopped
                mConnectionState = BluetoothGatt.STATE_DISCONNECTED;
                Log.d("BT MANAGER", "Disconnected");
                mCallbacks.onDeviceDisconnected(mBluetoothGatt.getDevice());
            }
            return true;
        }
        return false;
    }

    /**
     * This method returns true if the device is connected. Services could have not been discovered yet.
     */
    public boolean isConnected() {
        return mConnected;
    }

    public void close() {
        try {
//            mContext.unregisterReceiver(mBluetoothStateBroadcastReceiver);
//            mContext.unregisterReceiver(mBondingBroadcastReceiver);
//            mContext.unregisterReceiver(mPairingRequestBroadcastReceiver);
        } catch (final Exception e) {
            // the receiver must have been not registered or unregistered before
        }
        synchronized (mLock) {
            if (mBluetoothGatt != null) {
                Log.d("BT MANAGER", "gatt.close()");
                mBluetoothGatt.close();
                mBluetoothGatt = null;
            }
            mConnected = false;
            mInitialConnection = false;
            mConnectionState = BluetoothGatt.STATE_DISCONNECTED;
//            mGattCallback = null;
            mBluetoothDevice = null;
        }
    }
    /**
     * Sends the given text to RX characteristic.
     * @param text the text to be sent
     */
    public void send(final String text) {
        // Are we connected?
        if (mRXCharacteristic == null)
            return;

        // An outgoing buffer may not be null if there is already another packet being sent. We do nothing in this case.
        if (!TextUtils.isEmpty(text) && mOutgoingBuffer == null) {
            final byte[] buffer = mOutgoingBuffer = text.getBytes();
            mBufferOffset = 0;

            // Depending on whether the characteristic has the WRITE REQUEST property or not, we will either send it as it is (hoping the long write is implemented),
            // or divide it into up to 20 bytes chunks and send them one by one.
            final boolean writeRequest = (mRXCharacteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0;

            if (!writeRequest) { // no WRITE REQUEST property
                final int length = Math.min(buffer.length, MAX_PACKET_SIZE);
                mBufferOffset += length;
                enqueue(Request.newWriteRequest(mRXCharacteristic, buffer, 0, length));
            } else { // there is WRITE REQUEST property, let's try Long Write
                mBufferOffset = buffer.length;
                enqueue(Request.newWriteRequest(mRXCharacteristic, buffer, 0, buffer.length));
            }
        }
    }


    protected final boolean createBond() {
        return enqueue(Request.createBond());
    }

    /**
     * Creates a bond with the device. The device must be first set using {@link #connect(BluetoothDevice)} which will
     * try to connect to the device. If you need to pair with a device before connecting to it you may do it without
     * the use of BleManager object and connect after bond is established.
     *
     * @return true if pairing has started, false if it was already paired or an immediate error occur.
     */
    private boolean internalCreateBond() {
        final BluetoothDevice device = mBluetoothDevice;
        if (device == null)
            return false;

        if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
            Log.d("BT MANAGER", "Create bond request on already bonded device...");
            Log.d("BT MANAGER", "Device bonded");
            return false;
        }

        Log.d("BT MANAGER", "Starting pairing...");

        boolean result = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            Log.d("BT MANAGER", "device.createBond()");
            result = device.createBond();
        } else {
            /*
             * There is a createBond() method in BluetoothDevice class but for now it's hidden. We will call it using reflections. It has been revealed in KitKat (Api19)
             */
            try {
                final Method createBond = device.getClass().getMethod("createBond");
                if (createBond != null) {
                    Log.d("BT MANAGER", "device.createBond() (hidden)");
                    result = (Boolean) createBond.invoke(device);
                }
            } catch (final Exception e) {
                Log.e("BT MANAGER", "An exception occurred while creating bond", e);
            }
        }

        if (!result)
            Log.d("BT MANAGER", "Creating bond failed");
        return result;
    }

    /**
     * When the device is bonded and has the Generic Attribute service and the Service Changed characteristic this method enables indications on this characteristic.
     * In case one of the requirements is not fulfilled this method returns <code>false</code>.
     *
     * @return <code>true</code> when the request has been sent, <code>false</code> when the device is not bonded, does not have the Generic Attribute service, the GA service does not have
     * the Service Changed characteristic or this characteristic does not have the CCCD.
     */
    private boolean ensureServiceChangedEnabled() {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null)
            return false;

        // The Service Changed indications have sense only on bonded devices
        final BluetoothDevice device = gatt.getDevice();
        if (device.getBondState() != BluetoothDevice.BOND_BONDED)
            return false;

        final BluetoothGattService gaService = gatt.getService(GENERIC_ATTRIBUTE_SERVICE);
        if (gaService == null)
            return false;

        final BluetoothGattCharacteristic scCharacteristic = gaService.getCharacteristic(SERVICE_CHANGED_CHARACTERISTIC);
        if (scCharacteristic == null)
            return false;

        Log.d("BT MANAGER", "Service Changed characteristic found on a bonded device");
        return internalEnableIndications(scCharacteristic);
    }

    /**
     * Enables notifications on given characteristic.
     *
     * @return true is the request has been enqueued
     */
    protected final boolean enableNotifications(final BluetoothGattCharacteristic characteristic) {
        return enqueue(Request.newEnableNotificationsRequest(characteristic));
    }

    private boolean internalEnableNotifications(final BluetoothGattCharacteristic characteristic) {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null || characteristic == null)
            return false;

        // Check characteristic property
        final int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0)
            return false;

        Log.d("BT MANAGER", "gatt.setCharacteristicNotification(" + characteristic.getUuid() + ", true)");
        gatt.setCharacteristicNotification(characteristic, true);
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            Log.d("BT MANAGER", "Enabling notifications for " + characteristic.getUuid());
            Log.d("BT MANAGER", "gatt.writeDescriptor(" + CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID + ", value=0x01-00)");
            return internalWriteDescriptorWorkaround(descriptor);
        }
        return false;
    }

    /**
     * Enables notifications on given characteristic.
     *
     * @return true is the request has been enqueued
     */
    protected final boolean disableNotifications(final BluetoothGattCharacteristic characteristic) {
        return enqueue(Request.newDisableNotificationsRequest(characteristic));
    }

    private boolean internalDisableNotifications(final BluetoothGattCharacteristic characteristic) {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null || characteristic == null)
            return false;

        // Check characteristic property
        final int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0)
            return false;

        Log.d("BT MANAGER", "gatt.setCharacteristicNotification(" + characteristic.getUuid() + ", false)");
        gatt.setCharacteristicNotification(characteristic, false);
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            Log.d("BT MANAGER", "Disabling notifications for " + characteristic.getUuid());
            Log.d("BT MANAGER", "gatt.writeDescriptor(" + CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID + ", value=0x00-00)");
            return internalWriteDescriptorWorkaround(descriptor);
        }
        return false;
    }

    /**
     * Enables indications on given characteristic.
     *
     * @return true is the request has been enqueued
     */
    protected final boolean enableIndications(final BluetoothGattCharacteristic characteristic) {
        return enqueue(Request.newEnableIndicationsRequest(characteristic));
    }

    private boolean internalEnableIndications(final BluetoothGattCharacteristic characteristic) {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null || characteristic == null)
            return false;

        // Check characteristic property
        final int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) == 0)
            return false;

        Log.d("BT MANAGER", "gatt.setCharacteristicNotification(" + characteristic.getUuid() + ", true)");
        gatt.setCharacteristicNotification(characteristic, true);
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            Log.d("BT MANAGER", "Enabling indications for " + characteristic.getUuid());
            Log.d("BT MANAGER", "gatt.writeDescriptor(" + CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID + ", value=0x02-00)");
            return internalWriteDescriptorWorkaround(descriptor);
        }
        return false;
    }

    /**
     * Enables indications on given characteristic.
     *
     * @return true is the request has been enqueued
     */
    protected final boolean disableIndications(final BluetoothGattCharacteristic characteristic) {
        return enqueue(Request.newDisableIndicationsRequest(characteristic));
    }

    private boolean internalDisableIndications(final BluetoothGattCharacteristic characteristic) {
        // This writes exactly the same settings so do not duplicate code
        return internalDisableNotifications(characteristic);
    }

    /**
     * Sends the read request to the given characteristic.
     *
     * @param characteristic the characteristic to read
     * @return true if request has been enqueued
     */
    protected final boolean readCharacteristic(final BluetoothGattCharacteristic characteristic) {
        return enqueue(Request.newReadRequest(characteristic));
    }

    private boolean internalReadCharacteristic(final BluetoothGattCharacteristic characteristic) {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null || characteristic == null)
            return false;

        // Check characteristic property
        final int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) == 0)
            return false;

        Log.d("BT MANAGER", "Reading characteristic " + characteristic.getUuid());
        Log.d("BT MANAGER", "gatt.readCharacteristic(" + characteristic.getUuid() + ")");
        return gatt.readCharacteristic(characteristic);
    }

    /**
     * Writes the characteristic value to the given characteristic.
     * The write type will be read from the characteristic.
     *
     * @param characteristic the characteristic to write to
     * @param data the data to be sent
     * @return true if request has been enqueued
     */
    protected final boolean writeCharacteristic(final BluetoothGattCharacteristic characteristic, final byte[] data) {
        return enqueue(Request.newWriteRequest(characteristic, data));
    }

    /**
     * Writes the characteristic value to the given characteristic.
     *
     * @param characteristic the characteristic to write to
     * @param data the data to be sent
     * @param writeType the write type, one of {@link BluetoothGattCharacteristic#WRITE_TYPE_DEFAULT}
     *                  or {@link BluetoothGattCharacteristic#WRITE_TYPE_NO_RESPONSE}.
     * @return true if request has been enqueued
     */
    protected final boolean writeCharacteristic(final BluetoothGattCharacteristic characteristic, final byte[] data, final int writeType) {
        return enqueue(Request.newWriteRequest(characteristic, data, writeType));
    }

    private boolean internalWriteCharacteristic(final BluetoothGattCharacteristic characteristic) {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null || characteristic == null)
            return false;

        // Check characteristic property
        final int properties = characteristic.getProperties();
        if ((properties & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0)
            return false;

        Log.d("BT MANAGER", "Writing characteristic " + characteristic.getUuid() + " (" + getWriteType(characteristic.getWriteType()) + ")");
        Log.d("BT MANAGER", "gatt.writeCharacteristic(" + characteristic.getUuid() + ")");
        return gatt.writeCharacteristic(characteristic);
    }

    /**
     * Sends the read request to the given descriptor.
     *
     * @param descriptor the descriptor to read
     * @return true if request has been enqueued
     */
    protected final boolean readDescriptor(final BluetoothGattDescriptor descriptor) {
        return enqueue(Request.newReadRequest(descriptor));
    }

    private boolean internalReadDescriptor(final BluetoothGattDescriptor descriptor) {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null || descriptor == null)
            return false;

        Log.d("BT MANAGER", "Reading descriptor " + descriptor.getUuid());
        Log.d("BT MANAGER", "gatt.readDescriptor(" + descriptor.getUuid() + ")");
        return gatt.readDescriptor(descriptor);
    }

    /**
     * Writes the descriptor value to the given descriptor.
     *
     * @param descriptor the descriptor to write to
     * @param data the data to be sent
     * @return true if request has been enqueued
     */
    protected final boolean writeDescriptor(final BluetoothGattDescriptor descriptor, final byte[] data) {
        return enqueue(Request.newWriteRequest(descriptor, data));
    }

    private boolean internalWriteDescriptor(final BluetoothGattDescriptor descriptor) {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null || descriptor == null)
            return false;

        Log.d("BT MANAGER", "Writing descriptor " + descriptor.getUuid());
        Log.d("BT MANAGER", "gatt.writeDescriptor(" + descriptor.getUuid() + ")");
        return internalWriteDescriptorWorkaround(descriptor);
    }

    /**
     * Reads the battery level from the device.
     *
     * @return true if request has been enqueued
     */
    public final boolean readBatteryLevel() {
        return enqueue(Request.newReadBatteryLevelRequest());
    }

    private boolean internalReadBatteryLevel() {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null)
            return false;

        final BluetoothGattService batteryService = gatt.getService(BATTERY_SERVICE);
        if (batteryService == null)
            return false;

        final BluetoothGattCharacteristic batteryLevelCharacteristic = batteryService.getCharacteristic(BATTERY_LEVEL_CHARACTERISTIC);
        if (batteryLevelCharacteristic == null)
            return false;

        // Check characteristic property
        final int properties = batteryLevelCharacteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) == 0)
            return false;

        Log.d("BT MANAGER", "Reading battery level...");
        return internalReadCharacteristic(batteryLevelCharacteristic);
    }

    /**
     * This method tries to enable notifications on the Battery Level characteristic.
     *
     * @param enable <code>true</code> to enable battery notifications, false to disable
     * @return true if request has been enqueued
     */
    public final boolean setBatteryNotifications(final boolean enable) {
        if (enable)
            return enqueue(Request.newEnableBatteryLevelNotificationsRequest());
        else
            return enqueue(Request.newDisableBatteryLevelNotificationsRequest());
    }

    private boolean internalSetBatteryNotifications(final boolean enable) {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null) {
            return false;
        }

        final BluetoothGattService batteryService = gatt.getService(BATTERY_SERVICE);
        if (batteryService == null)
            return false;

        final BluetoothGattCharacteristic batteryLevelCharacteristic = batteryService.getCharacteristic(BATTERY_LEVEL_CHARACTERISTIC);
        if (batteryLevelCharacteristic == null)
            return false;

        // Check characteristic property
        final int properties = batteryLevelCharacteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0)
            return false;

        gatt.setCharacteristicNotification(batteryLevelCharacteristic, enable);
        final BluetoothGattDescriptor descriptor = batteryLevelCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
        if (descriptor != null) {
            if (enable) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                Log.d("BT MANAGER", "Enabling battery level notifications...");
                Log.d("BT MANAGER", "Enabling notifications for " + BATTERY_LEVEL_CHARACTERISTIC);
                Log.d("BT MANAGER", "gatt.writeDescriptor(" + CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID + ", value=0x0100)");
            } else {
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
                Log.d("BT MANAGER", "Disabling battery level notifications...");
                Log.d("BT MANAGER", "Disabling notifications for " + BATTERY_LEVEL_CHARACTERISTIC);
                Log.d("BT MANAGER", "gatt.writeDescriptor(" + CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID + ", value=0x0000)");
            }
            return internalWriteDescriptorWorkaround(descriptor);
        }
        return false;
    }

    /**
     * There was a bug in Android up to 6.0 where the descriptor was written using parent
     * characteristic's write type, instead of always Write With Response, as the spec says.
     * <p>
     * See: <a href="https://android.googlesource.com/platform/frameworks/base/+/942aebc95924ab1e7ea1e92aaf4e7fc45f695a6c%5E%21/#F0">
     * https://android.googlesource.com/platform/frameworks/base/+/942aebc95924ab1e7ea1e92aaf4e7fc45f695a6c%5E%21/#F0</a>
     * </p>
     *
     * @param descriptor the descriptor to be written
     * @return the result of {@link BluetoothGatt#writeDescriptor(BluetoothGattDescriptor)}
     */
    private boolean internalWriteDescriptorWorkaround(final BluetoothGattDescriptor descriptor) {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null || descriptor == null)
            return false;

        final BluetoothGattCharacteristic parentCharacteristic = descriptor.getCharacteristic();
        final int originalWriteType = parentCharacteristic.getWriteType();
        parentCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        final boolean result = gatt.writeDescriptor(descriptor);
        parentCharacteristic.setWriteType(originalWriteType);
        return result;
    }

    /**
     * Requests new MTU. On Android 4.3 and 4.4.x returns false.
     *
     * @return true if request has been enqueued
     */
    protected final boolean requestMtu(final int mtu) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && enqueue(Request.newMtuRequest(mtu));
    }

    /**
     * Returns the current MTU (Maximum Transfer Unit). MTU specifies the maximum number of bytes that can
     * be sent in a single write operation. 3 bytes are used for internal purposes, so the maximum size is MTU-3.
     * The value will changed only if requested with {@link #requestMtu(int)} and a successful callback is received.
     * If the peripheral requests MTU change, the {@link BluetoothGattCallback#onMtuChanged(BluetoothGatt, int, int)}
     * callback is not invoked, therefor the returned MTU value will not be correct.
     * Use {@link android.bluetooth.BluetoothGattServerCallback#onMtuChanged(BluetoothDevice, int)} to get the
     * callback with right value requested from the peripheral side.
     * @return the current MTU value. Default to 23.
     */
    protected final int getMtu() {
        return mMtu;
    }

    /**
     * This method overrides the MTU value. Use it only when the peripheral has changed MTU and you
     * received the {@link android.bluetooth.BluetoothGattServerCallback#onMtuChanged(BluetoothDevice, int)}
     * callback. If you want to set MTU as a master, use {@link #requestMtu(int)} instead.
     * @param mtu the MTU value set by the peripheral.
     */
    protected final void overrideMtu(final int mtu) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP)
            mMtu = mtu;
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private boolean internalRequestMtu(final int mtu) {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null)
            return false;

        Log.d("BT MANAGER", "Requesting new MTU...");
        Log.d("BT MANAGER", "gatt.requestMtu(" + mtu + ")");
        return gatt.requestMtu(mtu);
    }

    /**
     * Requests the new connection priority. Acceptable values are:
     * <ol>
     * <li>{@link BluetoothGatt#CONNECTION_PRIORITY_HIGH} - Interval: 11.25 -15 ms, latency: 0, supervision timeout: 20 sec,</li>
     * <li>{@link BluetoothGatt#CONNECTION_PRIORITY_BALANCED} - Interval: 30 - 50 ms, latency: 0, supervision timeout: 20 sec,</li>
     * <li>{@link BluetoothGatt#CONNECTION_PRIORITY_LOW_POWER} - Interval: 100 - 125 ms, latency: 2, supervision timeout: 20 sec.</li>
     * </ol>
     * On Android 4.3 and 4.4.x returns false.
     *
     * @param priority one of: {@link BluetoothGatt#CONNECTION_PRIORITY_HIGH}, {@link BluetoothGatt#CONNECTION_PRIORITY_BALANCED},
     *                 {@link BluetoothGatt#CONNECTION_PRIORITY_LOW_POWER}.
     * @return true if request has been enqueued
     */
    protected final boolean requestConnectionPriority(final int priority) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && enqueue(Request.newConnectionPriorityRequest(priority));
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private boolean internalRequestConnectionPriority(final int priority) {
        final BluetoothGatt gatt = mBluetoothGatt;
        if (gatt == null)
            return false;

        String text, priorityText;
        switch (priority) {
            case BluetoothGatt.CONNECTION_PRIORITY_HIGH:
                text = Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? "HIGH (11.25–15ms, 0, 20s)" : "HIGH (7.5–10ms, 0, 20s)";
                priorityText = "HIGH";
                break;
            case BluetoothGatt.CONNECTION_PRIORITY_LOW_POWER:
                text = "BALANCED (30–50ms, 0, 20s)";
                priorityText = "LOW POWER";
                break;
            default:
            case BluetoothGatt.CONNECTION_PRIORITY_BALANCED:
                text = "LOW POWER (100–125ms, 2, 20s)";
                priorityText = "BALANCED";
                break;
        }
        Log.d("BT MANAGER", "Requesting connection priority: " + text + "...");
        Log.d("BT MANAGER", "gatt.requestConnectionPriority(" + priorityText + ")");
        return gatt.requestConnectionPriority(priority);
    }

    /**
     * Enqueues a new request. The request will be handled immediately if there is no operation in progress,
     * or automatically after the last enqueued one will finish.
     * <p>This method should be used to read and write data from the target device as it ensures that the last operation has finished
     * before a new one will be called.</p>
     *
     * @param request new request to be added to the queue.
     * @return true if request has been enqueued, false if the {@link #connect(BluetoothDevice)} method was not called before,
     * or the manager was closed using {@link #close()}.
     */
    protected boolean enqueue(@NonNull final Request request) {
        if (mGattCallback != null) {
            // Add the new task to the end of the queue
            mGattCallback.mTaskQueue.add(request);
            mGattCallback.nextRequest();
            return true;
        }
        return false;
    }

    protected abstract class EsealGattCallback extends BluetoothGattCallback {

        private final static String ERROR_CONNECTION_STATE_CHANGE = "Error on connection state change";
        private final static String ERROR_DISCOVERY_SERVICE = "Error on discovering services";
        private final static String ERROR_AUTH_ERROR_WHILE_BONDED = "Phone has lost bonding information";
        private final static String ERROR_READ_CHARACTERISTIC = "Error on reading characteristic";
        private final static String ERROR_WRITE_CHARACTERISTIC = "Error on writing characteristic";
        private final static String ERROR_READ_DESCRIPTOR = "Error on reading descriptor";
        private final static String ERROR_WRITE_DESCRIPTOR = "Error on writing descriptor";
        private final static String ERROR_MTU_REQUEST = "Error on mtu request";
        private final static String ERROR_CONNECTION_PRIORITY_REQUEST = "Error on connection priority request";

        private final Queue<Request> mTaskQueue = new LinkedList<>();
        private Deque<Request> mInitQueue;
        private boolean mInitInProgress;
        private boolean mOperationInProgress = true; // Initially true to block operations before services are discovered.
        /**
         * This flag is required to resume operations after the connection priority request was made.
         * It is used only on Android Oreo and newer, as only there there is onConnectionUpdated callback.
         * However, as this callback is triggered every time the connection parameters change, even
         * when such request wasn't made, this flag ensures the nextRequest() method won't be called
         * during another operation.
         */
        private boolean mConnectionPriorityOperationInProgress = false;

        /**
         * This method should return <code>true</code> when the gatt device supports the required services.
         *
         * @param gatt the gatt device with services discovered
         * @return <code>true</code> when the device has teh required service
         */
        protected abstract boolean isRequiredServiceSupported(final BluetoothGatt gatt);

        /**
         * This method should return <code>true</code> when the gatt device supports the optional services.
         * The default implementation returns <code>false</code>.
         *
         * @param gatt the gatt device with services discovered
         * @return <code>true</code> when the device has teh optional service
         */
        protected boolean isOptionalServiceSupported(final BluetoothGatt gatt) {
            return false;
        }

        /**
         * This method should return a list of requests needed to initialize the profile.
         * Enabling Service Change indications for bonded devices and reading the Battery Level value and enabling Battery Level notifications
         * is handled before executing this queue. The queue should not have requests that are not available, e.g. should not
         * read an optional service when it is not supported by the connected device.
         * <p>This method is called when the services has been discovered and the device is supported (has required service).</p>
         *
         * @param gatt the gatt device with services discovered
         * @return the queue of requests
         */
        protected abstract Deque<Request> initGatt(final BluetoothGatt gatt);

        /**
         * Called then the initialization queue is complete.
         */
        protected void onDeviceReady() {
            mCallbacks.onDeviceReady(mBluetoothGatt.getDevice());
        }

        /**
         * This method should nullify all services and characteristics of the device.
         * It's called when the device is no longer connected, either due to user action
         * or a link loss.
         */
        protected abstract void onDeviceDisconnected();

        private void notifyDeviceDisconnected(final BluetoothDevice device) {
            mConnected = false;
            mConnectionState = BluetoothGatt.STATE_DISCONNECTED;
            if (mUserDisconnected) {
                Log.d("BT MANAGER", "Disconnected");
                mCallbacks.onDeviceDisconnected(device);
                close();
            } else {
                Log.d("BT MANAGER", "Connection lost");
                mCallbacks.onLinkLossOccurred(device);
                // We are not closing the connection here as the device should try to reconnect automatically.
                // This may be only called when the shouldAutoConnect() method returned true.
            }
            onDeviceDisconnected();
        }

        /**
         * Callback reporting the result of a characteristic read operation.
         *
         * @param gatt           GATT client
         * @param characteristic Characteristic that was read from the associated remote device.
         */
        protected void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // do nothing
        }

        /**
         * Callback indicating the result of a characteristic write operation.
         * <p>If this callback is invoked while a reliable write transaction is
         * in progress, the value of the characteristic represents the value
         * reported by the remote device. An application should compare this
         * value to the desired value to be written. If the values don't match,
         * the application must abort the reliable write transaction.
         *
         * @param gatt           GATT client
         * @param characteristic Characteristic that was written to the associated remote device.
         */
        protected void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // do nothing
        }

        /**
         * Callback reporting the result of a descriptor read operation.
         *
         * @param gatt       GATT client
         * @param descriptor Descriptor that was read from the associated remote device.
         */
        protected void onDescriptorRead(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor) {
            // do nothing
        }

        /**
         * Callback indicating the result of a descriptor write operation.
         * <p>If this callback is invoked while a reliable write transaction is in progress,
         * the value of the characteristic represents the value reported by the remote device.
         * An application should compare this value to the desired value to be written.
         * If the values don't match, the application must abort the reliable write transaction.
         *
         * @param gatt       GATT client
         * @param descriptor Descriptor that was written to the associated remote device.
         */
        protected void onDescriptorWrite(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor) {
            // do nothing
        }

        /**
         * Callback reporting the value of Battery Level characteristic which could have
         * been received by Read or Notify operations.
         *
         * @param gatt  GATT client
         * @param value the battery value in percent
         */
        protected void onBatteryValueReceived(final BluetoothGatt gatt, final int value) {
            // do nothing
        }

        /**
         * Callback indicating a notification has been received.
         *
         * @param gatt           GATT client
         * @param characteristic Characteristic from which the notification came.
         */
        protected void onCharacteristicNotified(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // do nothing
        }

        /**
         * Callback indicating an indication has been received.
         *
         * @param gatt           GATT client
         * @param characteristic Characteristic from which the indication came.
         */
        protected void onCharacteristicIndicated(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            // do nothing
        }

        /**
         * Method called when the MTU request has finished with success. The MTU value may
         * be different than requested one.
         *
         * @param mtu the new MTU (Maximum Transfer Unit)
         */
        protected void onMtuChanged(final int mtu) {
            // do nothing
        }

        /**
         * Callback indicating the connection parameters were updated. Works on Android 8+.
         *
         * @param interval Connection interval used on this connection, 1.25ms unit. Valid range is from
         *                 6 (7.5ms) to 3200 (4000ms).
         * @param latency  Slave latency for the connection in number of connection events. Valid range
         *                 is from 0 to 499
         * @param timeout  Supervision timeout for this connection, in 10ms unit. Valid range is from 10
         *                 (0.1s) to 3200 (32s)
         */
        @TargetApi(Build.VERSION_CODES.O)
        protected void onConnectionUpdated(final int interval, final int latency, final int timeout) {
            // do nothing
        }

        private void onError(final BluetoothDevice device, final String message, final int errorCode) {
            Log.d("BT MANAGER", "Error (0x" + Integer.toHexString(errorCode) + "): "
                    /*+ GattError.parse(errorCode)*/);
            mCallbacks.onError(device, message, errorCode);
        }

        @Override
        public final void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            Log.d("BT MANAGER", "[Callback] Connection state changed with status: " + status + " and new state: " + newState + " (" + stateToString(newState) + ")");

            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                // Notify the parent activity/service
                Log.d("BT MANAGER", "Connected to " + gatt.getDevice().getAddress());
                mConnected = true;
                mConnectionState = BluetoothGatt.STATE_CONNECTED;
                mCallbacks.onDeviceConnected(gatt.getDevice());

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
                    Log.d("BT MANAGER", "wait(" + delay + ")");
                mHandler.postDelayed(() -> {
                    // Some proximity tags (e.g. nRF PROXIMITY) initialize bonding automatically when connected.
                    if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_BONDING) {
                        Log.d("BT MANAGER", "Discovering Services...");
                        Log.d("BT MANAGER", "gatt.discoverServices()");
                        gatt.discoverServices();
                    }
                }, delay);
            } else {
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    if (status != BluetoothGatt.GATT_SUCCESS)
                        Log.d("BT MANAGER", "Error: (0x" + Integer.toHexString(status) + "): "
                                /*+ GattError.parseConnectionError(status)*/);

                    mOperationInProgress = true; // no more calls are possible
                    mInitQueue = null;
                    mTaskQueue.clear();
                    final boolean wasConnected = mConnected;
                    // if (mConnected) { // Checking mConnected prevents from calling onDeviceDisconnected if connection attempt failed. This check is not necessary
                    notifyDeviceDisconnected(gatt.getDevice()); // This sets the mConnected flag to false
                    // }
                    // Try to reconnect if the initial connection was lost because of a link loss or timeout, and shouldAutoConnect() returned true during connection attempt.
                    // This time it will set the autoConnect flag to true (gatt.connect() forces autoConnect true)
                    if (mInitialConnection) {
                        connect(gatt.getDevice());
                    }

                    if (wasConnected || status == BluetoothGatt.GATT_SUCCESS)
                        return;
                } else {
                    if (status != BluetoothGatt.GATT_SUCCESS)
                        Log.d("BT MANAGER", "Error (0x" + Integer.toHexString(status) + "): "
                                /*+ GattError.parseConnectionError(status)*/);
                }
                mCallbacks.onError(gatt.getDevice(), ERROR_CONNECTION_STATE_CHANGE, status);
            }
        }

        @Override
        public final void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BT MANAGER", "Services Discovered");
                if (isRequiredServiceSupported(gatt)) {
                    Log.d("BT MANAGER", "Primary service found");
                    final boolean optionalServicesFound = isOptionalServiceSupported(gatt);
                    if (optionalServicesFound)
                        Log.d("BT MANAGER", "Secondary service found");

                    // Notify the parent activity
                    mCallbacks.onServicesDiscovered(gatt.getDevice(), optionalServicesFound);

                    // Obtain the queue of initialization requests
                    mInitInProgress = true;
                    mInitQueue = initGatt(gatt);

                    // Before we start executing the initialization queue some other tasks need to be done.
                    if (mInitQueue == null)
                        mInitQueue = new LinkedList<>();

                    // Note, that operations are added in reverse order to the front of the queue.

                    // 3. Enable Battery Level notifications if required (if this char. does not exist, this operation will be skipped)
                    if (mCallbacks.shouldEnableBatteryLevelNotifications(gatt.getDevice()))
                        mInitQueue.addFirst(Request.newEnableBatteryLevelNotificationsRequest());
                    // 2. Read Battery Level characteristic (if such does not exist, this will be skipped)
                    mInitQueue.addFirst(Request.newReadBatteryLevelRequest());
                    // 1. On devices running Android 4.3-6.0 the Service Changed characteristic needs to be enabled by the app (for bonded devices)
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N)
                        mInitQueue.addFirst(Request.newEnableServiceChangedIndicationsRequest());

                    mOperationInProgress = false;
                    nextRequest();
                } else {
                    Log.d("BT MANAGER", "Device is not supported");
                    mCallbacks.onDeviceNotSupported(gatt.getDevice());
                    disconnect();
                }
            } else {
                Log.e("BT MANAGER", "onServicesDiscovered error " + status);
                onError(gatt.getDevice(), ERROR_DISCOVERY_SERVICE, status);
            }
        }

        @Override
        public final void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BT MANAGER", "Read Response received from " + characteristic.getUuid() + ", value: " + ParserUtils.parse(characteristic));

                if (isBatteryLevelCharacteristic(characteristic)) {
                    final int batteryValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    Log.d("BT MANAGER", "Battery level received: " + batteryValue + "%");
//                    mBatteryValue = batteryValue;
                    onBatteryValueReceived(gatt, batteryValue);
                    mCallbacks.onBatteryValueReceived(gatt.getDevice(), batteryValue);
                } else {
                    // The value has been read. Notify the manager and proceed with the initialization queue.
                    onCharacteristicRead(gatt, characteristic);
                }
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_NONE) {
                    // This should never happen but it used to: http://stackoverflow.com/a/20093695/2115352
                    Log.e("BT MANAGER", ERROR_AUTH_ERROR_WHILE_BONDED);
                    mCallbacks.onError(gatt.getDevice(), ERROR_AUTH_ERROR_WHILE_BONDED, status);
                }
            } else {
                Log.e("BT MANAGER", "onCharacteristicRead error " + status);
                onError(gatt.getDevice(), ERROR_READ_CHARACTERISTIC, status);
            }
            mOperationInProgress = false;
            nextRequest();
        }

        @Override
        public final void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BT MANAGER", "Data written to " + characteristic.getUuid() + ", value: " + ParserUtils.parse(characteristic));
                // The value has been written. Notify the manager and proceed with the initialization queue.
                onCharacteristicWrite(gatt, characteristic);
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_NONE) {
                    // This should never happen but it used to: http://stackoverflow.com/a/20093695/2115352
                    Log.e("BT MANAGER", ERROR_AUTH_ERROR_WHILE_BONDED);
                    mCallbacks.onError(gatt.getDevice(), ERROR_AUTH_ERROR_WHILE_BONDED, status);
                }
            } else {
                Log.e("BT MANAGER", "onCharacteristicWrite error " + status);
                onError(gatt.getDevice(), ERROR_WRITE_CHARACTERISTIC, status);
            }
            mOperationInProgress = false;
            nextRequest();
        }

        @Override
        public void onDescriptorRead(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BT MANAGER", "Read Response received from descr. " + descriptor.getUuid() + ", value: " + ParserUtils.parse(descriptor));

                // The value has been read. Notify the manager and proceed with the initialization queue.
                onDescriptorRead(gatt, descriptor);
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_NONE) {
                    // This should never happen but it used to: http://stackoverflow.com/a/20093695/2115352
                    Log.e("BT MANAGER", ERROR_AUTH_ERROR_WHILE_BONDED);
                    mCallbacks.onError(gatt.getDevice(), ERROR_AUTH_ERROR_WHILE_BONDED, status);
                }
            } else {
                Log.e("BT MANAGER", "onDescriptorRead error " + status);
                onError(gatt.getDevice(), ERROR_READ_DESCRIPTOR, status);
            }
            mOperationInProgress = false;
            nextRequest();
        }

        @Override
        public final void onDescriptorWrite(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BT MANAGER", "Data written to descr. " + descriptor.getUuid() + ", value: " + ParserUtils.parse(descriptor));

                if (isServiceChangedCCCD(descriptor)) {
                    Log.d("BT MANAGER", "Service Changed notifications enabled");
                } else if (isBatteryLevelCCCD(descriptor)) {
                    final byte[] value = descriptor.getValue();
                    if (value != null && value.length == 2 && value[1] == 0x00) {
                        if (value[0] == 0x01) {
                            Log.d("BT MANAGER", "Battery Level notifications enabled");
                        } else {
                            Log.d("BT MANAGER", "Battery Level notifications disabled");
                        }
                    } else {
                        onDescriptorWrite(gatt, descriptor);
                    }
                } else if (isCCCD(descriptor)) {
                    final byte[] value = descriptor.getValue();
                    if (value != null && value.length == 2 && value[1] == 0x00) {
                        switch (value[0]) {
                            case 0x00:
                                Log.d("BT MANAGER", "Notifications and indications disabled");
                                break;
                            case 0x01:
                                Log.d("BT MANAGER", "Notifications enabled");
                                break;
                            case 0x02:
                                Log.d("BT MANAGER", "Indications enabled");
                                break;
                        }
                    } else {
                        onDescriptorWrite(gatt, descriptor);
                    }
                } else {
                    onDescriptorWrite(gatt, descriptor);
                }
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_NONE) {
                    // This should never happen but it used to: http://stackoverflow.com/a/20093695/2115352
                    Log.e("BT MANAGER", ERROR_AUTH_ERROR_WHILE_BONDED);
                    mCallbacks.onError(gatt.getDevice(), ERROR_AUTH_ERROR_WHILE_BONDED, status);
                }
            } else {
                Log.e("BT MANAGER", "onDescriptorWrite error " + status);
                onError(gatt.getDevice(), ERROR_WRITE_DESCRIPTOR, status);
            }
            mOperationInProgress = false;
            nextRequest();
        }

        @Override
        public final void onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            final String data = ParserUtils.parse(characteristic);

            if (isBatteryLevelCharacteristic(characteristic)) {
                Log.d("BT MANAGER", "Notification received from " + characteristic.getUuid() + ", value: " + data);
                final int batteryValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                Log.d("BT MANAGER", "Battery level received: " + batteryValue + "%");
                mBatteryValue = batteryValue;
                onBatteryValueReceived(gatt, batteryValue);
                mCallbacks.onBatteryValueReceived(gatt.getDevice(), batteryValue);
            } else {
                final BluetoothGattDescriptor cccd = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
                final boolean notifications = cccd == null || cccd.getValue() == null || cccd.getValue().length != 2 || cccd.getValue()[0] == 0x01;

                if (notifications) {
                    Log.d("BT MANAGER", "Notification received from " + characteristic.getUuid() + ", value: " + data);
                    onCharacteristicNotified(gatt, characteristic);
                } else { // indications
                    Log.d("BT MANAGER", "Indication received from " + characteristic.getUuid() + ", value: " + data);
                    onCharacteristicIndicated(gatt, characteristic);
                }
            }
        }

        @Override
        public void onMtuChanged(final BluetoothGatt gatt, final int mtu, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BT MANAGER", "MTU changed to: " + mtu);
                onMtuChanged(mtu);
            } else {
                Log.e("BT MANAGER", "onMtuChanged error: " + status + ", mtu: " + mtu);
                onError(gatt.getDevice(), ERROR_MTU_REQUEST, status);
            }
            mMtu = mtu;
            mOperationInProgress = false;
            nextRequest();
        }

        // @Override

        /**
         * Callback indicating the connection parameters were updated. Works on Android 8+.
         *
         * @param gatt     GATT client involved
         * @param interval Connection interval used on this connection, 1.25ms unit. Valid range is from
         *                 6 (7.5ms) to 3200 (4000ms).
         * @param latency  Slave latency for the connection in number of connection events. Valid range
         *                 is from 0 to 499
         * @param timeout  Supervision timeout for this connection, in 10ms unit. Valid range is from 10
         *                 (0.1s) to 3200 (32s)
         * @param status   {@link BluetoothGatt#GATT_SUCCESS} if the connection has been updated
         *                 successfully
         */
        public void onConnectionUpdated(final BluetoothGatt gatt, final int interval, final int latency, final int timeout, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d("BT MANAGER", "Connection parameters updated (interval: " + (interval * 1.25) + "ms, latency: " + latency + ", timeout: " + (timeout * 10) + "ms)");
                onConnectionUpdated(interval, latency, timeout);
            } else if (status == 0x3b) { // HCI_ERR_UNACCEPT_CONN_INTERVAL
                Log.e("BT MANAGER", "onConnectionUpdated received status: Unacceptable connection interval, interval: " + interval + ", latency: " + latency + ", timeout: " + timeout);
                Log.d("BT MANAGER", "Connection parameters update failed with status: UNACCEPT CONN INTERVAL (0x3b) (interval: " + (interval * 1.25) + "ms, latency: " + latency + ", timeout: " + (timeout * 10) + "ms)");
            } else {
                Log.d("BT MANAGER", "onConnectionUpdated received status: " + status + ", interval: " + interval + ", latency: " + latency + ", timeout: " + timeout);
                Log.d("BT MANAGER", "Connection parameters update failed with status " + status + " (interval: " + (interval * 1.25) + "ms, latency: " + latency + ", timeout: " + (timeout * 10) + "ms)");
                mCallbacks.onError(gatt.getDevice(), ERROR_CONNECTION_PRIORITY_REQUEST, status);
            }
            if (mConnectionPriorityOperationInProgress) {
                mConnectionPriorityOperationInProgress = false;
                mOperationInProgress = false;
                nextRequest();
            }
        }

        /**
         * Executes the next request. If the last element from the initialization queue has been executed
         * the {@link #onDeviceReady()} callback is called.
         */
        @SuppressWarnings("ConstantConditions")
        private void nextRequest() {
            if (mOperationInProgress)
                return;

            // Get the first request from the init queue
            Request request = mInitQueue != null ? mInitQueue.poll() : null;

            // Are we done with initializing?
            if (request == null) {
                if (mInitInProgress) {
                    mInitQueue = null; // release the queue
                    mInitInProgress = false;
                    onDeviceReady();
                }
                // If so, we can continue with the task queue
                request = mTaskQueue.poll();
                if (request == null) {
                    // Nothing to be done for now
                    return;
                }
            }

            mOperationInProgress = true;
            boolean result = false;
            switch (request.type) {
                case CREATE_BOND: {
                    result = internalCreateBond();
                    break;
                }
                case READ: {
                    result = internalReadCharacteristic(request.characteristic);
                    break;
                }
                case WRITE: {
                    final BluetoothGattCharacteristic characteristic = request.characteristic;
                    characteristic.setValue(request.data);
                    characteristic.setWriteType(request.writeType);
                    result = internalWriteCharacteristic(characteristic);
                    break;
                }
                case READ_DESCRIPTOR: {
                    result = internalReadDescriptor(request.descriptor);
                    break;
                }
                case WRITE_DESCRIPTOR: {
                    final BluetoothGattDescriptor descriptor = request.descriptor;
                    descriptor.setValue(request.data);
                    result = internalWriteDescriptor(descriptor);
                    break;
                }
                case ENABLE_NOTIFICATIONS: {
                    result = internalEnableNotifications(request.characteristic);
                    break;
                }
                case ENABLE_INDICATIONS: {
                    result = internalEnableIndications(request.characteristic);
                    break;
                }
                case DISABLE_NOTIFICATIONS: {
                    result = internalDisableNotifications(request.characteristic);
                    break;
                }
                case DISABLE_INDICATIONS: {
                    result = internalDisableIndications(request.characteristic);
                    break;
                }
                case READ_BATTERY_LEVEL: {
                    result = internalReadBatteryLevel();
                    break;
                }
                case ENABLE_BATTERY_LEVEL_NOTIFICATIONS: {
                    result = internalSetBatteryNotifications(true);
                    break;
                }
                case DISABLE_BATTERY_LEVEL_NOTIFICATIONS: {
                    result = internalSetBatteryNotifications(false);
                    break;
                }
                case ENABLE_SERVICE_CHANGED_INDICATIONS: {
                    result = ensureServiceChangedEnabled();
                    break;
                }
                case REQUEST_MTU: {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        result = internalRequestMtu(request.value);
                    }
                    break;
                }
                case REQUEST_CONNECTION_PRIORITY: {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        mConnectionPriorityOperationInProgress = true;
                        result = internalRequestConnectionPriority(request.value);
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        result = internalRequestConnectionPriority(request.value);
                        // There is no callback for requestConnectionPriority(...) before Android Oreo.\
                        // Let's give it some time to finish as the request is an asynchronous operation.
                        if (result) {
                            mHandler.postDelayed(() -> {
                                mOperationInProgress = false;
                                nextRequest();
                            }, 100);
                        }
                    }
                    break;
                }
            }
            // The result may be false if given characteristic or descriptor were not found on the device,
            // or the feature is not supported on the Android.
            // In that case, proceed with next operation and ignore the one that failed.
            if (!result) {
                mConnectionPriorityOperationInProgress = false;
                mOperationInProgress = false;
                nextRequest();
            }
        }

        /**
         * Returns true if this descriptor is from the Service Changed characteristic.
         *
         * @param descriptor the descriptor to be checked
         * @return true if the descriptor belongs to the Service Changed characteristic
         */
        private boolean isServiceChangedCCCD(final BluetoothGattDescriptor descriptor) {
            return descriptor != null && SERVICE_CHANGED_CHARACTERISTIC.equals(descriptor.getCharacteristic().getUuid());
        }

        /**
         * Returns true if the characteristic is the Battery Level characteristic.
         *
         * @param characteristic the characteristic to be checked
         * @return true if the characteristic is the Battery Level characteristic.
         */
        private boolean isBatteryLevelCharacteristic(final BluetoothGattCharacteristic characteristic) {
            return characteristic != null && BATTERY_LEVEL_CHARACTERISTIC.equals(characteristic.getUuid());
        }

        /**
         * Returns true if this descriptor is from the Battery Level characteristic.
         *
         * @param descriptor the descriptor to be checked
         * @return true if the descriptor belongs to the Battery Level characteristic
         */
        private boolean isBatteryLevelCCCD(final BluetoothGattDescriptor descriptor) {
            return descriptor != null && BATTERY_LEVEL_CHARACTERISTIC.equals(descriptor.getCharacteristic().getUuid());
        }

        /**
         * Returns true if this descriptor is a Client Characteristic Configuration descriptor (CCCD).
         *
         * @param descriptor the descriptor to be checked
         * @return true if the descriptor is a CCCD
         */
        private boolean isCCCD(final BluetoothGattDescriptor descriptor) {
            return descriptor != null && CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID.equals(descriptor.getUuid());
        }
    }

    private static final int PAIRING_VARIANT_PIN = 0;
    private static final int PAIRING_VARIANT_PASSKEY = 1;
    private static final int PAIRING_VARIANT_PASSKEY_CONFIRMATION = 2;
    private static final int PAIRING_VARIANT_CONSENT = 3;
    private static final int PAIRING_VARIANT_DISPLAY_PASSKEY = 4;
    private static final int PAIRING_VARIANT_DISPLAY_PIN = 5;
    private static final int PAIRING_VARIANT_OOB_CONSENT = 6;

    protected String pairingVariantToString(final int variant) {
        switch (variant) {
            case PAIRING_VARIANT_PIN:
                return "PAIRING_VARIANT_PIN";
            case PAIRING_VARIANT_PASSKEY:
                return "PAIRING_VARIANT_PASSKEY";
            case PAIRING_VARIANT_PASSKEY_CONFIRMATION:
                return "PAIRING_VARIANT_PASSKEY_CONFIRMATION";
            case PAIRING_VARIANT_CONSENT:
                return "PAIRING_VARIANT_CONSENT";
            case PAIRING_VARIANT_DISPLAY_PASSKEY:
                return "PAIRING_VARIANT_DISPLAY_PASSKEY";
            case PAIRING_VARIANT_DISPLAY_PIN:
                return "PAIRING_VARIANT_DISPLAY_PIN";
            case PAIRING_VARIANT_OOB_CONSENT:
                return "PAIRING_VARIANT_OOB_CONSENT";
            default:
                return "UNKNOWN";
        }
    }

    protected String bondStateToString(final int state) {
        switch (state) {
            case BluetoothDevice.BOND_NONE:
                return "BOND_NONE";
            case BluetoothDevice.BOND_BONDING:
                return "BOND_BONDING";
            case BluetoothDevice.BOND_BONDED:
                return "BOND_BONDED";
            default:
                return "UNKNOWN";
        }
    }

    protected String getWriteType(final int type) {
        switch (type) {
            case BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT:
                return "WRITE REQUEST";
            case BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE:
                return "WRITE COMMAND";
            case BluetoothGattCharacteristic.WRITE_TYPE_SIGNED:
                return "WRITE SIGNED";
            default:
                return "UNKNOWN: " + type;
        }
    }

    /**
     * Converts the connection state to String value
     *
     * @param state the connection state
     * @return state as String
     */
    protected String stateToString(final int state) {
        switch (state) {
            case BluetoothProfile.STATE_CONNECTED:
                return "CONNECTED";
            case BluetoothProfile.STATE_CONNECTING:
                return "CONNECTING";
            case BluetoothProfile.STATE_DISCONNECTING:
                return "DISCONNECTING";
            default:
                return "DISCONNECTED";
        }
    }

}
