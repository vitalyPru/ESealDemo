package ru.spb.gamma.esealdemo;

import android.app.Activity;
import android.graphics.Color;
import android.widget.CheckedTextView;
import android.widget.TextView;

import java.text.SimpleDateFormat;

public class AlarmFlagDispItem extends DispItem {
    final private int resource_id;
    final private int date_resource_id;
    public AlarmFlagDispItem( String _str, int res_id, int date_id) {
        super( _str);
        resource_id = res_id;
        date_resource_id = date_id;

    }

    public void  display_data(Activity activity, String value) {
        CheckedTextView control = activity.findViewById((int)resource_id);
        TextView date = activity.findViewById((int)date_resource_id);
        activity.runOnUiThread(() -> {
            String validDate = date.getText().toString();
            if ( value.equals("1") ) {
                if (validDate.equals("")) {
                    java.util.Date time=new java.util.Date();
                    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    String dateandTime = sdf.format(time);
                    date.setText(dateandTime);
                }
                control.setChecked(true);
                control.setBackgroundColor(Color.RED);
            } else {
                control.setChecked(false);
                control.setBackgroundColor(Color.WHITE);
            }
        });
    }
}
