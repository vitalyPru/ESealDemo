package ru.spb.gamma.esealdemo;

import android.app.Activity;
import android.graphics.Color;
import android.widget.CheckedTextView;

public class AlarmFlagDispItem extends DispItem {
    final private int resource_id;
    public AlarmFlagDispItem( String _str, int res_id) {
        super( _str);
        resource_id = res_id;

    }

    public void  display_data(Activity activity, String value) {
        CheckedTextView control = activity.findViewById((int)resource_id);
        activity.runOnUiThread(() -> {
            if ( value.equals("1") ) {
                control.setChecked(true);
                control.setBackgroundColor(Color.RED);
            } else {
                control.setChecked(false);
                control.setBackgroundColor(Color.WHITE);
            }
        });
    }
}
