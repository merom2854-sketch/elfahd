package com.alfahdtv.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Sends an anonymous install heartbeat. No account, phone number, or device identifier is collected. */
final class UsageReporter {
    private static final String SUPABASE_URL = "https://zjmskrlsqfvxsqiypkhe.supabase.co";
    private static final String SUPABASE_KEY = "sb_publishable_JCudJEFExsvmnnAKs1iy4w_B9R9ca4A";
    private UsageReporter() {}

    static void register(Context context) {
        SharedPreferences prefs = context.getSharedPreferences("usage", 0);
        String installId = prefs.getString("install_id", null);
        if (installId == null) {
            installId = UUID.randomUUID().toString();
            prefs.edit().putString("install_id", installId).apply();
        }
        final String id = installId;
        new Thread(() -> send(id), "usage-heartbeat").start();
    }

    private static void send(String installId) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(SUPABASE_URL + "/rest/v1/rpc/register_install").openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(8000); connection.setReadTimeout(8000); connection.setDoOutput(true);
            connection.setRequestProperty("apikey", SUPABASE_KEY);
            connection.setRequestProperty("Authorization", "Bearer " + SUPABASE_KEY);
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setRequestProperty("Prefer", "return=minimal");
            String body = "{\"p_install_id\":\"" + installId + "\",\"p_app_version\":\"" + BuildConfig.VERSION_NAME + "\",\"p_platform\":\"android\"}";
            try (OutputStream output = connection.getOutputStream()) { output.write(body.getBytes(StandardCharsets.UTF_8)); }
            connection.getResponseCode();
        } catch (Exception ignored) { }
        finally { if (connection != null) connection.disconnect(); }
    }
}
