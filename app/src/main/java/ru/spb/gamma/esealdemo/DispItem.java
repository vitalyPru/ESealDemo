package ru.spb.gamma.esealdemo;

import android.app.Activity;

public abstract class DispItem {
    protected final String protocol_string;
//    protected final Activity activity;

    public DispItem(  String _str ){
//        activity = act;
        protocol_string = _str;
    }

    abstract public void  display_data(Activity activity, String value);

}
