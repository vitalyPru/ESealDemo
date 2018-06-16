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

    public static final  ArrayList<Pair<String, Integer>> params = new ArrayList<Pair<String, Integer>>();
    static {
        params.add(new Pair("board=", R.id.board_name));
        params.add(new Pair("version=", R.id.board_version));
        params.add(new Pair("date=", R.id.board_data));
        params.add(new Pair("gpsLat=", R.id.gps_lat));
        params.add(new Pair("gpsLon=", R.id.gps_lon));
        params.add(new Pair("gpsAlt=", R.id.gps_alt));
        params.add(new Pair("loraCnt=", R.id.lora_tamper));
        params.add(new Pair("lotaBat=", R.id.lora_battery));
        params.add(new Pair("lotaHall1=", R.id.loa_hall_1));
        params.add(new Pair("lotaHall2=", R.id.lora_hall_2));
        params.add(new Pair("lotaRSSI=", R.id.lora_rssi));
        params.add(new Pair("wake=", R.id.wake_flag));
        params.add(new Pair("arm=", R.id.arm_flag));
        params.add(new Pair("alarm=", R.id.alarm_flag));
        params.add(new Pair("alert=", R.id.tamper_flag));
        params.add(new Pair("cable=", R.id.cable_flag));
        params.add(new Pair("bat=", R.id.battery_value));

    };

    public static Queue<Pair<Integer,String>> parse_data(String data)
    {

        Queue<Pair<Integer,String>> list = new LinkedList<Pair<Integer,String>>();
        String[] lines = data.split("\\r\\n");
        for ( String line : lines) {
            for ( Pair<String, Integer> pair : params) {
                boolean found = line.startsWith(pair.first);
                int len = pair.first.length();
                if (found) {
                    list.add(new Pair(pair.second, line.substring(len)));
                    break;
                }
            }
        }
        return list;
    }

}
