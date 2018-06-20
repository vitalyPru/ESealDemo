package ru.spb.gamma.esealdemo;

import android.app.Activity;
import android.graphics.Color;
import android.widget.CheckedTextView;
import android.widget.ProgressBar;
import android.widget.TextView;

public class BatteryDispItem extends DispItem {
    final private int text_resource_id;
    final private int value_resource_id;
    public BatteryDispItem( String _str, int _text_id, int _value_id) {
        super( _str);
        text_resource_id = _text_id;
        value_resource_id = _value_id;

    }

    public void  display_data(Activity activity, String value) {
        TextView text_control = activity.findViewById((int)text_resource_id);
        ProgressBar progress_bar = activity.findViewById((int)value_resource_id);
        final int val =  Integer.parseInt(value);

        activity.runOnUiThread(() -> {
            text_control.setText(value);
            progress_bar.setProgress(val);
            if ( val < 20) {
                progress_bar.setBackgroundColor(Color.RED);
            } else  if ( val < 60) {
                progress_bar.setBackgroundColor(Color.YELLOW);
            } else {
                progress_bar.setBackgroundColor(Color.GREEN);
            }
        });
    }

}
