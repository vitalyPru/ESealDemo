package ru.spb.gamma.esealdemo;

import android.app.Activity;
import android.widget.TextView;

import ru.spb.gamma.esealdemo.DispItem;

public class TextDispItem extends DispItem {

    final private int resource_id;
    public TextDispItem( String _str, int res_id) {
        super( _str);
        resource_id = res_id;

    }

    public void  display_data(Activity activity, String value) {
        TextView control = activity.findViewById((int)resource_id);
        activity.runOnUiThread(() -> {
            control.setText(value);
        });
    }

}
