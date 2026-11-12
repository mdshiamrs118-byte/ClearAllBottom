package com.example.clearallbottom;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewOutlineProvider;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.Locale;

/** A small glass-style card showing used / free / total RAM (and optionally swap). */
public class RamView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final float d;
    private final ActivityManager.MemoryInfo mi = new ActivityManager.MemoryInfo();
    private long total, avail, swapTotal, swapFree;
    private int opacity = Prefs.D_RAM_OPACITY;
    private boolean showSwap = false;

    public RamView(Context c) {
        super(c);
        d = c.getResources().getDisplayMetrics().density;
        setClickable(false);
        setElevation(10 * d);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), 22 * d);
            }
        });
        refresh();
    }

    public void configure(int opacityPct, boolean swap) {
        opacity = Math.max(10, Math.min(100, opacityPct));
        boolean relayout = swap != showSwap;
        showSwap = swap;
        if (relayout) requestLayout();
        refresh();
    }

    public void refresh() {
        try {
            ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
            if (am != null) {
                am.getMemoryInfo(mi);
                total = mi.totalMem;
                avail = mi.availMem;
            }
        } catch (Throwable ignored) {
        }
        if (showSwap) readSwap();
        invalidate();
    }

    private void readSwap() {
        long st = 0, sf = 0;
        try {
            BufferedReader br = new BufferedReader(new FileReader("/proc/meminfo"));
            String line;
            while ((line = br.readLine()) != null) {
                if (line.startsWith("SwapTotal:")) st = kb(line);
                else if (line.startsWith("SwapFree:")) sf = kb(line);
            }
            br.close();
        } catch (Throwable ignored) {
        }
        swapTotal = st;
        swapFree = sf;
    }

    private static long kb(String line) {
        String[] parts = line.trim().split("\\s+");
        try {
            return Long.parseLong(parts[1]) * 1024L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private static String fmt(long bytes) {
        double gb = bytes / 1073741824.0;
        if (gb >= 1.0) return String.format(Locale.US, "%.1f GB", gb);
        return String.format(Locale.US, "%d MB", bytes / 1048576L);
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int sw = getResources().getDisplayMetrics().widthPixels;
        int w = (int) Math.min(sw - 32 * d, 340 * d);
        int h = (int) ((showSwap ? 116 : 94) * d);
        setMeasuredDimension(w, h);
    }

    private static int lighten(int c, float amt) {
        int r = Color.red(c), g = Color.green(c), b = Color.blue(c);
        return Color.rgb((int) (r + (255 - r) * amt), (int) (g + (255 - g) * amt),
                (int) (b + (255 - b) * amt));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        float pad = 18 * d;
        float used = Math.max(0, total - avail);
        float frac = total > 0 ? Math.min(1f, used / (float) total) : 0f;
        int accent = frac < 0.60f ? 0xFF3DDC97 : frac < 0.80f ? 0xFFFFB74D : 0xFFFF6B6B;
        int a = opacity * 255 / 100;

        // card background + border
        rect.set(0, 0, w, h);
        paint.setStyle(Paint.Style.FILL);
        paint.setShader(new LinearGradient(0, 0, 0, h, Color.argb(a, 0x30, 0x32, 0x3C),
                Color.argb(a, 0x18, 0x19, 0x1F), Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, 22 * d, 22 * d, paint);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(d);
        paint.setColor(0x33FFFFFF);
        rect.set(d / 2, d / 2, w - d / 2, h - d / 2);
        canvas.drawRoundRect(rect, 22 * d, 22 * d, paint);

        // title + percent
        paint.setStyle(Paint.Style.FILL);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(12 * d);
        paint.setLetterSpacing(0.14f);
        paint.setColor(0xFFB8BCC8);
        canvas.drawText("MEMORY", pad, 27 * d, paint);
        paint.setLetterSpacing(0f);
        paint.setTextAlign(Paint.Align.RIGHT);
        paint.setTextSize(15 * d);
        paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        paint.setColor(accent);
        canvas.drawText(Math.round(frac * 100) + "%", w - pad, 28 * d, paint);

        // usage bar
        float barW = w - 2 * pad;
        float top = 38 * d, bh = 10 * d;
        paint.setColor(0x26FFFFFF);
        rect.set(pad, top, pad + barW, top + bh);
        canvas.drawRoundRect(rect, bh / 2, bh / 2, paint);
        if (frac > 0f) {
            float fw = Math.max(bh, barW * frac);
            paint.setShader(new LinearGradient(pad, 0, pad + fw, 0, accent, lighten(accent, 0.35f),
                    Shader.TileMode.CLAMP));
            rect.set(pad, top, pad + fw, top + bh);
            canvas.drawRoundRect(rect, bh / 2, bh / 2, paint);
            paint.setShader(null);
        }

        // three stats: used / free / total
        String[] values = {fmt((long) used), fmt(avail), fmt(total)};
        String[] labels = {"Used", "Free", "Total"};
        int[] dots = {accent, 0xFF64B5F6, 0xFFE0E3EB};
        for (int i = 0; i < 3; i++) {
            float cx = pad + barW * (i * 2 + 1) / 6f;
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            paint.setTextSize(16 * d);
            paint.setColor(0xFFFFFFFF);
            canvas.drawText(values[i], cx, 72 * d, paint);

            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setTextSize(11 * d);
            paint.setColor(0xFF9EA3AE);
            canvas.drawText(labels[i], cx + 5 * d, 87 * d, paint);
            float tw = paint.measureText(labels[i]);
            paint.setColor(dots[i]);
            canvas.drawCircle(cx + 5 * d - tw / 2 - 7 * d, 83.5f * d, 3.2f * d, paint);
        }

        // optional swap line
        if (showSwap) {
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif", Typeface.NORMAL));
            paint.setTextSize(11 * d);
            paint.setColor(0xFF9EA3AE);
            String s = swapTotal > 0
                    ? "Swap  " + fmt(Math.max(0, swapTotal - swapFree)) + " / " + fmt(swapTotal)
                    : "Swap  none";
            canvas.drawText(s, w / 2f, 108 * d, paint);
        }
    }
}
