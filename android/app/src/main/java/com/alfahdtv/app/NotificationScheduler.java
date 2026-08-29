package com.alfahdtv.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

final class NotificationScheduler {
    static void schedule(Context c){
        if(!c.getSharedPreferences("settings",0).getBoolean("notifications",true))return;
        AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        Intent i=new Intent(c,ContentCheckReceiver.class);
        PendingIntent p=PendingIntent.getBroadcast(c,404,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        // Android's practical minimum for a repeating background alarm is 15 minutes.
        // A foreground check is also triggered by both activities when they open.
        a.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,android.os.SystemClock.elapsedRealtime()+60_000,15*60*1000L,p);
    }
    static void checkNow(Context c){
        if(!c.getSharedPreferences("settings",0).getBoolean("notifications",true))return;
        c.sendBroadcast(new Intent(c,ContentCheckReceiver.class));
    }
    static void cancel(Context c){AlarmManager a=(AlarmManager)c.getSystemService(Context.ALARM_SERVICE);PendingIntent p=PendingIntent.getBroadcast(c,404,new Intent(c,ContentCheckReceiver.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);a.cancel(p);}
}
