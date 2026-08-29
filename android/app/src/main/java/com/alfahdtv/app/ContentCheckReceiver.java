package com.alfahdtv.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Build;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class ContentCheckReceiver extends BroadcastReceiver {
    private static final String[] NOTIFICATIONS_APIS = {
            "https://elfahd-tv.vercel.app/data/notifications.json",
            "https://raw.githubusercontent.com/merom2854-sketch/elfahd/main/data/notifications.json"
    };

    @Override public void onReceive(Context context, Intent intent) {
        final PendingResult pending = goAsync();
        new Thread(() -> { try { check(context); } finally { pending.finish(); } }).start();
    }

    private void check(Context context) {
        if (!context.getSharedPreferences("settings", 0).getBoolean("notifications", true)) return;
        if (checkRemoteNotification(context)) return;
        checkLatestContent(context);
    }

    private boolean checkRemoteNotification(Context context) {
        for (String endpoint : NOTIFICATIONS_APIS) {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL(endpoint + "?ts=" + System.currentTimeMillis()).openConnection();
                connection.setConnectTimeout(9000);
                connection.setReadTimeout(9000);
                connection.setRequestProperty("Accept", "application/json");
                connection.setRequestProperty("Cache-Control", "no-cache");
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                    StringBuilder body = new StringBuilder(); String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                    JSONObject data = new JSONObject(body.toString()).optJSONObject("data");
                    if (data == null) return false;
                    String id = data.optString("id", "");
                    if (id.isEmpty()) return false;
                    SharedPreferences preferences = context.getSharedPreferences("content", 0);
                    String old = preferences.getString("notification_id", "");
                    preferences.edit().putString("notification_id", id).apply();
                    if (old.isEmpty() || old.equals(id)) return false;
                    notify(context, data.optString("title", "الفهد TV"), data.optString("body", "إضافة جديدة متاحة الآن"));
                    return true;
                } finally { connection.disconnect(); }
            } catch (Exception ignored) { }
        }
        return false;
    }

    private void checkLatestContent(Context context) {
        try {
            String endpoint = "https://akwam-stream-fetcher.meroo3292.workers.dev/?action=genre&genre=" + java.net.URLEncoder.encode("https://akwam.it/movies", "UTF-8");
            HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setConnectTimeout(9000); connection.setReadTimeout(9000); connection.setRequestProperty("Accept", "application/json");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder(); String line;
                while ((line = reader.readLine()) != null) body.append(line);
                org.json.JSONArray data = new JSONObject(body.toString()).optJSONArray("data");
                JSONObject first = data == null ? null : (data.optJSONObject(1) != null ? data.optJSONObject(1) : data.optJSONObject(0));
                String title = first == null ? "" : first.optString("title", "");
                if (title.isEmpty()) return;
                SharedPreferences preferences = context.getSharedPreferences("content", 0); String old = preferences.getString("latest_title", "");
                preferences.edit().putString("latest_title", title).apply(); if (!old.isEmpty() && !old.equals(title)) notify(context, "جديد على الفهد TV", title);
            } finally { connection.disconnect(); }
        } catch (Exception ignored) { }
    }

    private void notify(Context context, String title, String text) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        String channel = "new_content";
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel notificationChannel = new NotificationChannel(channel, "المحتوى الجديد", NotificationManager.IMPORTANCE_DEFAULT);
            notificationChannel.setDescription("تنبيهات الأفلام والإضافات الجديدة"); manager.createNotificationChannel(notificationChannel);
        }
        PendingIntent open = PendingIntent.getActivity(context, 0, new Intent(context, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        android.app.Notification.Builder builder = Build.VERSION.SDK_INT >= 26 ? new android.app.Notification.Builder(context, channel) : new android.app.Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_launcher).setContentTitle(title).setContentText(text).setAutoCancel(true).setContentIntent(open).setColor(Color.rgb(239, 35, 60));
        manager.notify(601, builder.build());
    }
}
