package ru.spb.gamma.esealdemo;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import no.nordicsemi.android.support.v18.scanner.ScanResult;

public class DeviceListAdapter extends BaseAdapter {
    private static final int TYPE_TITLE = 0;
    private static final int TYPE_ITEM = 1;
    private static final int TYPE_EMPTY = 2;

    //private final ArrayList<ExtendedBluetoothDevice> mListBondedValues = new ArrayList<>();
    private final ArrayList<ExtendedBluetoothDevice> mListValues = new ArrayList<>();
    private final Context mContext;

    public DeviceListAdapter(final Context context) {
        mContext = context;
    }

    /**
     * Sets a list of bonded devices.
     * @param devices list of bonded devices.
     */
    public void addBondedDevices(final Set<BluetoothDevice> devices) {
        //final List<ExtendedBluetoothDevice> bondedDevices = mListBondedValues;
        //for (BluetoothDevice device : devices) {
          //  bondedDevices.add(new ExtendedBluetoothDevice(device));
        //}
        notifyDataSetChanged();
    }

    /**
     * Updates the list of not bonded devices.
     * @param results list of results from the scanner
     */
    public void update(final List<ScanResult> results) {
        for (final ScanResult result : results) {
            final ExtendedBluetoothDevice device = findDevice(result);
            if (device == null) {
                mListValues.add(new ExtendedBluetoothDevice(result));
            } else {
                device.name = result.getScanRecord() != null ? result.getScanRecord().getDeviceName() : null;
                device.rssi = result.getRssi();
            }
        }
        notifyDataSetChanged();
    }

    private ExtendedBluetoothDevice findDevice(final ScanResult result) {
//        for (final ExtendedBluetoothDevice device : mListBondedValues)
//            if (device.matches(result))
//                return device;
        for (final ExtendedBluetoothDevice device : mListValues)
            if (device.matches(result))
                return device;
        return null;
    }

    public void clearDevices() {
        mListValues.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
  //      final int bondedCount = mListBondedValues.size() + 1; // 1 for the title
        final int availableCount =  mListValues.size() ; // 1 for title, 1 for empty text
 //       if (bondedCount == 1)
            return availableCount;
 //       return bondedCount + availableCount;
    }

    @Override
    public Object getItem(int position) {
        //final int bondedCount = mListBondedValues.size() + 1; // 1 for the title

                return mListValues.get(position);

    }

    @Override
    public int getViewTypeCount() {
        return 3;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return getItemViewType(position) == TYPE_ITEM;
    }

    @Override
    public int getItemViewType(int position) {
//        if (position == 0)
//            return TYPE_TITLE;

//        if (!mListBondedValues.isEmpty() && position == mListBondedValues.size() + 1)
//            return TYPE_TITLE;

//        if (position == getCount() - 1 && mListValues.isEmpty())
//            return TYPE_EMPTY;

        return TYPE_ITEM;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View oldView, ViewGroup parent) {
        final LayoutInflater inflater = LayoutInflater.from(mContext);
        final int type = getItemViewType(position);

        View view = oldView;
        switch (type) {
            case TYPE_EMPTY:
                if (view == null) {
                    view = inflater.inflate(R.layout.device_list_empty, parent, false);
                }
                break;
            case TYPE_TITLE:
                if (view == null) {
                    view = inflater.inflate(R.layout.device_list_title, parent, false);
                }
                final TextView title = (TextView) view;
                title.setText((Integer) getItem(position));
                break;
            default:
                if (view == null) {
                    view = inflater.inflate(R.layout.device_list_row, parent, false);
                    final ViewHolder holder = new ViewHolder();
                    holder.name = view.findViewById(R.id.name);
                    holder.address = view.findViewById(R.id.address);
                    holder.rssi = view.findViewById(R.id.rssi);
                    view.setTag(holder);
                }

                final ExtendedBluetoothDevice device = (ExtendedBluetoothDevice) getItem(position);
                final ViewHolder holder = (ViewHolder) view.getTag();
                final String name = device.name;
                holder.name.setText(name != null ? name : mContext.getString(R.string.not_available));
                holder.address.setText(device.device.getAddress());
                if (!device.isBonded || device.rssi != ExtendedBluetoothDevice.NO_RSSI) {
                    final int rssiPercent = (int) (100.0f * (127.0f + device.rssi) / (127.0f + 20.0f));
                    holder.rssi.setImageLevel(rssiPercent);
                    holder.rssi.setVisibility(View.VISIBLE);
                } else {
                    holder.rssi.setVisibility(View.GONE);
                }
                break;
        }

        return view;
    }

    private class ViewHolder {
        private TextView name;
        private TextView address;
        private ImageView rssi;
    }
}
