package com.alfahdtv.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class UpdateChecker {
    private static final String RELEASE_URL = "https://api.github.com/repos/merom2854-sketch/elfahd/releases/latest";
    private static final String FALLBACK_URL = "https://elfahd-tv.vercel.app/download/update.json";
    private static final long PROMPT_INTERVAL_MS = 24L * 60L * 60L * 1000L;

    private UpdateChecker() {}

    static void check(Activity activity, boolean manual) {
        new Thread(() -> {
            try {
                String[] result = tryFetch(RELEASE_URL, true);
                if (result == null || result[0].isEmpty()) result = tryFetch(FALLBACK_URL, false);
                if (result == null || result[0].isEmpty() || !newer(result[0], BuildConfig.VERSION_NAME)) return;

                String tag = result[0];
                String apk = result[1];
                SharedPreferences preferences = activity.getSharedPreferences("updates", 0);
                long lastPrompt = preferences.getLong("last_prompt", 0L);
                if (!manual && tag.equals(preferences.getString("seen", ""))
                        && System.currentTimeMillis() - lastPrompt < PROMPT_INTERVAL_MS) return;
                preferences.edit().putString("seen", tag)
                        .putLong("last_prompt", System.currentTimeMillis()).apply();

                String finalApk = apk;
                new Handler(Looper.getMainLooper()).post(() -> new AlertDialog.Builder(activity)
                        .setTitle("تحديث جديد للفهد TV")
                        .setMessage("الإصدار " + tag + " متاح الآن.")
                        .setNegativeButton("لاحقًا", null)
                        .setPositiveButton("تحميل التحديث", (dialog, which) -> activity.startActivity(
                                new Intent(Intent.ACTION_VIEW, Uri.parse(finalApk.isEmpty()
                                        ? "https://github.com/merom2854-sketch/elfahd/releases/latest" : finalApk))))
                        .show());
            } catch (Exception ignored) {
                if (manual) new Handler(Looper.getMainLooper()).post(() -> new AlertDialog.Builder(activity)
                        .setMessage("تعذر فحص التحديثات الآن.")
                        .setPositiveButton("حسنًا", null).show());
            }
        }).start();
    }

    private static String[] tryFetch(String endpoint, boolean github) {
        try { return fetch(endpoint, github); } catch (Exception ignored) { return null; }
    }

    private static String[] fetch(String endpoint, boolean github) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(endpoint).openConnection();
        connection.setConnectTimeout(9000);
        connection.setReadTimeout(9000);
        connection.setUseCaches(false);
        connection.setRequestProperty("Cache-Control", "no-cache");
        connection.setRequestProperty("Accept", "application/json");
        if (github) connection.setRequestProperty("User-Agent", "Al-Fahd-TV");
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IllegalStateException("Update endpoint returned " + code);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                    connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) body.append(line);
                String json = body.toString();
                return new String[]{github ? find(json, "tag_name") : find(json, "version"),
                        github ? findApk(json) : find(json, "apkUrl")};
            }
        } finally { connection.disconnect(); }
    }

    private static String find(String json, String key) {
        Matcher matcher = Pattern.compile("\\\"" + key + "\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").matcher(json);
        return matcher.find() ? matcher.group(1) : "";
    }

    private static String findApk(String json) {
        Matcher matcher = Pattern.compile("\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+\\.apk[^\\\"]*)\\\"").matcher(json);
        return matcher.find() ? matcher.group(1).replace("\\/", "/") : "";
    }

    private static boolean newer(String candidate, String current) {
        String[] candidateParts = candidate.replaceAll("[^0-9.]", "").split("\\.");
        String[] currentParts = current.replaceAll("[^0-9.]", "").split("\\.");
        for (int i = 0; i < Math.max(candidateParts.length, currentParts.length); i++) {
            int candidateValue = i < candidateParts.length ? Integer.parseInt(candidateParts[i]) : 0;
            int currentValue = i < currentParts.length ? Integer.parseInt(currentParts[i]) : 0;
            if (candidateValue != currentValue) return candidateValue > currentValue;
        }
        return false;
    }
}
