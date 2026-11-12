package com.example.clearallbottom;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.util.HashSet;
import java.util.Set;

/**
 * Receives "Clear all" events from the launcher hook and runs the root clean-up
 * (force-stop, kill background, drop caches) with su. Needs the shared secret token.
 */
public class CleanReceiver extends BroadcastReceiver {

    private static final String[] NEVER = {
            "android", "com.android.systemui", "com.android.launcher3",
            "com.google.android.apps.nexuslauncher", Prefs.PKG
    };

    @Override
    public void onReceive(Context ctx, Intent in) {
        if (in == null || !Prefs.ACTION_CLEAN.equals(in.getAction())) return;
        SharedPreferences sp = ctx.getSharedPreferences(Prefs.NAME, Context.MODE_PRIVATE);
        String token = sp.getString(Prefs.K_TOKEN, null);
        if (token == null || !token.equals(in.getStringExtra("token"))) return;
        if (!sp.getBoolean(Prefs.K_ROOT_ON, Prefs.D_ROOT_ON)) return;

        Set<String> protectedSet = new HashSet<>();
        String raw = sp.getString(Prefs.K_PROTECTED, "");
        if (raw != null) {
            for (String s : raw.split(",")) if (s.trim().length() > 0) protectedSet.add(s.trim());
        }
        StringBuilder cmd = new StringBuilder();
        String[] pkgs = in.getStringArrayExtra("pkgs");
        if (sp.getBoolean(Prefs.K_ROOT_FORCE, Prefs.D_ROOT_FORCE) && pkgs != null) {
            for (String pkg : pkgs) {
                if (pkg == null || !pkg.matches("[A-Za-z0-9_.]+")) continue;
                if (protectedSet.contains(pkg) || isNever(pkg)) continue;
                cmd.append("am force-stop ").append(pkg).append("; ");
            }
        }
        if (sp.getBoolean(Prefs.K_ROOT_KILLBG, Prefs.D_ROOT_KILLBG)) cmd.append("am kill-all; ");
        if (sp.getBoolean(Prefs.K_ROOT_DROP, Prefs.D_ROOT_DROP)) {
            cmd.append("sync; echo 3 > /proc/sys/vm/drop_caches; ");
        }
        if (cmd.length() == 0) return;

        final String command = cmd.toString();
        final PendingResult pending = goAsync();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", command});
                    p.waitFor();
                } catch (Exception ignored) {
                } finally {
                    pending.finish();
                }
            }
        }).start();
    }

    private static boolean isNever(String pkg) {
        for (String n : NEVER) if (n.equals(pkg)) return true;
        return false;
    }
}
