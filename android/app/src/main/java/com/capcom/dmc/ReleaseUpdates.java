package com.capcom.dmc;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import javax.net.ssl.HttpsURLConnection;

/** Optional official updates check. */
final class ReleaseUpdates {
    private static final String REPO = "https://github.com/capcom/dmc-android/";
    private static final String API = "https://api.github.com/repos/capcom/dmc-android/releases/latest";
    static final class Result {
        final String tag, url, error;
        Result(String tag, String url, String error) { this.tag = tag; this.url = url; this.error = error; }
    }
    static SharedPreferences prefs(Context context) { return context.getSharedPreferences("dmc_updates", 0); }
    static String installed(Context context) {
        try { return context.getPackageManager().getPackageInfo(context.getPackageName(), 0).versionName; }
        catch (Exception ignored) { return "1.0.0"; }
    }
    static void check(Context context, boolean force, Consumer<Result> callback) {
        Context app = context.getApplicationContext();
        Handler main = new Handler(Looper.getMainLooper());
        Thread worker = new Thread(() -> {
            Result result;
            try { result = fetch(app, force); }
            catch (Exception error) { result = new Result(null, null, "No se pudo consultar actualizaciones."); }
            Result value = result;
            main.post(() -> callback.accept(value));
        }, "ReleaseCheck");
        worker.setDaemon(true); worker.start();
    }
    private static Result fetch(Context context, boolean force) throws Exception {
        SharedPreferences prefs = prefs(context);
        long age = System.currentTimeMillis() - prefs.getLong("last_check", 0);
        if (!force && age >= 0 && age < TimeUnit.HOURS.toMillis(6)) {
            String tag = prefs.getString("latest_tag", "");
            String url = prefs.getString("latest_url", REPO + "releases/latest");
            return new Result(tag, url.startsWith(REPO + "releases/") ? url : REPO + "releases/latest", null);
        }
        return new Result("", REPO + "releases/latest", null);
    }
}
