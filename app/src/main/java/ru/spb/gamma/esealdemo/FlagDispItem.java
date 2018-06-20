package ru.spb.gamma.esealdemo;

import android.app.Activity;
import android.widget.CheckedTextView;
import android.widget.TextView;

public class FlagDispItem extends DispItem {
    final private int resource_id;
    public FlagDispItem( String _str, int res_id) {
        super( _str);
        resource_id = res_id;

    }

    public void  display_data(Activity activity, String value) {
        CheckedTextView control = activity.findViewById((int)resource_id);
        activity.runOnUiThread(() -> {
            if ( value.equals("1") ) {
                control.setChecked(true);
            } else {
                control.setChecked(false);
            }
        });
    }

}
