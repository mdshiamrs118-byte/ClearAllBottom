package com.example.clearallbottom;

import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.Toast;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/** Icon-only, single-row popup shown when a card's app icon is tapped in Recents. */
final class RecentsPopup {
    private RecentsPopup() {}

    private static final String AMW = "com.android.systemui.shared.system.ActivityManagerWrapper";
    private static final String AOC = "com.android.systemui.shared.system.ActivityOptionsCompat";
    private static PopupWindow current;

    static void dismiss() {
        try {
            if (current != null && current.isShowing()) current.dismiss();
        } catch (Throwable ignored) {
        }
        current = null;
    }

    static void show(final ViewGroup rv, final View card, final View icon,
                     final String pkg, final int taskId) {
        dismiss();
        final Context ctx = rv.getContext();
        final float d = ctx.getResources().getDisplayMetrics().density;

        LinearLayout row = new LinearLayout(ctx);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xF2242424);
        bg.setCornerRadius(28 * d);
        row.setBackground(bg);
        int pad = Math.round(6 * d);
        row.setPadding(pad, pad, pad, pad);

        final PopupWindow pw = new PopupWindow(row, ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, true);
        pw.setBackgroundDrawable(new ColorDrawable(0));
        pw.setOutsideTouchable(true);
        pw.setElevation(10 * d);

