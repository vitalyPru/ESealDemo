package ru.spb.gamma.esealdemo;

import android.app.Activity;
import android.widget.TextView;

import java.text.SimpleDateFormat;

public class GpsDateDispItem extends DispItem {
    final private int resource_id;
    final private int date_resource_id;
    public GpsDateDispItem( String _str, int res_id, int dale_id) {
        super( _str);
        resource_id = res_id;
        date_resource_id = dale_id;

    }

    public void  display_data(Activity activity, String value) {
        TextView control = activity.findViewById((int)resource_id);
        TextView date = activity.findViewById((int)date_resource_id);
        java.util.Date time=new java.util.Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        String dateandTime = sdf.format(time);
        activity.runOnUiThread(() -> {
            control.setText(value);
            date.setText(dateandTime);
        });
    }

}
