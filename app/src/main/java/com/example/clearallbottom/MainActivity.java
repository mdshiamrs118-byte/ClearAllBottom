package com.example.clearallbottom;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.ArrayAdapter;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class MainActivity extends Activity {

    /** The Xposed hook replaces this to return true when the module is active. */
    static boolean isModuleActive() {
        return false;
    }

    private SharedPreferences sp;
    private boolean shared = true;
    private TextView previewBtn;
    private SharedPreferences meta;
    private Spinner presetSpinner;
    private final List<String> presetNames = new ArrayList<>();
    private static final int REQ_EXPORT = 41;
    private static final int REQ_IMPORT = 42;
    private static final String[] BUILTIN = {"Stock", "Stack", "Cover flow", "iPhone style"};
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;

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
        meta = getSharedPreferences("meta", MODE_PRIVATE);
        if (sp.getString(Prefs.K_TOKEN, null) == null) {
            sp.edit().putString(Prefs.K_TOKEN, UUID.randomUUID().toString()).apply();
        }

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

        buildPresetSection(root);

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
        toggle(root, Prefs.K_TRANS_ON, "Smooth transition from stock size to custom size "
                + "(off = custom size right away)", Prefs.D_TRANS_ON);
        slider(root, Prefs.K_TRANS_MS, "Transition duration", 100, 800, Prefs.D_TRANS_MS, " ms", null);

        // ----- Stack layout -----
        heading(root, "Stack layout (not used by Original)");
        slider(root, Prefs.K_SPACING, "Spacing between cards", 0, 100, Prefs.D_SPACING, "%", null);
        slider(root, Prefs.K_SHRINK, "Back cards shrink", 0, 25, Prefs.D_SHRINK, "% per step", null);
        slider(root, Prefs.K_FADE, "Back cards fade", 0, 50, Prefs.D_FADE, "% per step", null);
        slider(root, Prefs.K_DEPTH, "Cards visible each side", 1, 6, Prefs.D_DEPTH, "", null);
        slider(root, Prefs.K_LIFT, "Lift of back cards (Stack)", 0, 40, Prefs.D_LIFT, " dp", null);
        slider(root, Prefs.K_TILT, "Tilt angle (Cover flow)", 0, 80, Prefs.D_TILT, "\u00B0", null);
        toggle(root, Prefs.K_INVERT, "Invert tilt direction", Prefs.D_INVERT);
        toggle(root, Prefs.K_FLAT, "Flat tilt, no 3D (use if side cards look black)", Prefs.D_FLAT);

        // ----- Touch -----
        heading(root, "Touch");
        toggle(root, Prefs.K_TAP_HOME, "Tap empty space to go home", Prefs.D_TAP_HOME);
        toggle(root, Prefs.K_ICON_POPUP, "Tap an app icon for the icon popup "
                + "(freeform, split screen, app info, lock)", Prefs.D_ICON_POPUP);

        // ----- Clear all -----
        heading(root, "Clear all button");
        toggle(root, Prefs.K_PIN, "Pin to bottom-center, always visible", Prefs.D_PIN);
        slider(root, Prefs.K_CLEAR_BOTTOM, "Distance from bottom", 0, 250,
                Prefs.D_CLEAR_BOTTOM, " dp", null);

        // ----- Clear all look -----
        heading(root, "Clear all button look");
        FrameLayout stage = new FrameLayout(this);
        GradientDrawable stageBg = new GradientDrawable();
        stageBg.setColor(0xFF3A3A3A);
        stageBg.setCornerRadius(dp(12));
        stage.setBackground(stageBg);
        stage.setPadding(dp(12), dp(18), dp(12), dp(18));
        previewBtn = new TextView(this);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        stage.addView(previewBtn, plp);
        root.addView(stage);

        toggle(root, Prefs.K_BTN_ON, "Use custom look", Prefs.D_BTN_ON);
        textRow(root, Prefs.K_BTN_TEXT, "Label (empty = default)");
        colorRow(root, Prefs.K_BTN_TEXT_COLOR, "Text color", Prefs.D_BTN_TEXT_COLOR, false);
        slider(root, Prefs.K_BTN_TEXT_SIZE, "Text size", 0, 30, Prefs.D_BTN_TEXT_SIZE, " sp", "Stock");
        toggle(root, Prefs.K_BTN_BOLD, "Bold text", Prefs.D_BTN_BOLD);
        colorRow(root, Prefs.K_BTN_BG, "Background color and opacity", Prefs.D_BTN_BG, true);
        slider(root, Prefs.K_BTN_RADIUS, "Corner roundness", 0, 60, Prefs.D_BTN_RADIUS, " dp", null);
        slider(root, Prefs.K_BTN_W, "Width", 0, 300, Prefs.D_BTN_W, " dp", "Auto");
        slider(root, Prefs.K_BTN_H, "Height", 0, 120, Prefs.D_BTN_H, " dp", "Auto");
        slider(root, Prefs.K_BTN_PADX, "Side padding", 0, 60, Prefs.D_BTN_PADX, " dp", "Stock");
        slider(root, Prefs.K_BTN_STROKE, "Border width", 0, 6, Prefs.D_BTN_STROKE, " dp", "None");
        colorRow(root, Prefs.K_BTN_STROKE_COLOR, "Border color", Prefs.D_BTN_STROKE_COLOR, true);
        slider(root, Prefs.K_BTN_SHADOW, "Shadow", 0, 16, Prefs.D_BTN_SHADOW, " dp", "None");

        refreshPreview();
        prefListener = new SharedPreferences.OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(SharedPreferences p, String key) {
                refreshPreview();
            }
        };
        sp.registerOnSharedPreferenceChangeListener(prefListener);

        buildRootSection(root);

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
                SharedPreferences.Editor ed = sp.edit();
                for (String k : new ArrayList<>(sp.getAll().keySet())) {
                    if (!Prefs.isGlobalKey(k)) ed.remove(k);
                }
                ed.apply();
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

    @Override
    protected void onDestroy() {
        if (sp != null && prefListener != null) {
            sp.unregisterOnSharedPreferenceChangeListener(prefListener);
        }
        super.onDestroy();
    }

    /** Small live preview of the Clear all button using the current settings. */
    private void refreshPreview() {
        if (previewBtn == null) return;
        TextView tv = previewBtn;
        String label = sp.getString(Prefs.K_BTN_TEXT, Prefs.D_BTN_TEXT);
        if (label == null || label.length() == 0) label = "Clear all";
        tv.setText(label);
        if (!sp.getBoolean(Prefs.K_BTN_ON, Prefs.D_BTN_ON)) {
            tv.setBackground(null);
            tv.setTextColor(Color.WHITE);
            tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            tv.setTypeface(null, Typeface.BOLD);
            tv.setPadding(dp(8), dp(8), dp(8), dp(8));
            tv.setWidth(0);
            tv.setMinWidth(0);
            tv.setHeight(0);
            tv.setMinHeight(0);
            tv.setElevation(0f);
            return;
        }
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(sp.getInt(Prefs.K_BTN_BG, Prefs.D_BTN_BG));
        gd.setCornerRadius(dp(sp.getInt(Prefs.K_BTN_RADIUS, Prefs.D_BTN_RADIUS)));
        int stroke = sp.getInt(Prefs.K_BTN_STROKE, Prefs.D_BTN_STROKE);
        if (stroke > 0) {
            gd.setStroke(dp(stroke), sp.getInt(Prefs.K_BTN_STROKE_COLOR, Prefs.D_BTN_STROKE_COLOR));
        }
        tv.setBackground(gd);
        tv.setTextColor(sp.getInt(Prefs.K_BTN_TEXT_COLOR, Prefs.D_BTN_TEXT_COLOR));
        int size = sp.getInt(Prefs.K_BTN_TEXT_SIZE, Prefs.D_BTN_TEXT_SIZE);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, size > 0 ? size : 14);
        tv.setTypeface(null, sp.getBoolean(Prefs.K_BTN_BOLD, Prefs.D_BTN_BOLD)
                ? Typeface.BOLD : Typeface.NORMAL);
        int padx = sp.getInt(Prefs.K_BTN_PADX, Prefs.D_BTN_PADX);
        tv.setPadding(dp(padx > 0 ? padx : 16), dp(8), dp(padx > 0 ? padx : 16), dp(8));
        int w = sp.getInt(Prefs.K_BTN_W, Prefs.D_BTN_W);
        int hgt = sp.getInt(Prefs.K_BTN_H, Prefs.D_BTN_H);
        if (w > 0) tv.setWidth(dp(w)); else { tv.setWidth(0); tv.setMinWidth(0); }
        if (hgt > 0) tv.setHeight(dp(hgt)); else { tv.setHeight(0); tv.setMinHeight(0); }
        tv.setGravity(Gravity.CENTER);
        tv.setElevation(dp(sp.getInt(Prefs.K_BTN_SHADOW, Prefs.D_BTN_SHADOW)));
    }

    private void textRow(LinearLayout parent, final String key, String label) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14);
        tv.setPadding(0, dp(8), 0, 0);
        parent.addView(tv);

        EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setHint("Clear all");
        et.setHintTextColor(0xFF777777);
        et.setTextColor(Color.WHITE);
        et.setText(sp.getString(key, ""));
        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable e) {
                sp.edit().putString(key, e.toString()).apply();
            }
        });
        parent.addView(et);
    }

    private void paintSwatch(View swatch, int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(6));
        g.setStroke(dp(1), 0xFF9E9E9E);
        swatch.setBackground(g);
    }

    private void colorRow(LinearLayout parent, final String key, final String label,
                          final int def, final boolean alpha) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(10), 0, dp(10));

        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(Color.WHITE);
        tv.setTextSize(14);
        row.addView(tv, new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        final View swatch = new View(this);
        paintSwatch(swatch, sp.getInt(key, def));
        row.addView(swatch, new LinearLayout.LayoutParams(dp(48), dp(30)));

        row.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showColorDialog(key, label, def, alpha, swatch);
            }
        });
        parent.addView(row);
    }

    private void showColorDialog(final String key, String label, int def, boolean alpha,
                                 final View swatch) {
        int cur = sp.getInt(key, def);
        final int[] c = {Color.alpha(cur), Color.red(cur), Color.green(cur), Color.blue(cur)};
        if (!alpha) c[0] = 255;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(12), dp(20), dp(4));

        final View preview = new View(this);
        box.addView(preview, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(48)));
        final TextView hex = new TextView(this);
        hex.setTextColor(0xFFBDBDBD);
        hex.setTextSize(12);
        hex.setPadding(0, dp(4), 0, dp(6));
        box.addView(hex);

        final Runnable update = new Runnable() {
            @Override
            public void run() {
                int col = Color.argb(c[0], c[1], c[2], c[3]);
                paintSwatch(preview, col);
                hex.setText(String.format("#%08X", col));
            }
        };

        final SeekBar[] bars = new SeekBar[4];
        String[] names = {"Opacity", "Red", "Green", "Blue"};
        for (int i = 0; i < 4; i++) {
            if (i == 0 && !alpha) continue;
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(names[i]);
            t.setTextColor(Color.WHITE);
            t.setTextSize(13);
            box.addView(t);
            SeekBar sb = new SeekBar(this);
            sb.setMax(255);
            sb.setProgress(c[i]);
            sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar b, int progress, boolean fromUser) {
                    c[idx] = progress;
                    update.run();
                }

                @Override
                public void onStartTrackingTouch(SeekBar b) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar b) {
                }
            });
            bars[i] = sb;
            box.addView(sb);
        }

        // quick presets
        int[] presets = {0xFFFFFFFF, 0xFF000000, 0xFF9E9E9E, 0xFFF44336, 0xFFFF9800, 0xFFFFEB3B,
                0xFF4CAF50, 0xFF009688, 0xFF2196F3, 0xFF9C27B0, 0xFFE91E63};
        LinearLayout strip = new LinearLayout(this);
        strip.setPadding(0, dp(10), 0, dp(6));
        for (final int pcol : presets) {
            View sw = new View(this);
            paintSwatch(sw, pcol);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(34));
            lp.rightMargin = dp(8);
            sw.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    c[1] = Color.red(pcol);
                    c[2] = Color.green(pcol);
                    c[3] = Color.blue(pcol);
                    for (int k = 1; k < 4; k++) if (bars[k] != null) bars[k].setProgress(c[k]);
                    update.run();
                }
            });
            strip.addView(sw, lp);
        }
        HorizontalScrollView hs = new HorizontalScrollView(this);
        hs.addView(strip);
        box.addView(hs);

        update.run();
        new AlertDialog.Builder(this)
                .setTitle(label)
                .setView(box)
                .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        int col = Color.argb(c[0], c[1], c[2], c[3]);
                        sp.edit().putInt(key, col).apply();
                        paintSwatch(swatch, col);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ================================================================ presets
    private void buildPresetSection(LinearLayout root) {
        heading(root, "Presets");
        presetSpinner = new Spinner(this);
        refreshPresetList(null);
        root.addView(presetSpinner);

        LinearLayout row1 = new LinearLayout(this);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.addView(smallButton("Load", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                loadSelectedPreset();
            }
        }));
        row1.addView(smallButton("Save as...", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveCurrentAsPreset();
            }
        }));
        row1.addView(smallButton("Delete", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                deleteSelectedPreset();
            }
        }));
        root.addView(row1);

        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);
        row2.addView(smallButton("Export", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("application/json");
                i.putExtra(Intent.EXTRA_TITLE, "recents-styler-presets.json");
                startActivityForResult(i, REQ_EXPORT);
            }
        }));
        row2.addView(smallButton("Import", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                i.addCategory(Intent.CATEGORY_OPENABLE);
                i.setType("*/*");
                startActivityForResult(i, REQ_IMPORT);
            }
        }));
        root.addView(row2);
    }

    private Button smallButton(String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        return b;
    }

    private JSONObject loadPresets() {
        try {
            return new JSONObject(meta.getString("presets", "{}"));
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private void storePresets(JSONObject o) {
        meta.edit().putString("presets", o.toString()).apply();
    }

    private boolean isBuiltin(String name) {
        for (String b : BUILTIN) if (b.equals(name)) return true;
        return false;
    }

    private JSONObject builtinPreset(String name) throws JSONException {
        JSONObject o = new JSONObject();
        if ("Stock".equals(name)) {
            o.put(Prefs.K_STYLE, Prefs.STYLE_ORIGINAL);
        } else if ("Stack".equals(name)) {
            o.put(Prefs.K_STYLE, Prefs.STYLE_STACK);
        } else if ("Cover flow".equals(name)) {
            o.put(Prefs.K_STYLE, Prefs.STYLE_COVERFLOW);
            o.put(Prefs.K_SPACING, 40);
            o.put(Prefs.K_SHRINK, 10);
            o.put(Prefs.K_TILT, 45);
        } else if ("iPhone style".equals(name)) {
            o.put(Prefs.K_STYLE, Prefs.STYLE_IPHONE);
            o.put(Prefs.K_SPACING, 40);
            o.put(Prefs.K_SHRINK, 5);
        }
        return o;
    }

    private void refreshPresetList(String select) {
        presetNames.clear();
        for (String b : BUILTIN) presetNames.add(b);
        List<String> saved = new ArrayList<>();
        Iterator<String> it = loadPresets().keys();
        while (it.hasNext()) saved.add(it.next());
        Collections.sort(saved, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return a.compareToIgnoreCase(b);
            }
        });
        presetNames.addAll(saved);
        ArrayAdapter<String> ad = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, presetNames);
        ad.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        presetSpinner.setAdapter(ad);
        if (select != null) {
            int idx = presetNames.indexOf(select);
            if (idx >= 0) presetSpinner.setSelection(idx);
        }
    }

    private String selectedPreset() {
        int pos = presetSpinner.getSelectedItemPosition();
        return pos >= 0 && pos < presetNames.size() ? presetNames.get(pos) : null;
    }

    private JSONObject snapshot() {
        JSONObject o = new JSONObject();
        for (Map.Entry<String, ?> e : sp.getAll().entrySet()) {
            if (Prefs.isGlobalKey(e.getKey())) continue;
            Object v = e.getValue();
            if (v instanceof Integer || v instanceof Boolean || v instanceof String) {
                try {
                    o.put(e.getKey(), v);
                } catch (JSONException ignored) {
                }
            }
        }
        return o;
    }

    private void applyPreset(JSONObject o) {
        SharedPreferences.Editor ed = sp.edit();
        for (String k : new ArrayList<>(sp.getAll().keySet())) {
            if (!Prefs.isGlobalKey(k)) ed.remove(k);
        }
        Iterator<String> it = o.keys();
        while (it.hasNext()) {
            String k = it.next();
            if (Prefs.isGlobalKey(k)) continue;
            Object v = o.opt(k);
            if (v instanceof Integer) ed.putInt(k, (Integer) v);
            else if (v instanceof Long) ed.putInt(k, (int) (long) (Long) v);
            else if (v instanceof Boolean) ed.putBoolean(k, (Boolean) v);
            else if (v instanceof String) ed.putString(k, (String) v);
        }
        ed.apply();
        recreate();
    }

    private void loadSelectedPreset() {
        String name = selectedPreset();
        if (name == null) return;
        try {
            JSONObject o = isBuiltin(name) ? builtinPreset(name) : loadPresets().getJSONObject(name);
            applyPreset(o);
            Toast.makeText(this, "Loaded \"" + name + "\"", Toast.LENGTH_SHORT).show();
        } catch (JSONException e) {
            Toast.makeText(this, "Couldn't load that preset", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveCurrentAsPreset() {
        final EditText et = new EditText(this);
        et.setSingleLine(true);
        et.setHint("Preset name");
        new AlertDialog.Builder(this)
                .setTitle("Save current settings as")
                .setView(et)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        String name = et.getText().toString().trim();
                        if (name.length() == 0) return;
                        if (isBuiltin(name)) {
                            Toast.makeText(MainActivity.this, "That name is built in, pick another",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }
                        try {
                            JSONObject all = loadPresets();
                            all.put(name, snapshot());
                            storePresets(all);
                            refreshPresetList(name);
                            Toast.makeText(MainActivity.this, "Saved \"" + name + "\"",
                                    Toast.LENGTH_SHORT).show();
                        } catch (JSONException e) {
                            Toast.makeText(MainActivity.this, "Couldn't save", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void deleteSelectedPreset() {
        String name = selectedPreset();
        if (name == null) return;
        if (isBuiltin(name)) {
            Toast.makeText(this, "Built-in presets can't be deleted", Toast.LENGTH_SHORT).show();
            return;
        }
        JSONObject all = loadPresets();
        all.remove(name);
        storePresets(all);
        refreshPresetList(null);
        Toast.makeText(this, "Deleted \"" + name + "\"", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onActivityResult(int req, int result, Intent data) {
        super.onActivityResult(req, result, data);
        if (result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        try {
            if (req == REQ_EXPORT) {
                JSONObject out = new JSONObject();
                out.put("presets", loadPresets());
                OutputStream os = getContentResolver().openOutputStream(uri);
                if (os != null) {
                    os.write(out.toString(2).getBytes("UTF-8"));
                    os.close();
                }
                Toast.makeText(this, "Presets exported", Toast.LENGTH_SHORT).show();
            } else if (req == REQ_IMPORT) {
                InputStream is = getContentResolver().openInputStream(uri);
                ByteArrayOutputStream bos = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while (is != null && (n = is.read(buf)) > 0) bos.write(buf, 0, n);
                if (is != null) is.close();
                JSONObject in = new JSONObject(bos.toString("UTF-8"));
                JSONObject incoming = in.has("presets") ? in.getJSONObject("presets") : null;
                JSONObject all = loadPresets();
                int count = 0;
                if (incoming != null) {
                    Iterator<String> it = incoming.keys();
                    while (it.hasNext()) {
                        String k = it.next();
                        if (isBuiltin(k)) continue;
                        all.put(k, incoming.getJSONObject(k));
                        count++;
                    }
                }
                storePresets(all);
                refreshPresetList(null);
                Toast.makeText(this, "Imported " + count + " preset(s)", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            Toast.makeText(this, "That didn't work: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ================================================================ root + protected apps
    private void buildRootSection(LinearLayout root) {
        heading(root, "Clear all actions (needs root)");
        toggle(root, Prefs.K_ROOT_ON, "Root clean when clearing", Prefs.D_ROOT_ON);
        toggle(root, Prefs.K_ROOT_FORCE, "Force-stop the apps that were cleared", Prefs.D_ROOT_FORCE);
        toggle(root, Prefs.K_ROOT_KILLBG, "Kill all other background processes", Prefs.D_ROOT_KILLBG);
        toggle(root, Prefs.K_ROOT_DROP, "Free cached memory (drop caches)", Prefs.D_ROOT_DROP);

        final Button prot = new Button(this);
        prot.setAllCaps(false);
        setProtectedLabel(prot);
        prot.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showProtectedDialog(prot);
            }
        });
        root.addView(prot);

        Button ff = new Button(this);
        ff.setAllCaps(false);
        ff.setText("Enable freeform windows (root, reboot after)");
        ff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                runRoot("settings put global enable_freeform_support 1; "
                        + "settings put global force_resizable_activities 1; "
                        + "settings put global freeform_window_management 1",
                        "Freeform enabled. Reboot for it to take effect.");
            }
        });
        root.addView(ff);

        Button test = new Button(this);
        test.setAllCaps(false);
        test.setText("Test root access");
        test.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                testRoot();
            }
        });
        root.addView(test);

        TextView note = new TextView(this);
        note.setText("Protected apps stay in Recents when you tap Clear all and are never "
                + "force-stopped. Swiping a protected card away still removes it.");
        note.setTextColor(0xFF9E9E9E);
        note.setTextSize(12);
        note.setPadding(0, dp(6), 0, 0);
        root.addView(note);
    }

    private Set<String> loadProtected() {
        Set<String> s = new HashSet<>();
        String raw = sp.getString(Prefs.K_PROTECTED, "");
        if (raw != null) {
            for (String p : raw.split(",")) if (p.trim().length() > 0) s.add(p.trim());
        }
        return s;
    }

    private void setProtectedLabel(Button b) {
        b.setText("Protected apps (" + loadProtected().size() + ")");
    }

    private void showProtectedDialog(final Button label) {
        PackageManager pm = getPackageManager();
        final Map<String, String> labels = new HashMap<>();
        final List<String> pkgs = new ArrayList<>();
        for (ApplicationInfo ai : pm.getInstalledApplications(0)) {
            if (ai.packageName.equals(getPackageName())) continue;
            if (pm.getLaunchIntentForPackage(ai.packageName) == null) continue;
            labels.put(ai.packageName, String.valueOf(pm.getApplicationLabel(ai)));
            pkgs.add(ai.packageName);
        }
        Collections.sort(pkgs, new Comparator<String>() {
            @Override
            public int compare(String a, String b) {
                return labels.get(a).compareToIgnoreCase(labels.get(b));
            }
        });

        final Set<String> current = loadProtected();
        final String[] names = new String[pkgs.size()];
        final boolean[] checked = new boolean[pkgs.size()];
        for (int i = 0; i < pkgs.size(); i++) {
            String pkg = pkgs.get(i);
            names[i] = labels.get(pkg) + "  (" + pkg + ")";
            checked[i] = current.contains(pkg);
        }
        new AlertDialog.Builder(this)
                .setTitle("Protected apps")
                .setMultiChoiceItems(names, checked,
                        new android.content.DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(android.content.DialogInterface d, int which,
                                                boolean isChecked) {
                                checked[which] = isChecked;
                            }
                        })
                .setPositiveButton("OK", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        Set<String> result = new HashSet<>(current);
                        result.removeAll(pkgs); // re-add the ones still checked below
                        for (int i = 0; i < pkgs.size(); i++) if (checked[i]) result.add(pkgs.get(i));
                        StringBuilder sb = new StringBuilder();
                        for (String p : result) {
                            if (sb.length() > 0) sb.append(',');
                            sb.append(p);
                        }
                        sp.edit().putString(Prefs.K_PROTECTED, sb.toString()).apply();
                        setProtectedLabel(label);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void runRoot(final String cmd, final String okMessage) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                String res;
                try {
                    Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", cmd});
                    res = p.waitFor() == 0 ? okMessage : "Root command failed";
                } catch (Exception e) {
                    res = "No root: " + e.getMessage();
                }
                final String msg = res;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }

    private void testRoot() {
        Toast.makeText(this, "Asking for root...", Toast.LENGTH_SHORT).show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String res;
                try {
                    Process p = Runtime.getRuntime().exec(new String[]{"su", "-c", "id"});
                    BufferedReader br = new BufferedReader(new InputStreamReader(p.getInputStream()));
                    String line = br.readLine();
                    int code = p.waitFor();
                    res = code == 0 ? "Root OK: " + line : "Root denied (exit " + code + ")";
                } catch (Exception e) {
                    res = "No root: " + e.getMessage();
                }
                final String msg = res;
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show();
                    }
                });
            }
        }).start();
    }
}