        addButton(row, d, new IconDrawable(IconDrawable.FREEFORM, 0xFFFFFFFF, false),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pw.dismiss();
                        freeform(rv, taskId, pkg);
                    }
                });
        addButton(row, d, new IconDrawable(IconDrawable.SPLIT, 0xFFFFFFFF, false),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pw.dismiss();
                        split(rv, taskId, pkg);
                    }
                });
        addButton(row, d, new IconDrawable(IconDrawable.INFO, 0xFFFFFFFF, false),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pw.dismiss();
                        appInfo(ctx, pkg);
                    }
                });
        boolean locked = Hook.isLocked(pkg);
        addButton(row, d, new IconDrawable(IconDrawable.LOCK, locked ? 0xFFFFC107 : 0xFFFFFFFF, locked),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        pw.dismiss();
                        Boolean now = Hook.toggleLock(ctx, pkg);
                        if (now == null) {
                            Toast.makeText(ctx, "Open Recents Styler once, then try again",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(ctx, now ? "Locked: Clear all will skip this app"
                                    : "Unlocked", Toast.LENGTH_SHORT).show();
                        }
                    }
                });

        row.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int w = row.getMeasuredWidth();
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        int[] loc = new int[2];
        icon.getLocationOnScreen(loc);
        float sx = card.getScaleX() * icon.getScaleX();
        float sy = card.getScaleY() * icon.getScaleY();
        int x = Math.round(loc[0] + icon.getWidth() * sx / 2f - w / 2f);
        int y = Math.round(loc[1] + icon.getHeight() * sy + 8 * d);
        int margin = Math.round(8 * d);
        x = Math.max(margin, Math.min(dm.widthPixels - w - margin, x));
        y = Math.max(margin, y);

        current = pw;
        try {
            pw.showAtLocation(rv, Gravity.NO_GRAVITY, x, y);
        } catch (Throwable t) {
            XposedBridge.log("RecentsStyler: popup: " + t);
        }
    }

    private static void addButton(LinearLayout row, float d, Drawable icon, View.OnClickListener l) {
        ImageView iv = new ImageView(row.getContext());
        iv.setImageDrawable(icon);
        int pad = Math.round(11 * d);
        iv.setPadding(pad, pad, pad, pad);
        GradientDrawable mask = new GradientDrawable();
        mask.setShape(GradientDrawable.OVAL);
        mask.setColor(0xFFFFFFFF);
        iv.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, mask));
        iv.setOnClickListener(l);
        int size = Math.round(48 * d);
        row.addView(iv, new LinearLayout.LayoutParams(size, size));
    }

    // ------------------------------------------------------------- actions
    private static void start(ViewGroup rv, int taskId, String pkg, ActivityOptions opts) {
        Context ctx = rv.getContext();
        try {
            Class<?> k = XposedHelpers.findClass(AMW, rv.getClass().getClassLoader());
            Object amw = XposedHelpers.callStaticMethod(k, "getInstance");
            XposedHelpers.callMethod(amw, "startActivityFromRecents", taskId, opts);
            return;
        } catch (Throwable t) {
            XposedBridge.log("RecentsStyler: startActivityFromRecents: " + t);
        }
        try {
            Intent li = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
            if (li != null) {
                li.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                ctx.startActivity(li, opts.toBundle());
            }
        } catch (Throwable t) {
            XposedBridge.log("RecentsStyler: launch fallback: " + t);
        }
    }

    private static void freeform(ViewGroup rv, int taskId, String pkg) {
        try {
            ActivityOptions o = ActivityOptions.makeBasic();
            XposedHelpers.callMethod(o, "setLaunchWindowingMode", 5); // WINDOWING_MODE_FREEFORM
            DisplayMetrics dm = rv.getResources().getDisplayMetrics();
            o.setLaunchBounds(new Rect(Math.round(dm.widthPixels * 0.08f),
                    Math.round(dm.heightPixels * 0.16f), Math.round(dm.widthPixels * 0.92f),
                    Math.round(dm.heightPixels * 0.78f)));
            start(rv, taskId, pkg, o);
        } catch (Throwable t) {
            XposedBridge.log("RecentsStyler: freeform: " + t);
        }
    }

    private static void split(ViewGroup rv, int taskId, String pkg) {
        ActivityOptions opts = null;
        try {
            Class<?> k = XposedHelpers.findClass(AOC, rv.getClass().getClassLoader());
            opts = (ActivityOptions) XposedHelpers.callStaticMethod(k, "makeSplitScreenOptions", true);
        } catch (Throwable t) {
            XposedBridge.log("RecentsStyler: split options: " + t);
        }
        if (opts == null) {
            try {
                opts = ActivityOptions.makeBasic();
                XposedHelpers.callMethod(opts, "setLaunchWindowingMode", 3); // split primary
                try {
                    XposedHelpers.callMethod(opts, "setSplitScreenCreateMode", 0);
                } catch (Throwable ignored) {
                }
            } catch (Throwable t) {
                XposedBridge.log("RecentsStyler: split fallback: " + t);
                return;
            }
        }
        start(rv, taskId, pkg, opts);
    }

    private static void appInfo(Context ctx, String pkg) {
        try {
            Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:" + pkg));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Throwable t) {
            XposedBridge.log("RecentsStyler: appInfo: " + t);
        }
    }

    // ------------------------------------------------------------- icons
    static final class IconDrawable extends Drawable {
        static final int FREEFORM = 0, SPLIT = 1, INFO = 2, LOCK = 3;
        private final int type;
        private final int color;
        private final boolean locked;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private float ox, oy, s;

        IconDrawable(int type, int color, boolean locked) {
            this.type = type;
            this.color = color;
            this.locked = locked;
        }

        private float x(float f) {
            return ox + f * s;
        }

        private float y(float f) {
            return oy + f * s;
        }

        private RectF r(float l, float t, float rr, float b) {
            return new RectF(x(l), y(t), x(rr), y(b));
        }

        @Override
        public void draw(Canvas c) {
            Rect b = getBounds();
            s = Math.min(b.width(), b.height());
            ox = b.left + (b.width() - s) / 2f;
            oy = b.top + (b.height() - s) / 2f;
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(s * 0.08f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            float rad = s * 0.08f;
            switch (type) {
                case FREEFORM: {
                    c.drawRoundRect(r(0.12f, 0.14f, 0.66f, 0.58f), rad, rad, p);
                    p.setStyle(Paint.Style.FILL);
                    p.setColor(0xFF242424);
                    c.drawRoundRect(r(0.34f, 0.40f, 0.88f, 0.86f), rad, rad, p);
                    p.setStyle(Paint.Style.STROKE);
                    p.setColor(color);
                    c.drawRoundRect(r(0.34f, 0.40f, 0.88f, 0.86f), rad, rad, p);
                    break;
                }
                case SPLIT: {
                    c.drawRoundRect(r(0.18f, 0.12f, 0.82f, 0.88f), rad, rad, p);
                    c.drawLine(x(0.18f), y(0.5f), x(0.82f), y(0.5f), p);
                    break;
                }
                case INFO: {
                    c.drawCircle(x(0.5f), y(0.5f), s * 0.38f, p);
                    c.drawLine(x(0.5f), y(0.47f), x(0.5f), y(0.70f), p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(x(0.5f), y(0.32f), s * 0.05f, p);
                    break;
                }
                default: { // LOCK
                    float lift = locked ? 0f : s * 0.08f;
                    p.setStyle(Paint.Style.FILL);
                    c.drawRoundRect(r(0.22f, 0.46f, 0.78f, 0.88f), rad, rad, p);
                    p.setStyle(Paint.Style.STROKE);
                    RectF arc = r(0.33f, 0.14f, 0.67f, 0.54f);
                    arc.offset(0f, -lift);
                    c.drawArc(arc, 180f, 180f, false, p);
                    c.drawLine(x(0.33f), y(0.34f) - lift, x(0.33f), y(0.46f), p);
                    if (locked) c.drawLine(x(0.67f), y(0.34f), x(0.67f), y(0.46f), p);
                    break;
                }
            }
        }

        @Override
        public void setAlpha(int alpha) {
            p.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter cf) {
            p.setColorFilter(cf);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
