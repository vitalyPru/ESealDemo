package ru.spb.gamma.esealdemo;

import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.support.v4.util.Pair;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class ParserUtils {
    protected final static char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String parse(final BluetoothGattCharacteristic characteristic) {
        return parse(characteristic.getValue());
    }

    public static String parse(final BluetoothGattDescriptor descriptor) {
        return parse(descriptor.getValue());
    }

    public static String parse(final byte[] data) {
        if (data == null || data.length == 0)
            return "";

        final char[] out = new char[data.length * 3 - 1];
        for (int j = 0; j < data.length; j++) {
            int v = data[j] & 0xFF;
            out[j * 3] = HEX_ARRAY[v >>> 4];
            out[j * 3 + 1] = HEX_ARRAY[v & 0x0F];
            if (j != data.length - 1)
                out[j * 3 + 2] = '-';
        }
        return "(0x) " + new String(out);
    }
    public static String parseDebug(final byte[] data) {
        if (data == null || data.length == 0)
            return "";

        final char[] out = new char[data.length * 2];
        for (int j = 0; j < data.length; j++) {
            int v = data[j] & 0xFF;
            out[j * 2] = HEX_ARRAY[v >>> 4];
            out[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
        }
        return "0x" + new String(out);
    }

    public static final  ArrayList<DispItem> params = new ArrayList<DispItem>();
    static {
        params.add(new TextDispItem("board=", R.id.board_name));
        params.add(new TextDispItem("version=", R.id.board_version));
        params.add(new TextDispItem("date=", R.id.board_data));
        params.add(new TextDispItem("gpsLat=", R.id.gps_lat));
        params.add(new TextDispItem("gpsLon=", R.id.gps_lon));
        params.add(new TextDispItem("gpsAlt=", R.id.gps_alt));
        params.add(new TextDispItem("loraCnt=", R.id.lora_tamper));
        params.add(new BatteryDispItem("loraBat=", R.id.lora_battery, R.id.lora_battery_gauge));
        params.add(new TextDispItem("loraHall1=", R.id.loa_hall_1));
        params.add(new TextDispItem("loraHall2=", R.id.lora_hall_2));
        params.add(new TextDispItem("loraRSSI=", R.id.lora_rssi));
        params.add(new FlagDispItem("wake=", R.id.wake_flag));
        params.add(new FlagDispItem("arm=", R.id.arm_flag));
        params.add(new FlagDispItem("alarm=", R.id.alarm_flag));
        params.add(new FlagDispItem("alert=", R.id.tamper_flag));
        params.add(new FlagDispItem("cable=", R.id.cable_flag));
        params.add(new BatteryDispItem("bat=", R.id.battery_value , R.id.battery_gauge));
        params.add(new TextDispItem("getID=", R.id.wire_id));
        params.add(new TextDispItem("getInvoice=", R.id.doc_id));
    };

    public static Queue<Pair<DispItem,String>> parse_data(String data)
    {

        Queue<Pair<DispItem,String>> list = new LinkedList<Pair<DispItem,String>>();
        String[] lines = data.split("\\r\\n");
        for ( String line : lines) {
            for ( DispItem  item: params) {
                boolean found = line.startsWith( item.protocol_string);
                int len = item.protocol_string.length();
                if (found) {
                    list.add(new Pair(item, line.substring(len)));
                    break;
                }
            }
        }
        return list;
    }

}
