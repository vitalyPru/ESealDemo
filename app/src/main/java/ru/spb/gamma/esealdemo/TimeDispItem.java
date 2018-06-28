package ru.spb.gamma.esealdemo;

import android.app.Activity;
import android.graphics.Color;
import android.widget.CheckedTextView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;

public class TimeDispItem extends DispItem {
    final private int time_resource_id;
//    final private int flag_resource_id;
    public TimeDispItem( String _str, int _time_id  ) {
        super( _str);
        time_resource_id = _time_id;
//        flag_resource_id = _flag_id;

    }

    public void  display_data(Activity activity, String value) {
        TextView text_control = activity.findViewById((int)time_resource_id);
//        final int val =  Integer.parseInt(value);
//        if (val > 1500000000 )
        long timeStamp = Long.parseLong(value);
        String dateandTime = "";
        if (timeStamp > 1500000000 ) {
            java.util.Date time = new java.util.Date((long) timeStamp * 1000);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            dateandTime = sdf.format(time);
        }
        final String dandt = dateandTime;
        activity.runOnUiThread(() -> {
            text_control.setText(dandt);
//            progress_bar.setProgress(val);
//            if ( val < 20) {
//                progress_bar.setBackgroundColor(Color.RED);
//            } else  if ( val < 60) {
//                progress_bar.setBackgroundColor(Color.YELLOW);
//            } else {
//                progress_bar.setBackgroundColor(Color.GREEN);
//            }
        });
    }
}
