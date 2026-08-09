package com.alfahdtv.app;

import android.app.NotificationChannel;import android.app.NotificationManager;import android.app.PendingIntent;import android.content.BroadcastReceiver;import android.content.Context;import android.content.Intent;import android.content.SharedPreferences;import android.graphics.Color;import android.os.Build;
import java.io.BufferedReader;import java.io.InputStreamReader;import java.net.HttpURLConnection;import java.net.URL;import java.nio.charset.StandardCharsets;import java.util.regex.Matcher;import java.util.regex.Pattern;

public final class ContentCheckReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context,Intent intent){final PendingResult pending=goAsync();new Thread(()->{try{check(context);}finally{pending.finish();}}).start();}
    private void check(Context c){
        if(!c.getSharedPreferences("settings",0).getBoolean("notifications",true))return;
        try{
            String endpoint="https://akwam-stream-fetcher.meroo3292.workers.dev/?action=genre&genre="+java.net.URLEncoder.encode("https://akwam.it/movies","UTF-8");
            HttpURLConnection h=(HttpURLConnection)new URL(endpoint).openConnection();h.setConnectTimeout(9000);h.setReadTimeout(9000);h.setRequestProperty("Accept","application/json");
            String body;try(BufferedReader r=new BufferedReader(new InputStreamReader(h.getInputStream(),StandardCharsets.UTF_8))){body=r.lines().collect(java.util.stream.Collectors.joining());}
            Matcher m=Pattern.compile("\\\"title\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(body);if(!m.find())return;String title=m.group(1),key="latest_title";SharedPreferences p=c.getSharedPreferences("content",0);String old=p.getString(key,"");p.edit().putString(key,title).apply();if(!old.isEmpty()&&!old.equals(title))notify(c,"جديد على الفهد TV",title);
        }catch(Exception ignored){}
    }
    private void notify(Context c,String title,String text){
        NotificationManager n=(NotificationManager)c.getSystemService(Context.NOTIFICATION_SERVICE);String channel="new_content";
        if(Build.VERSION.SDK_INT>=26){NotificationChannel ch=new NotificationChannel(channel,"المحتوى الجديد",NotificationManager.IMPORTANCE_DEFAULT);ch.setDescription("تنبيهات الأفلام والحلقات الجديدة");n.createNotificationChannel(ch);}
        PendingIntent open=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder b=Build.VERSION.SDK_INT>=26?new android.app.Notification.Builder(c,channel):new android.app.Notification.Builder(c);b.setSmallIcon(R.drawable.ic_launcher).setContentTitle(title).setContentText(text).setAutoCancel(true).setContentIntent(open).setColor(Color.rgb(239,35,60));n.notify(601,b.build());
    }
}
