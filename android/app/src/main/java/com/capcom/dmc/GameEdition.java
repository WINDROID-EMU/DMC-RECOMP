package com.capcom.dmc;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/**
 * Validates the DmC game executable (default.xex or game.iso).
 */
final class GameEdition {
    private static final String PREFS = "dmc_edition";

    final String sha256;
    final String name;

    private GameEdition(String sha256, String name) {
        this.sha256 = sha256;
        this.name = name;
    }

    boolean supported() {
        return true; // Any valid Xbox 360 DmC executable or ISO
    }

    String describe() {
        return name != null ? name : "DmC Devil May Cry (Xbox 360)";
    }

    static GameEdition of(Context context, File gameRoot) {
        File xex = new File(gameRoot, "default.xex");
        if (!xex.isFile()) {
            File iso = new File(gameRoot, "game.iso");
            if (iso.isFile()) {
                return new GameEdition("iso", "DmC Devil May Cry (game.iso)");
            }
            File dmcModule = new File(gameRoot, "DmcModule.xex");
            if (dmcModule.isFile()) {
                return new GameEdition("dmc_module", "DmC Devil May Cry (DmcModule.xex)");
            }
            return null;
        }
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String key = xex.length() + ":" + xex.lastModified();
        String sha = key.equals(prefs.getString("key", "")) ? prefs.getString("sha256", null) : null;
        if (sha == null) {
            try (InputStream in = new FileInputStream(xex)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[1 << 16];
                for (int n; (n = in.read(buffer)) > 0; ) digest.update(buffer, 0, n);
                StringBuilder hex = new StringBuilder();
                for (byte b : digest.digest()) hex.append(String.format("%02x", b));
                sha = hex.toString();
            } catch (Exception e) {
                return new GameEdition("", "DmC Devil May Cry");
            }
            prefs.edit().putString("key", key).putString("sha256", sha).apply();
        }
        return new GameEdition(sha, "DmC Devil May Cry");
    }
}
