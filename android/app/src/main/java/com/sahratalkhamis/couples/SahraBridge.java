package com.sahratalkhamis.couples;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
public class SahraBridge {
 private final MainActivity activity;
 SahraBridge(MainActivity activity){this.activity=activity;}
 @JavascriptInterface public void setReminder(int h,int m){if(h<0||h>23||m<0||m>59)return;activity.getSharedPreferences("sahra",0).edit().putBoolean("reminder",true).putInt("hour",h).putInt("minute",m).apply();AlarmReceiver.scheduleReminder(activity,h,m);}
 @JavascriptInterface public void cancelReminder(){activity.getSharedPreferences("sahra",0).edit().putBoolean("reminder",false).apply();AlarmReceiver.cancel(activity,"reminder",10);}
 @JavascriptInterface public boolean startDnd(){NotificationManager nm=(NotificationManager)activity.getSystemService(Context.NOTIFICATION_SERVICE);if(!nm.isNotificationPolicyAccessGranted()){activity.startActivity(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));return false;}int previous=nm.getCurrentInterruptionFilter();long end=System.currentTimeMillis()+7200000;activity.getSharedPreferences("sahra",0).edit().putInt("dndPrevious",previous).putLong("dndEnd",end).apply();nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_NONE);AlarmReceiver.scheduleDnd(activity,end);return true;}
 @JavascriptInterface public void endDnd(){AlarmReceiver.restoreDnd(activity);}
}
