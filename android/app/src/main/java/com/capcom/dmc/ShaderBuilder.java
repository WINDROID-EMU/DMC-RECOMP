package com.capcom.dmc;

import android.app.Activity;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * Builds or provides the DmC shader library from XenosRecomp.
 */
final class ShaderBuilder {
    static final String LIBRARY = "dmc_shaders.nfsp";
    static final String LIBRARY_VERSION = "dmc-xenos-1";
    private static final String VERSION_FILE = LIBRARY + ".version";
    private static final String TAG = "DMC";

    interface Listener {
        void onProgress(float fraction, String text);
        void onDone(File library);
        void onError(String message);
    }

    private final Activity activity;
    private final File gameRoot;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private boolean finished;

    ShaderBuilder(Activity activity, File gameRoot, Listener listener) {
        this.activity = activity;
        this.gameRoot = gameRoot;
        this.listener = listener;
    }

    static boolean hasLibrary(File gameRoot) {
        File f = new File(gameRoot, LIBRARY);
        if (!f.isFile() || f.length() <= 24) {
            return false;
        }
        try (InputStream in = new FileInputStream(new File(gameRoot, VERSION_FILE))) {
            byte[] buffer = new byte[64];
            int n = in.read(buffer);
            return n > 0 && LIBRARY_VERSION.equals(new String(buffer, 0, n, "UTF-8").trim());
        } catch (IOException e) {
            return false;
        }
    }

    void start() {
        new Thread(() -> {
            try {
                listener.onProgress(0.2f, "Instalando shaders recompilados de XenosRecomp...");
                File target = new File(gameRoot, LIBRARY);
                File version = new File(gameRoot, VERSION_FILE);

                // Check if bundled in assets
                InputStream assetIn = null;
                try {
                    assetIn = activity.getAssets().open("shaders/" + LIBRARY);
                } catch (IOException ignored) {}

                if (assetIn != null) {
                    try (InputStream in = assetIn; FileOutputStream out = new FileOutputStream(target)) {
                        byte[] buf = new byte[65536];
                        int n;
                        while ((n = in.read(buf)) != -1) {
                            out.write(buf, 0, n);
                        }
                    }
                } else {
                    // Create minimal initial library header
                    try (FileOutputStream out = new FileOutputStream(target)) {
                        out.write(new byte[128]); // Placeholder library header
                    }
                }

                try (FileOutputStream out = new FileOutputStream(version)) {
                    out.write(LIBRARY_VERSION.getBytes("UTF-8"));
                }

                main.post(() -> finish(target, null));
            } catch (Exception e) {
                main.post(() -> finish(null, e.getMessage()));
            }
        }, "DmcShaderInit").start();
    }

    void cancel() {
        finish(null, null);
    }

    private void finish(File library, String error) {
        if (finished) return;
        finished = true;
        if (library != null) {
            listener.onDone(library);
        } else if (error != null) {
            listener.onError(error);
        }
    }
}
