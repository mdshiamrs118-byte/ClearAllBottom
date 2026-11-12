package com.example.clearallbottom;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    /** The Xposed hook replaces this to return true when the module is active. */
    static boolean isModuleActive() {
        return false;
    }

    private SharedPreferences sp;
    private boolean shared = true;

    @SuppressWarnings({"deprecation", "WorldReadableFiles"})
    private SharedPreferences openPrefs() {
        try {
            return getSharedPreferences(Prefs.NAME, MODE_WORLD_READABLE);
        } catch (SecurityException e) {
            shared = false;
            return getSharedPreferences(Prefs.NAME, MODE_PRIVATE);
        }
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sp = openPrefs();

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(18), dp(36), dp(18), dp(40));
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("Recents Styler");
        title.setTextSize(26);
        title.setTextColor(Color.WHITE);
        root.addView(title);

        TextView status = new TextView(this);
        status.setTextSize(13);
        status.setPadding(0, dp(4), 0, dp(10));
        if (isModuleActive()) {
            status.setText("\u25CF Module active");
            status.setTextColor(0xFF66BB6A);
        } else {
            status.setText("\u25CF Module not active. Enable it in LSPosed (scope: Trebuchet "
                    + "+ this app), then restart the launcher.");
            status.setTextColor(0xFFFFB74D);
        }
        root.addView(status);

        if (!shared) {
            TextView warn = new TextView(this);
            warn.setText("Settings can't be shared with the launcher until the module is "
                    + "enabled in LSPosed. Enable it, reopen this app, and set things again.");
            warn.setTextColor(0xFFEF5350);
            warn.setTextSize(13);
            warn.setPadding(0, 0, 0, dp(10));
            root.addView(warn);
        }

        // ----- Style -----
        heading(root, "Recents style");
        RadioGroup group = new RadioGroup(this);
        String[] names = {"Original (stock)", "Stack", "Cover flow", "iPhone style"};
        int current = sp.getInt(Prefs.K_STYLE, Prefs.D_STYLE);
        for (int i = 0; i < names.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(names[i]);
            rb.setId(1000 + i);
            rb.setTextColor(Color.WHITE);
            group.addView(rb);
        }
        group.check(1000 + current);
        group.setOnCheckedChangeListener(new RadioGroup.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(RadioGroup g, int id) {
                sp.edit().putInt(Prefs.K_STYLE, id - 1000).apply();
            }
        });
        root.addView(group);

        // ----- Card size & shape -----
        heading(root, "Card size and shape");
        slider(root, Prefs.K_WIDTH, "Card width", 50, 150, Prefs.D_WIDTH, "%", null);
        slider(root, Prefs.K_HEIGHT, "Card height", 50, 150, Prefs.D_HEIGHT, "%", null);
        slider(root, Prefs.K_RADIUS, "Corner roundness", 0, 64, Prefs.D_RADIUS, " dp", "Stock");
        slider(root, Prefs.K_YOFF, "Move cards up / down", -150, 150, Prefs.D_YOFF, " dp", null);

        // ----- Stack layout -----
        heading(root, "Stack layout (not used by Original)");
        slider(root, Prefs.K_SPACING, "Spacing between cards", 0, 100, Prefs.D_SPACING, "%", null);
        slider(root, Prefs.K_SHRINK, "Back cards shrink", 0, 25, Prefs.D_SHRINK, "% per step", null);
        slider(root, Prefs.K_FADE, "Back cards fade", 0, 50, Prefs.D_FADE, "% per step", null);
        slider(root, Prefs.K_DEPTH, "Cards visible each side", 1, 6, Prefs.D_DEPTH, "", null);
        slider(root, Prefs.K_LIFT, "Lift of back cards (Stack)", 0, 40, Prefs.D_LIFT, " dp", null);
        slider(root, Prefs.K_TILT, "Tilt angle (Cover flow)", 0, 80, Prefs.D_TILT, "\u00B0", null);
        toggle(root, Prefs.K_INVERT, "Invert tilt direction", Prefs.D_INVERT);

        // ----- Clear all -----
        heading(root, "Clear all button");
        toggle(root, Prefs.K_PIN, "Pin to bottom-center, always visible", Prefs.D_PIN);
        slider(root, Prefs.K_CLEAR_BOTTOM, "Distance from bottom", 0, 250,
                Prefs.D_CLEAR_BOTTOM, " dp", null);

        // ----- Actions -----
        Button restart = new Button(this);
        restart.setText("Restart launcher (root)");
        restart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Runtime.getRuntime().exec(new String[]{"su", "-c",
                                    "am force-stop com.android.launcher3"});
                        } catch (Exception ignored) {
                        }
                    }
                }).start();
                Toast.makeText(MainActivity.this, "Restarting launcher...", Toast.LENGTH_SHORT).show();
            }
        });
        root.addView(restart);

        Button reset = new Button(this);
        reset.setText("Reset to defaults");
        reset.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sp.edit().clear().apply();
                recreate();
            }
        });
        root.addView(reset);

        TextView note = new TextView(this);
        note.setText("Changes apply live: just open Recents again. Corner roundness 'Stock' "
                + "keeps the launcher's own corners; values above stock round the cards more.");
        note.setTextColor(0xFF9E9E9E);
        note.setTextSize(12);
        note.setPadding(0, dp(12), 0, 0);
        root.addView(note);
    }

    private void heading(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(15);
        tv.setTextColor(0xFF90CAF9);
        tv.setPadding(0, dp(20), 0, dp(6));
        parent.addView(tv);
    }

    private void slider(LinearLayout parent, final String key, final String label,
                        final int min, int max, int def, final String unit, final String zeroLabel) {
        final TextView tv = new TextView(this);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14);
        tv.setGravity(Gravity.START);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        int value = sp.getInt(key, def);
        sb.setProgress(Math.max(0, Math.min(max - min, value - min)));
        tv.setText(describe(label, value, unit, zeroLabel));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int v = progress + min;
                tv.setText(describe(label, v, unit, zeroLabel));
                if (fromUser) sp.edit().putInt(key, v).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
                sp.edit().putInt(key, bar.getProgress() + min).apply();
            }
        });
        parent.addView(tv);
        parent.addView(sb);
    }

    private String describe(String label, int v, String unit, String zeroLabel) {
        if (v == 0 && zeroLabel != null) return label + ": " + zeroLabel;
        return label + ": " + v + unit;
    }

    private void toggle(LinearLayout parent, final String key, String label, boolean def) {
        Switch sw = new Switch(this);
        sw.setText(label);
        sw.setTextColor(Color.WHITE);
        sw.setChecked(sp.getBoolean(key, def));
        sw.setPadding(0, dp(8), 0, dp(8));
        sw.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton b, boolean checked) {
                sp.edit().putBoolean(key, checked).apply();
            }
        });
        parent.addView(sw);
    }
}
