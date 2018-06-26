package ru.spb.gamma.esealdemo;

import android.app.Activity;
import android.widget.TextView;

import ru.spb.gamma.esealdemo.DispItem;

public class MoveDispItem extends DispItem {

    final private int resource_id;
    public MoveDispItem(String _str, int res_id) {
            super( _str);
        resource_id = res_id;

    }

    public void  display_data(Activity activity, String value) {
        TextView control = activity.findViewById((int)resource_id);
        String svalue = "";
        if (value.startsWith("0")) {
            svalue = "нет";
        } else if (value.startsWith("1")) {
            svalue = "ось X";
        } else if (value.startsWith("2")) {
            svalue = "ось Y";
        } else if (value.startsWith("3")) {
            svalue = "ось Z";
        }
        final String ssvalue = svalue;

        activity.runOnUiThread(() -> {
            control.setText(ssvalue);
        });
    }


}
