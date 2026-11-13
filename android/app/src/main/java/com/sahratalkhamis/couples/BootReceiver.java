package com.sahratalkhamis.couples;
import android.content.*;
public class BootReceiver extends BroadcastReceiver {
 @Override public void onReceive(Context c,Intent i){if(!Intent.ACTION_BOOT_COMPLETED.equals(i.getAction()))return;SharedPreferences p=c.getSharedPreferences("sahra",0);if(p.getBoolean("reminder",false))AlarmReceiver.scheduleReminder(c,p.getInt("hour",21),p.getInt("minute",0));long end=p.getLong("dndEnd",0);if(end>System.currentTimeMillis())AlarmReceiver.scheduleDnd(c,end);else if(end>0)AlarmReceiver.restoreDnd(c);}
}
