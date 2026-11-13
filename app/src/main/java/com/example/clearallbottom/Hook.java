package com.example.clearallbottom;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.view.animation.LinearInterpolator;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XC_MethodReplacement;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Recents Styler: restyles the Quickstep Recents screen (Original / Stack / Cover flow /
 * iPhone style), card size and corner roundness, and pins the Clear all button.
 */
public class Hook implements IXposedHookLoadPackage {

    private static final String TAG = "RecentsStyler: ";
    private static final String RV = "com.android.quickstep.views.RecentsView";

    // ---------------- live settings (reloaded from the settings app) ----------------
    private static volatile int style = Prefs.D_STYLE;
    private static volatile int widthPct = Prefs.D_WIDTH;
    private static volatile int heightPct = Prefs.D_HEIGHT;
    private static volatile int radiusDp = Prefs.D_RADIUS;
    private static volatile int yOffDp = Prefs.D_YOFF;
    private static volatile int spacingPct = Prefs.D_SPACING;
    private static volatile int shrinkPct = Prefs.D_SHRINK;
    private static volatile int fadePct = Prefs.D_FADE;
    private static volatile int tiltDeg = Prefs.D_TILT;
    private static volatile int depth = Prefs.D_DEPTH;
    private static volatile int liftDp = Prefs.D_LIFT;
    private static volatile boolean invertTilt = Prefs.D_INVERT;
    private static volatile boolean pinClear = Prefs.D_PIN;
    private static volatile boolean flatTilt = Prefs.D_FLAT;
    private static volatile boolean tapHome = Prefs.D_TAP_HOME;
    private static volatile boolean btnOn = Prefs.D_BTN_ON;
    private static volatile String btnText = Prefs.D_BTN_TEXT;
    private static volatile int btnTextColor = Prefs.D_BTN_TEXT_COLOR;
    private static volatile int btnTextSize = Prefs.D_BTN_TEXT_SIZE;
    private static volatile boolean btnBold = Prefs.D_BTN_BOLD;
    private static volatile int btnBg = Prefs.D_BTN_BG;
    private static volatile int btnRadius = Prefs.D_BTN_RADIUS;
    private static volatile int btnW = Prefs.D_BTN_W;
    private static volatile int btnH = Prefs.D_BTN_H;
    private static volatile int btnPadX = Prefs.D_BTN_PADX;
    private static volatile int btnStroke = Prefs.D_BTN_STROKE;
    private static volatile int btnStrokeColor = Prefs.D_BTN_STROKE_COLOR;
    private static volatile int btnShadow = Prefs.D_BTN_SHADOW;
    private static volatile int cfgVersion = 0;
    private static volatile Set<String> protectedSet = new HashSet<>();
    private static volatile boolean rootOn = Prefs.D_ROOT_ON;
    private static volatile String token = null;
    private static boolean clearHookOk = false;
    private static long lastClearAt = 0L;
    private static boolean clearing = false;
    private static boolean glidePending = false;
    private static View glideFocus;
    private static final long FLY_MS = 260L;
    private static final long GLIDE_MS = 280L;
    /** Where the kept cards were drawn (center x) just before the cleared ones were removed. */
    private static final WeakHashMap<View, Float> GLIDE_OLD = new WeakHashMap<>();
    /** Per kept card: {offset to glide away, start time}. */
    private static final WeakHashMap<View, float[]> GLIDE = new WeakHashMap<>();
    private static boolean lastClearHandled = false;
    /** Locks changed from the popup in this launcher session (pkg -> locked). */
    private static final Map<String, Boolean> OVERRIDES = new ConcurrentHashMap<>();
    private static volatile boolean iconPopup = Prefs.D_ICON_POPUP;
    private static volatile boolean transOn = Prefs.D_TRANS_ON;
    private static volatile int transMs = Prefs.D_TRANS_MS;
    private static volatile boolean ramOn = Prefs.D_RAM_ON;
    private static volatile boolean ramBottom = Prefs.D_RAM_BOTTOM;
    private static volatile int ramOffset = Prefs.D_RAM_OFFSET;
    private static volatile int ramOpacity = Prefs.D_RAM_OPACITY;
    private static volatile boolean ramSwap = Prefs.D_RAM_SWAP;
    private static RamView ramView;
    private static boolean ramTicking;
    private static int ramVersion = -1;
    private static long rvShownAt = 0L;
    private static boolean lastShown = false;
    private static boolean animating = false;
    private static View downIconCard;
    /** Per card: {time it last became visible, was visible last frame (0/1)}. */
    private static final WeakHashMap<View, long[]> ANIM = new WeakHashMap<>();
    private static volatile int clearBottomDp = Prefs.D_CLEAR_BOTTOM;

    private static XSharedPreferences xp;
    private static long lastCheck;
    private static boolean warned;

    private static void refreshCfg() {
        long now = SystemClock.uptimeMillis();
        if (now - lastCheck < 500) return;
        lastCheck = now;
        try {
            if (xp == null) {
                xp = new XSharedPreferences(Prefs.PKG, Prefs.NAME);
                loadCfg();
            } else if (xp.hasFileChanged()) {
                xp.reload();
                loadCfg();
            }
        } catch (Throwable t) {
            if (!warned) {
                warned = true;
                XposedBridge.log(TAG + "prefs: " + t);
            }
        }
    }

    private static void loadCfg() {
        if (!xp.getFile().canRead()) {
            if (!warned) {
                warned = true;
                XposedBridge.log(TAG + "settings file not readable yet, using defaults");
            }
            return;
        }
        style = xp.getInt(Prefs.K_STYLE, Prefs.D_STYLE);
        widthPct = xp.getInt(Prefs.K_WIDTH, Prefs.D_WIDTH);
        heightPct = xp.getInt(Prefs.K_HEIGHT, Prefs.D_HEIGHT);
        radiusDp = xp.getInt(Prefs.K_RADIUS, Prefs.D_RADIUS);
        yOffDp = xp.getInt(Prefs.K_YOFF, Prefs.D_YOFF);
        spacingPct = xp.getInt(Prefs.K_SPACING, Prefs.D_SPACING);
        shrinkPct = xp.getInt(Prefs.K_SHRINK, Prefs.D_SHRINK);
        fadePct = xp.getInt(Prefs.K_FADE, Prefs.D_FADE);
        tiltDeg = xp.getInt(Prefs.K_TILT, Prefs.D_TILT);
        depth = xp.getInt(Prefs.K_DEPTH, Prefs.D_DEPTH);
        liftDp = xp.getInt(Prefs.K_LIFT, Prefs.D_LIFT);
        invertTilt = xp.getBoolean(Prefs.K_INVERT, Prefs.D_INVERT);
        pinClear = xp.getBoolean(Prefs.K_PIN, Prefs.D_PIN);
        flatTilt = xp.getBoolean(Prefs.K_FLAT, Prefs.D_FLAT);
        tapHome = xp.getBoolean(Prefs.K_TAP_HOME, Prefs.D_TAP_HOME);
        btnOn = xp.getBoolean(Prefs.K_BTN_ON, Prefs.D_BTN_ON);
        String bt = xp.getString(Prefs.K_BTN_TEXT, Prefs.D_BTN_TEXT);
        btnText = bt == null ? "" : bt;
        btnTextColor = xp.getInt(Prefs.K_BTN_TEXT_COLOR, Prefs.D_BTN_TEXT_COLOR);
        btnTextSize = xp.getInt(Prefs.K_BTN_TEXT_SIZE, Prefs.D_BTN_TEXT_SIZE);
        btnBold = xp.getBoolean(Prefs.K_BTN_BOLD, Prefs.D_BTN_BOLD);
        btnBg = xp.getInt(Prefs.K_BTN_BG, Prefs.D_BTN_BG);
        btnRadius = xp.getInt(Prefs.K_BTN_RADIUS, Prefs.D_BTN_RADIUS);
        btnW = xp.getInt(Prefs.K_BTN_W, Prefs.D_BTN_W);
        btnH = xp.getInt(Prefs.K_BTN_H, Prefs.D_BTN_H);
        btnPadX = xp.getInt(Prefs.K_BTN_PADX, Prefs.D_BTN_PADX);
        btnStroke = xp.getInt(Prefs.K_BTN_STROKE, Prefs.D_BTN_STROKE);
        btnStrokeColor = xp.getInt(Prefs.K_BTN_STROKE_COLOR, Prefs.D_BTN_STROKE_COLOR);
        btnShadow = xp.getInt(Prefs.K_BTN_SHADOW, Prefs.D_BTN_SHADOW);
        String prot = xp.getString(Prefs.K_PROTECTED, "");
        Set<String> ps = new HashSet<>();
        if (prot != null) {
            for (String s : prot.split(",")) if (s.trim().length() > 0) ps.add(s.trim());
        }
        protectedSet = ps;
        for (Map.Entry<String, Boolean> e : new ArrayList<>(OVERRIDES.entrySet())) {
            if (ps.contains(e.getKey()) == e.getValue().booleanValue()) OVERRIDES.remove(e.getKey());
        }
        rootOn = xp.getBoolean(Prefs.K_ROOT_ON, Prefs.D_ROOT_ON);
        token = xp.getString(Prefs.K_TOKEN, null);
        iconPopup = xp.getBoolean(Prefs.K_ICON_POPUP, Prefs.D_ICON_POPUP);
        transOn = xp.getBoolean(Prefs.K_TRANS_ON, Prefs.D_TRANS_ON);
        transMs = xp.getInt(Prefs.K_TRANS_MS, Prefs.D_TRANS_MS);
        ramOn = xp.getBoolean(Prefs.K_RAM_ON, Prefs.D_RAM_ON);
        ramBottom = xp.getBoolean(Prefs.K_RAM_BOTTOM, Prefs.D_RAM_BOTTOM);
        ramOffset = xp.getInt(Prefs.K_RAM_OFFSET, Prefs.D_RAM_OFFSET);
        ramOpacity = xp.getInt(Prefs.K_RAM_OPACITY, Prefs.D_RAM_OPACITY);
        ramSwap = xp.getBoolean(Prefs.K_RAM_SWAP, Prefs.D_RAM_SWAP);
        cfgVersion++;
        clearBottomDp = xp.getInt(Prefs.K_CLEAR_BOTTOM, Prefs.D_CLEAR_BOTTOM);
    }

    // ---------------- state ----------------
    private static boolean placing = false;
    private static boolean sizing = false;
    private static boolean tracking = false;
    private static boolean pinnedApplied = false;
    private static Method drawChildMethod;
    private static float downX, downY;
    private static boolean tapActive, tapMoved;
    private static View downTarget;
    /** Original look of the Clear all button, and which settings version we last applied. */
    private static final WeakHashMap<View, Object[]> BTN_ORIG = new WeakHashMap<>();
    private static final WeakHashMap<View, Integer> BTN_VER = new WeakHashMap<>();
    private static final Set<View> LAYERED =
            Collections.newSetFromMap(new WeakHashMap<View, Boolean>());
    private static final WeakHashMap<ViewGroup, ViewTreeObserver> OBSERVERS = new WeakHashMap<>();

    /** Per-card record of what we last wrote, so we build on top of the launcher's own values. */
    private static final WeakHashMap<View, float[]> STATE = new WeakHashMap<>();
    /** Thumbnail views whose corners we changed (original values saved). */
    private static final WeakHashMap<View, Object[]> RADIUS = new WeakHashMap<>();
    /** Cards we decided not to draw this frame (too deep). */
    private static final Set<View> skipped =
            Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    // ---------------- entry ----------------
    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        if (Prefs.PKG.equals(lpparam.packageName)) {
            try {
                XposedHelpers.findAndHookMethod(Prefs.PKG + ".MainActivity", lpparam.classLoader,
                        "isModuleActive", XC_MethodReplacement.returnConstant(true));
            } catch (Throwable t) {
                XposedBridge.log(TAG + "self hook: " + t);
            }
            return;
        }

        final Class<?> recentsView = XposedHelpers.findClassIfExists(RV, lpparam.classLoader);
        if (recentsView == null) {
            return;
        }
        XposedBridge.log(TAG + "RecentsView found in " + lpparam.packageName);

        XC_MethodHook fix = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                apply(param.thisObject);
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                apply(param.thisObject);
            }
        };
        hookChain(recentsView, "onLayout", fix);
        hookChain(recentsView, "onPageScroll", fix);
        hookChain(recentsView, "updateClearAllButtonAlpha", fix);

        hookDraw(recentsView);
        hookClearAll(recentsView);
        hookTouchOrder();
        hookButtonTouch();

        Class<?> clearAll = XposedHelpers.findClassIfExists(
                "com.android.quickstep.views.ClearAllButton", lpparam.classLoader);
        if (clearAll != null) {
            XC_MethodHook buttonFix = new XC_MethodHook() {
                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    View b = (View) param.thisObject;
                    if (b.getParent() instanceof ViewGroup) {
                        place((ViewGroup) b.getParent(), b);
                    }
                }
            };
            hookChain(clearAll, "onLayout", buttonFix);
            hookChain(clearAll, "onRecentsViewScroll", buttonFix);
            hookChain(clearAll, "onPageScroll", buttonFix);
            hookChain(clearAll, "updateAlpha", buttonFix);
            hookChain(clearAll, "setContentAlpha", buttonFix);
            hookChain(clearAll, "setVisibilityAlpha", buttonFix);
            hookChain(clearAll, "setTranslationX", buttonFix);
            hookChain(clearAll, "setTranslationY", buttonFix);
        }
    }

    // ---------------- drawing ----------------
    private static void hookDraw(Class<?> recentsView) {
        for (Class<?> c = recentsView; c != null && c != ViewGroup.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod("dispatchDraw", Canvas.class);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!isRecentsView(param.thisObject.getClass())) return;
                        try {
                            refreshCfg();
                            ViewGroup rv = (ViewGroup) param.thisObject;
                            apply(rv);
                            if (style == Prefs.STYLE_ORIGINAL) {
                                restoreAll(rv);
                                return; // stock drawing; button is drawn in afterHook
                            }
                            List<View> order = buildOrder(rv, true);
                            Canvas canvas = (Canvas) param.args[0];
                            long time = rv.getDrawingTime();
                            Method dc = getDrawChild();
                            for (View v : order) {
                                if (v.getVisibility() != View.VISIBLE) continue;
                                if (skipped.contains(v)) continue;
                                dc.invoke(rv, canvas, v, time);
                            }
                            param.setObjectExtra("custom", Boolean.TRUE);
                            param.setResult(null);
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + "draw: " + t); // fall back to stock draw
                        }
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!isRecentsView(param.thisObject.getClass())) return;
                        if (param.getObjectExtra("custom") != null || !pinClear) return;
                        try {
                            ViewGroup rv = (ViewGroup) param.thisObject;
                            View b = findButton(rv);
                            if (b != null && b.getVisibility() == View.VISIBLE) {
                                getDrawChild().invoke(rv, (Canvas) param.args[0], b,
                                        rv.getDrawingTime());
                            }
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + "draw button: " + t);
                        }
                    }
                });
                XposedBridge.log(TAG + "draw hooked in " + c.getName());
                return;
            } catch (NoSuchMethodException ignored) {
            } catch (Throwable t) {
                XposedBridge.log(TAG + "hookDraw: " + t);
                return;
            }
        }
    }

    private static Method getDrawChild() throws Exception {
        if (drawChildMethod == null) {
            drawChildMethod = ViewGroup.class.getDeclaredMethod(
                    "drawChild", Canvas.class, View.class, long.class);
            drawChildMethod.setAccessible(true);
        }
        return drawChildMethod;
    }

    /**
     * Returns ALL children ordered back-to-front (last = topmost). When applyTransforms is
     * true, also applies the selected style's transforms to the cards.
     */
    private static List<View> buildOrder(ViewGroup rv, boolean applyTransforms) {
        final int n = rv.getChildCount();
        final int st = style;
        final long now = SystemClock.uptimeMillis();
        if (applyTransforms) animating = false;
        List<View> others = new ArrayList<>();
        final List<View> cards = new ArrayList<>();
        View clearAll = null;
        for (int i = 0; i < n; i++) {
            View v = rv.getChildAt(i);
            String name = v.getClass().getName();
            if (name.endsWith("ClearAllButton")) clearAll = v;
            else if (name.endsWith("TaskView")) cards.add(v);
            else others.add(v);
        }

        final IdentityHashMap<View, Float> key = new IdentityHashMap<>();
        if (!cards.isEmpty()) {
            float density = rv.getResources().getDisplayMetrics().density;
            float rvCenter = rv.getWidth() / 2f;
            float step;
            if (cards.size() >= 2) {
                View a = cards.get(0), b = cards.get(1);
                step = Math.abs((b.getLeft() + b.getWidth() / 2f) - (a.getLeft() + a.getWidth() / 2f));
            } else {
                step = cards.get(0).getWidth() * 1.1f;
            }
            if (step < 1f) step = Math.max(1f, rv.getWidth() * 0.9f);

            float sizeX = widthPct / 100f;
            float sizeY = heightPct / 100f;
            float spacing = spacingPct / 100f;
            float shrink = shrinkPct / 100f;
            float fade = fadePct / 100f;
            float maxDepth = depth + 0.6f;
            float yOff = yOffDp * density;

            for (View c : cards) {
                float layoutCenter = c.getLeft() + c.getWidth() / 2f;
                float tLayout = (layoutCenter - rv.getScrollX() - rvCenter) / step;
                // Include the launcher's own horizontal translation (e.g. the gap-closing
                // slide after a dismiss) so our layout follows where the card would really be.
                float t = tLayout + baseTx(c) / step;
                float tt = Math.abs(t);
                float s = Math.signum(t);
                // sort key: ascending = back to front
                if (st == Prefs.STYLE_IPHONE) key.put(c, t < 0 ? -tt : tt + 0.0001f);
                else key.put(c, -tt);

                if (!applyTransforms) continue;

                if (tt > maxDepth) {
                    skipped.add(c);
                    continue;
                }
                skipped.remove(c);

                // size progress: 0 = launcher's own card size, 1 = your custom size
                float pr = 1f;
                if (transOn) {
                    boolean vis = c.getVisibility() == View.VISIBLE && baseAlpha(c) > 0.02f;
                    long[] an = ANIM.get(c);
                    if (an == null) {
                        an = new long[]{0L, vis ? 1L : 0L};
                        ANIM.put(c, an);
                    }
                    if (vis && an[1] == 0L) an[0] = now;
                    an[1] = vis ? 1L : 0L;
                    long start = Math.max(an[0], rvShownAt);
                    if (start > 0L) {
                        float xx = (now - start) / (float) Math.max(60, transMs);
                        if (xx < 1f) {
                            xx = Math.max(0f, xx);
                            pr = 1f - (1f - xx) * (1f - xx);
                            animating = true;
                        }
                    }
                }
                float curX = 1f + (sizeX - 1f) * pr;
                float curY = 1f + (sizeY - 1f) * pr;

                float eff = tt <= 1f ? tt : 1f + 0.35f * (tt - 1f);
                float scale = 1f;
                float visual = t * step;
                float dy = 0f;
                float alpha = 1f;
                float rot = 0f;
                float squeeze = 1f;

                switch (st) {
                    case Prefs.STYLE_STACK:
                        scale = Math.max(0.5f, 1f - shrink * tt);
                        visual = s * step * spacing * eff;
                        dy = -liftDp * density * Math.min(tt, 3f);
                        alpha = Math.max(0f, 1f - fade * tt);
                        break;
                    case Prefs.STYLE_COVERFLOW:
                        scale = Math.max(0.5f, 1f - shrink * tt);
                        visual = s * step * spacing * eff;
                        alpha = Math.max(0f, 1f - fade * tt);
                        rot = (invertTilt ? 1f : -1f) * s * tiltDeg * Math.min(tt, 1f);
                        if (flatTilt) {
                            squeeze = (float) Math.cos(Math.toRadians(Math.abs(rot)));
                            rot = 0f;
                        }
                        break;
                    case Prefs.STYLE_IPHONE:
                        if (t < 0f) {
                            // older cards pile up behind, peeking out on the left
                            float e = tt <= 1f ? tt : 1f + 0.25f * (tt - 1f);
                            scale = Math.max(0.5f, 1f - shrink * tt);
                            visual = -step * spacing * 0.7f * e;
                            alpha = Math.max(0f, 1f - fade * tt);
                        } else {
                            // the card slides away to the right at normal speed, on top
                            scale = 1f;
                            visual = t * step;
                        }
                        break;
                    default:
                        break;
                }
                float txAbs = visual - tLayout * step; // absolute translation to reach 'visual'
                applyCard(c, scale * curX * squeeze, scale * curY, txAbs + glideOffset(c, now), dy + yOff,
                        alpha, rot, density);
                applyRadius(c, density);
            }
        }

        Collections.sort(cards, new Comparator<View>() {
            @Override
            public int compare(View a, View b) {
                Float ka = key.get(a), kb = key.get(b);
                float fa = ka == null ? 0f : ka, fb = kb == null ? 0f : kb;
                int c = Float.compare(fa, fb);
                if (c != 0) return c;
                ViewGroup pa = (ViewGroup) a.getParent();
                int ia = pa == null ? 0 : pa.indexOfChild(a);
                int ib = pa == null ? 0 : pa.indexOfChild(b);
                return Integer.compare(ib, ia);
            }
        });

        List<View> order = new ArrayList<>(n);
        order.addAll(others);
        order.addAll(cards);
        if (clearAll != null) order.add(clearAll);
        return order;
    }

    /** Remaining horizontal glide (px) for a card that has to slide into its new spot. */
    private static float glideOffset(View c, long now) {
        float[] g = GLIDE.get(c);
        if (g == null) return 0f;
        float x = (now - (long) g[1]) / (float) GLIDE_MS;
        if (x >= 1f) {
            GLIDE.remove(c);
            return 0f;
        }
        x = Math.max(0f, x);
        float inv = 1f - x;
        animating = true;
        return g[0] * inv * inv * inv; // ease-out cubic
    }

    /** The launcher's own alpha for a card, ignoring what we wrote. */
    private static float baseAlpha(View v) {
        float[] s = STATE.get(v);
        return (s != null && v.getAlpha() == s[8]) ? s[9] : v.getAlpha();
    }

    /** The launcher's own translationX for a card, ignoring what we wrote. */
    private static float baseTx(View v) {
        float[] s = STATE.get(v);
        return (s != null && v.getTranslationX() == s[4]) ? s[5] : v.getTranslationX();
    }

    /** Sets scale/translation/alpha as (launcher's own value) combined with our effect. */
    private static void applyCard(View v, float sx, float sy, float dx, float dy,
                                  float alpha, float rot, float density) {
        float[] s = STATE.get(v);
        if (s == null) {
            s = new float[10];
            for (int i = 0; i < s.length; i++) s[i] = Float.NaN;
            STATE.put(v, s);
        }
        // [lastSX, baseSX, lastSY, baseSY, lastTX, baseTX, lastTY, baseTY, lastA, baseA]
        float bSX = (v.getScaleX() == s[0]) ? s[1] : v.getScaleX();
        float bSY = (v.getScaleY() == s[2]) ? s[3] : v.getScaleY();
        float bTX = (v.getTranslationX() == s[4]) ? s[5] : v.getTranslationX();
        float bTY = (v.getTranslationY() == s[6]) ? s[7] : v.getTranslationY();
        float bA = (v.getAlpha() == s[8]) ? s[9] : v.getAlpha();

        v.setScaleX(bSX * sx);
        v.setScaleY(bSY * sy);
        v.setTranslationX(dx); // absolute (already includes the launcher's own translation)
        v.setTranslationY(bTY + dy);
        v.setAlpha(bA * alpha);
        if (rot != 0f) {
            v.setCameraDistance(density * 6000f);
            // Render 3D-tilted cards through an offscreen layer so their content isn't lost.
            if (LAYERED.add(v)) v.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        } else if (LAYERED.remove(v)) {
            v.setLayerType(View.LAYER_TYPE_NONE, null);
        }
        if (v.getRotationY() != rot) v.setRotationY(rot);

        s[0] = v.getScaleX();        s[1] = bSX;
        s[2] = v.getScaleY();        s[3] = bSY;
        s[4] = v.getTranslationX();  s[5] = bTX;
        s[6] = v.getTranslationY();  s[7] = bTY;
        s[8] = v.getAlpha();         s[9] = bA;
    }

    /** Puts every card back to what the launcher set (used by Original style). */
    private static void restoreAll(ViewGroup rv) {
        if (STATE.isEmpty() && RADIUS.isEmpty() && skipped.isEmpty()) return;
        for (int i = 0; i < rv.getChildCount(); i++) {
            View v = rv.getChildAt(i);
            float[] s = STATE.get(v);
            if (s != null) {
                if (v.getScaleX() == s[0]) v.setScaleX(s[1]);
                if (v.getScaleY() == s[2]) v.setScaleY(s[3]);
                if (v.getTranslationX() == s[4]) v.setTranslationX(s[5]);
                if (v.getTranslationY() == s[6]) v.setTranslationY(s[7]);
                if (v.getAlpha() == s[8]) v.setAlpha(s[9]);
                v.setRotationY(0f);
            }
            if (LAYERED.remove(v)) v.setLayerType(View.LAYER_TYPE_NONE, null);
            View th = findThumb(v);
            if (th != null) restoreRadius(th);
        }
        STATE.clear();
        skipped.clear();
    }

    // ---------------- corner roundness ----------------
    private static View findThumb(View card) {
        if (!(card instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) card;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.getClass().getName().endsWith("TaskThumbnailView")) return c;
        }
        return null;
    }

    private static float getF(View v, String name) {
        try {
            return XposedHelpers.getFloatField(v, name);
        } catch (Throwable t) {
            return Float.NaN;
        }
    }

    private static void setF(View v, String name, float value) {
        try {
            XposedHelpers.setFloatField(v, name, value);
        } catch (Throwable ignored) {
        }
    }

    private static void applyRadius(View card, float density) {
        View th = findThumb(card);
        if (th == null) return;
        Object[] st = RADIUS.get(th);
        if (radiusDp <= 0) {
            if (st != null) restoreRadius(th);
            return;
        }
        final float r = radiusDp * density;
        if (st != null && ((Float) st[4]).floatValue() == r) return;
        if (st == null) {
            st = new Object[]{th.getOutlineProvider(), th.getClipToOutline(),
                    getF(th, "mCornerRadius"), getF(th, "mWindowCornerRadius"), -1f};
            RADIUS.put(th, st);
        }
        setF(th, "mCornerRadius", r);
        setF(th, "mWindowCornerRadius", r);
        th.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), r);
            }
        });
        th.setClipToOutline(true);
        th.invalidateOutline();
        th.invalidate();
        st[4] = r;
    }

    private static void restoreRadius(View th) {
        Object[] st = RADIUS.remove(th);
        if (st == null) return;
        th.setOutlineProvider((ViewOutlineProvider) st[0]);
        th.setClipToOutline((Boolean) st[1]);
        float c = (Float) st[2], w = (Float) st[3];
        if (!Float.isNaN(c)) setF(th, "mCornerRadius", c);
        if (!Float.isNaN(w)) setF(th, "mWindowCornerRadius", w);
        th.invalidateOutline();
        th.invalidate();
    }

    // ---------------- touch ----------------
    /** Makes touch hit-testing use the same front-to-back order as drawing. */
    private static void hookTouchOrder() {
        try {
            XposedHelpers.findAndHookMethod(ViewGroup.class, "buildTouchDispatchChildList",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (style == Prefs.STYLE_ORIGINAL
                                        || !isRecentsView(param.thisObject.getClass())) return;
                                ViewGroup rv = (ViewGroup) param.thisObject;
                                List<View> order = buildOrder(rv, false);
                                if (order.size() == rv.getChildCount()) {
                                    param.setResult(new ArrayList<>(order));
                                }
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "touchOrder: " + t);
                            }
                        }
                    });
            XposedBridge.log(TAG + "touch order hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hookTouchOrder: " + t);
        }
    }

    /** Topmost visible card under (x, y), using each card's real (transformed) bounds. */
    private static View cardAt(ViewGroup rv, float x, float y) {
        List<View> order = buildOrder(rv, false);
        for (int i = order.size() - 1; i >= 0; i--) {
            View c = order.get(i);
            if (!c.getClass().getName().endsWith("TaskView")) continue;
            if (c.getVisibility() != View.VISIBLE || skipped.contains(c)) continue;
            float[] pt = {x + rv.getScrollX() - c.getLeft(), y + rv.getScrollY() - c.getTop()};
            Matrix m = c.getMatrix();
            if (!m.isIdentity()) {
                Matrix inv = new Matrix();
                if (m.invert(inv)) inv.mapPoints(pt);
            }
            if (pt[0] >= 0 && pt[0] <= c.getWidth() && pt[1] >= 0 && pt[1] <= c.getHeight()) {
                return c;
            }
        }
        return null;
    }

    /** Taps on the pinned Clear all button. Returns true if the event was consumed. */
    private static boolean handleButtonTouch(ViewGroup rv, MotionEvent ev,
                                             XC_MethodHook.MethodHookParam param) {
        if (!pinClear) return false;
        View b = findButton(rv);
        if (b == null || b.getVisibility() != View.VISIBLE) return false;
        int action = ev.getActionMasked();

        float pad = 8 * rv.getResources().getDisplayMetrics().density;
        float l = b.getX() - rv.getScrollX() - pad;
        float top = b.getY() - pad;
        float r = l + b.getWidth() + 2 * pad;
        float bottom = top + b.getHeight() + 2 * pad;
        boolean inside = ev.getX() >= l && ev.getX() <= r
                && ev.getY() >= top && ev.getY() <= bottom;

        if (action == MotionEvent.ACTION_DOWN) {
            tracking = inside;
            if (tracking) {
                b.setPressed(true);
                param.setResult(true);
                return true;
            }
        } else if (tracking) {
            if (action == MotionEvent.ACTION_UP) {
                tracking = false;
                b.setPressed(false);
                if (inside) {
                    if (!(!clearHookOk && handleClearAll(rv))) b.performClick();
                }
            } else if (action == MotionEvent.ACTION_CANCEL) {
                tracking = false;
                b.setPressed(false);
            }
            param.setResult(true);
            return true;
        }
        return false;
    }

    /**
     * Handles taps on the Clear all button, and makes card taps follow the cards' VISIBLE
     * (resized / moved) areas only: a tap is resolved by us and the stock handling is cancelled.
     */
    private static void hookButtonTouch() {
        try {
            XposedHelpers.findAndHookMethod(ViewGroup.class, "dispatchTouchEvent",
                    MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (!isRecentsView(param.thisObject.getClass())) return;
                        ViewGroup rv = (ViewGroup) param.thisObject;
                        MotionEvent ev = (MotionEvent) param.args[0];
                        if (handleButtonTouch(rv, ev, param)) return;
                        if (style == Prefs.STYLE_ORIGINAL && !iconPopup) return;

                        int action = ev.getActionMasked();
                        float slop = ViewConfiguration.get(rv.getContext()).getScaledTouchSlop();
                        if (action == MotionEvent.ACTION_DOWN) {
                            downX = ev.getX();
                            downY = ev.getY();
                            tapMoved = false;
                            tapActive = true;
                            downIconCard = iconPopup ? iconTargetAt(rv, downX, downY) : null;
                            downTarget = downIconCard != null ? downIconCard
                                    : cardAt(rv, downX, downY);
                        } else if (tapActive) {
                            if (action == MotionEvent.ACTION_MOVE) {
                                if (Math.hypot(ev.getX() - downX, ev.getY() - downY) > slop) {
                                    tapMoved = true;
                                }
                            } else if (action == MotionEvent.ACTION_POINTER_DOWN) {
                                tapMoved = true;
                            } else if (action == MotionEvent.ACTION_CANCEL) {
                                tapActive = false;
                            } else if (action == MotionEvent.ACTION_UP) {
                                tapActive = false;
                                if (!tapMoved) {
                                    // A tap: stock code must not decide what it hit.
                                    MotionEvent cancel = MotionEvent.obtain(ev);
                                    cancel.setAction(MotionEvent.ACTION_CANCEL);
                                    param.args[0] = cancel;
                                    View iconCard = downIconCard;
                                    View target = downTarget;
                                    View upTarget = cardAt(rv, ev.getX(), ev.getY());
                                    if (iconCard != null
                                            && iconTargetAt(rv, ev.getX(), ev.getY()) == iconCard) {
                                        param.setObjectExtra("tapIcon", iconCard);
                                    } else if (target != null && upTarget == target) {
                                        param.setObjectExtra("tapCard", target);
                                    } else if (target == null && upTarget == null && tapHome) {
                                        param.setObjectExtra("tapHome", Boolean.TRUE);
                                    }
                                }
                                downTarget = null;
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "touch: " + t);
                    }
                }

                @Override
                protected void afterHookedMethod(MethodHookParam param) {
                    try {
                        Object card = param.getObjectExtra("tapCard");
                        if (card instanceof View) ((View) card).performClick();
                        Object ic = param.getObjectExtra("tapIcon");
                        if (ic instanceof View) {
                            openIconPopup((ViewGroup) param.thisObject, (View) ic);
                        }
                        if (param.getObjectExtra("tapHome") != null) {
                            Intent home = new Intent(Intent.ACTION_MAIN);
                            home.addCategory(Intent.CATEGORY_HOME);
                            home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                            ((ViewGroup) param.thisObject).getContext().startActivity(home);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "tap click: " + t);
                    }
                }
            });
            XposedBridge.log(TAG + "touch hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hookButtonTouch: " + t);
        }
    }

    /**
     * The launcher animates card properties (e.g. after a swipe-up dismiss) without
     * re-running dispatchDraw, which would briefly show the stock look. Re-apply our
     * transforms right before every frame so that never happens.
     */
    private static void ensurePreDraw(final ViewGroup rv) {
        ViewTreeObserver vto = rv.getViewTreeObserver();
        if (vto == null || !vto.isAlive() || OBSERVERS.get(rv) == vto) return;
        OBSERVERS.put(rv, vto);
        vto.addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
            @Override
            public boolean onPreDraw() {
                try {
                    boolean shown = rv.isAttachedToWindow() && rv.getVisibility() == View.VISIBLE
                            && rv.getAlpha() > 0f && rv.isShown();
                    if (shown && !lastShown) rvShownAt = SystemClock.uptimeMillis();
                    if (!shown && lastShown) RecentsPopup.dismiss();
                    lastShown = shown;
                    syncRam(rv, shown);
                    if (glidePending && shown) prepareGlide(rv);
                    if (style != Prefs.STYLE_ORIGINAL && shown) {
                        refreshCfg();
                        buildOrder(rv, true);
                        View b = findButton(rv);
                        if (b != null) place(rv, b);
                        if (animating) rv.postInvalidateOnAnimation();
                    }
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "preDraw: " + t);
                }
                return true;
            }
        });
    }

    /**
     * The launcher measures this button with its own size rules, which can ignore our
     * width/height. Re-measure and re-lay it out at exactly the requested size, keeping
     * its center where the launcher put it.
     */
    private static void enforceSize(View button) {
        if (!btnOn || (btnW <= 0 && btnH <= 0) || sizing) return;
        float d = button.getResources().getDisplayMetrics().density;
        int tw = btnW > 0 ? Math.round(btnW * d) : button.getWidth();
        int th = btnH > 0 ? Math.round(btnH * d) : button.getHeight();
        if (tw <= 0 || th <= 0) return;
        if (button.getWidth() == tw && button.getHeight() == th) return;
        sizing = true;
        try {
            int cx = button.getLeft() + button.getWidth() / 2;
            int cy = button.getTop() + button.getHeight() / 2;
            button.measure(View.MeasureSpec.makeMeasureSpec(tw, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(th, View.MeasureSpec.EXACTLY));
            int left = cx - tw / 2;
            int top = btnH > 0 ? button.getTop() : cy - th / 2;
            button.layout(left, top, left + tw, top + th);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "enforceSize: " + t);
        } finally {
            sizing = false;
        }
    }

    // ---------------- RAM card ----------------
    /** Keeps the RAM card in the launcher window, visible only while Recents is. */
    private static void syncRam(final ViewGroup rv, boolean shown) {
        try {
            if (!ramOn) {
                if (ramView != null) ramView.setVisibility(View.GONE);
                return;
            }
            View rootV = rv.getRootView();
            if (!(rootV instanceof FrameLayout)) return;
            FrameLayout root = (FrameLayout) rootV;
            float d = rv.getResources().getDisplayMetrics().density;
            if (ramView == null || ramView.getParent() != root) {
                if (ramView != null && ramView.getParent() instanceof ViewGroup) {
                    ((ViewGroup) ramView.getParent()).removeView(ramView);
                }
                ramView = new RamView(rv.getContext());
                root.addView(ramView, new FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));
                ramVersion = -1;
            }
            if (ramVersion != cfgVersion) {
                ramVersion = cfgVersion;
                ramView.configure(ramOpacity, ramSwap);
                WindowInsets wi = rv.getRootWindowInsets();
                int topInset = wi != null ? wi.getSystemWindowInsetTop() : Math.round(24 * d);
                int bottomInset = wi != null ? wi.getSystemWindowInsetBottom() : 0;
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) ramView.getLayoutParams();
                lp.gravity = (ramBottom ? Gravity.BOTTOM : Gravity.TOP) | Gravity.CENTER_HORIZONTAL;
                lp.topMargin = ramBottom ? 0 : topInset + Math.round(ramOffset * d);
                lp.bottomMargin = ramBottom ? bottomInset + Math.round(ramOffset * d) : 0;
                ramView.setLayoutParams(lp);
            }
            ramView.setVisibility(shown ? View.VISIBLE : View.INVISIBLE);
            ramView.setAlpha(rv.getAlpha());
            if (shown && !ramTicking) {
                ramTicking = true;
                ramView.refresh();
                scheduleRamTick(rv);
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + "ram: " + t);
        }
    }

    private static void scheduleRamTick(final ViewGroup rv) {
        rv.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (ramOn && lastShown && ramView != null) {
                    ramView.refresh();
                    rv.postDelayed(this, 1000);
                } else {
                    ramTicking = false;
                }
            }
        }, 1000);
    }

    // ---------------- icon popup helpers ----------------
    private static View iconOf(View card) {
        if (!(card instanceof ViewGroup)) return null;
        ViewGroup g = (ViewGroup) card;
        for (int i = 0; i < g.getChildCount(); i++) {
            View c = g.getChildAt(i);
            if (c.getClass().getName().endsWith("IconView")) return c;
        }
        return null;
    }

    /** Topmost card whose app icon is under (x, y); icons may stick out above their card. */
    private static View iconTargetAt(ViewGroup rv, float x, float y) {
        List<View> order = buildOrder(rv, false);
        float pad = 10 * rv.getResources().getDisplayMetrics().density;
        for (int i = order.size() - 1; i >= 0; i--) {
            View c = order.get(i);
            if (!c.getClass().getName().endsWith("TaskView")) continue;
            if (c.getVisibility() != View.VISIBLE || skipped.contains(c)) continue;
            View ic = iconOf(c);
            if (ic == null || ic.getVisibility() != View.VISIBLE) continue;
            float[] pt = {x + rv.getScrollX() - c.getLeft(), y + rv.getScrollY() - c.getTop()};
            Matrix m = c.getMatrix();
            if (!m.isIdentity()) {
                Matrix inv = new Matrix();
                if (m.invert(inv)) inv.mapPoints(pt);
            }
            if (pt[0] >= ic.getX() - pad && pt[0] <= ic.getX() + ic.getWidth() + pad
                    && pt[1] >= ic.getY() - pad && pt[1] <= ic.getY() + ic.getHeight() + pad) {
                return c;
            }
        }
        return null;
    }

    private static void openIconPopup(ViewGroup rv, View card) {
        try {
            Object[] info = taskInfo(card);
            View icon = iconOf(card);
            if (info == null || icon == null || info[0] == null) return;
            RecentsPopup.show(rv, card, icon, (String) info[0], (Integer) info[1]);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "iconPopup: " + t);
        }
    }

    static boolean isLocked(String pkg) {
        Boolean o = OVERRIDES.get(pkg);
        return o != null ? o.booleanValue() : protectedSet.contains(pkg);
    }

    /** Protected list as the launcher currently sees it (file + changes made this session). */
    private static Set<String> effectiveProtected() {
        Set<String> s = new HashSet<>(protectedSet);
        for (Map.Entry<String, Boolean> e : OVERRIDES.entrySet()) {
            if (e.getValue().booleanValue()) s.add(e.getKey());
            else s.remove(e.getKey());
        }
        return s;
    }

    /** Locks/unlocks an app (adds/removes it from the protected list). Null if it can't be saved. */
    static Boolean toggleLock(Context ctx, String pkg) {
        String tk = token;
        if (tk == null) return null;
        boolean lock = !isLocked(pkg);
        OVERRIDES.put(pkg, lock);
        try {
            Intent i = new Intent(Prefs.ACTION_LOCK);
            i.setClassName(Prefs.PKG, Prefs.PKG + ".CleanReceiver");
            i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            i.putExtra("token", tk);
            i.putExtra("pkg", pkg);
            i.putExtra("lock", lock);
            ctx.sendBroadcast(i);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "toggleLock: " + t);
        }
        return lock;
    }

    // ---------------- Clear all: protected apps + root clean ----------------
    private static void hookClearAll(Class<?> recentsView) {
        XC_MethodHook clearHook = new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (!isRecentsView(param.thisObject.getClass())) return;
                try {
                    refreshCfg();
                    if (handleClearAll((ViewGroup) param.thisObject)) param.setResult(null);
                } catch (Throwable t) {
                    XposedBridge.log(TAG + "clearAll: " + t);
                    // never let a failure wipe locked apps
                    if (!effectiveProtected().isEmpty()) param.setResult(null);
                }
            }
        };
        int n = 0;
        for (Class<?> c = recentsView; c != null && c != ViewGroup.class && c != Object.class;
             c = c.getSuperclass()) {
            try {
                n += XposedBridge.hookAllMethods(c, "dismissAllTasks", clearHook).size();
            } catch (Throwable t) {
                XposedBridge.log(TAG + "hookClearAll: " + t);
            }
        }
        clearHookOk = n > 0;
        XposedBridge.log(TAG + "dismissAllTasks hooks: " + n);

        // Name-independent: catch the Clear all button's click itself.
        try {
            XposedHelpers.findAndHookMethod(View.class, "performClick", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Object t = param.thisObject;
                    if (!t.getClass().getName().endsWith("ClearAllButton")) return;
                    ViewParent p = ((View) t).getParent();
                    if (!(p instanceof ViewGroup) || !isRecentsView(p.getClass())) return;
                    try {
                        refreshCfg();
                        if (handleClearAll((ViewGroup) p)) param.setResult(true);
                    } catch (Throwable e) {
                        XposedBridge.log(TAG + "clearAll click: " + e);
                        if (!effectiveProtected().isEmpty()) param.setResult(true);
                    }
                }
            });
            clearHookOk = true;
            XposedBridge.log(TAG + "Clear all click hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "click hook: " + t);
        }
    }

    /** {packageName, taskId} of a card, or null if unreadable. */
    private static Object[] taskInfo(View card) {
        try {
            Object task = XposedHelpers.callMethod(card, "getTask");
            if (task == null) return null;
            Object key = XposedHelpers.getObjectField(task, "key");
            String pkg = (String) XposedHelpers.callMethod(key, "getPackageName");
            int id = XposedHelpers.getIntField(key, "id");
            return new Object[]{pkg, id};
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Returns true if we cleared things ourselves (some apps are protected), false if the
     * stock "clear everything" should go ahead.
     */
    private static boolean handleClearAll(ViewGroup rv) {
        long nowMs = SystemClock.uptimeMillis();
        if (nowMs - lastClearAt < 700L) return lastClearHandled; // same tap seen by two hooks
        lastClearAt = nowMs;
        lastClearHandled = false;
        Set<String> prot = effectiveProtected();
        if (prot.isEmpty() && !rootOn) return false;

        List<View> cards = new ArrayList<>();
        for (int i = 0; i < rv.getChildCount(); i++) {
            View c = rv.getChildAt(i);
            if (c.getClass().getName().endsWith("TaskView")) cards.add(c);
        }

        boolean anyProtected = false;
        List<View> removeViews = new ArrayList<>();
        List<Integer> removeIds = new ArrayList<>();
        List<String> pkgs = new ArrayList<>();
        for (View c : cards) {
            Object[] info = taskInfo(c);
            if (info == null) {
                if (!prot.isEmpty()) anyProtected = true; // unknown card: keep it to be safe
                continue;
            }
            String pkg = (String) info[0];
            if (prot.contains(pkg)) {
                anyProtected = true;
                continue;
            }
            removeViews.add(c);
            removeIds.add((Integer) info[1]);
            pkgs.add(pkg);
        }

        Context ctx = rv.getContext();
        XposedBridge.log(TAG + "clearAll: locked=" + prot.size() + " cards=" + cards.size()
                + " removing=" + removeViews.size() + " keepingLocked=" + anyProtected);
        if (!anyProtected) {
            sendClean(ctx, pkgs);
            return false; // nothing protected on screen: stock clear-all removes everything
        }

        sendClean(ctx, pkgs);
        lastClearHandled = true;
        if (!clearing && !removeViews.isEmpty()) {
            clearing = true;
            animatedClear(rv, removeViews, removeIds);
        }
        return true;
    }

    /**
     * One clean motion: every unlocked card flies up and fades together, then they are
     * removed in one go and the locked cards glide to their new place.
     */
    private static void animatedClear(final ViewGroup rv, final List<View> targets,
                                      final List<Integer> ids) {
        try {
            final int n = targets.size();
            final float h = Math.max(1f, rv.getHeight());
            final float[] startTy = new float[n];
            final float[] startA = new float[n];
            for (int i = 0; i < n; i++) {
                targets.get(i).animate().cancel();
                startTy[i] = targets.get(i).getTranslationY();
                startA[i] = targets.get(i).getAlpha();
            }
            final long stagger = 14L;
            final long maxDelay = Math.min(n * stagger, 98L);
            final long total = FLY_MS + maxDelay;
            ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
            va.setDuration(total);
            va.setInterpolator(new LinearInterpolator());
            va.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator a) {
                    long elapsed = (long) (a.getAnimatedFraction() * total);
                    for (int i = 0; i < n; i++) {
                        View c = targets.get(i);
                        if (c.getParent() != rv) continue;
                        long local = elapsed - Math.min(i * stagger, maxDelay);
                        float p = Math.max(0f, Math.min(1f, local / (float) FLY_MS));
                        c.setTranslationY(startTy[i] - h * 0.9f * p * p);
                        c.setAlpha(startA[i] * (1f - Math.min(1f, p / 0.6f)));
                    }
                    rv.invalidate();
                }
            });
            va.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    finishClear(rv, targets, ids);
                }
            });
            va.start();
        } catch (Throwable t) {
            XposedBridge.log(TAG + "animatedClear: " + t);
            clearUnprotected(rv, 0); // fallback: launcher's own dismiss, retried
        }
    }

    private static void finishClear(final ViewGroup rv, List<View> targets, final List<Integer> ids) {
        try {
            List<View> keep = new ArrayList<>();
            for (int i = 0; i < rv.getChildCount(); i++) {
                View c = rv.getChildAt(i);
                if (c.getClass().getName().endsWith("TaskView") && !targets.contains(c)
                        && c.getVisibility() == View.VISIBLE && !skipped.contains(c)) {
                    keep.add(c);
                }
            }
            GLIDE_OLD.clear();
            GLIDE.clear();
            glideFocus = null;
            float rvCenter = rv.getWidth() / 2f;
            float best = Float.MAX_VALUE;
            for (View c : keep) {
                float center = c.getLeft() + c.getWidth() / 2f + c.getTranslationX() - rv.getScrollX();
                GLIDE_OLD.put(c, center);
                if (Math.abs(center - rvCenter) < best) {
                    best = Math.abs(center - rvCenter);
                    glideFocus = c;
                }
            }

            for (View c : targets) {
                c.animate().cancel();
                try {
                    XposedHelpers.callMethod(c, "resetViewTransforms");
                } catch (Throwable ignored) {
                }
                c.setAlpha(1f);
                c.setTranslationX(0f);
                c.setTranslationY(0f);
                c.setScaleX(1f);
                c.setScaleY(1f);
                c.setRotationY(0f);
                if (LAYERED.remove(c)) c.setLayerType(View.LAYER_TYPE_NONE, null);
                STATE.remove(c);
                ANIM.remove(c);
                skipped.remove(c);
                if (c.getParent() == rv) rv.removeView(c);
            }
            boolean anyLeft = false;
            for (int i = 0; i < rv.getChildCount(); i++) {
                if (rv.getChildAt(i).getClass().getName().endsWith("TaskView")) anyLeft = true;
            }
            glidePending = anyLeft;
            rv.requestLayout();
            rv.invalidate();

            final ClassLoader cl = rv.getClass().getClassLoader();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Class<?> k = XposedHelpers.findClass(
                                "com.android.systemui.shared.system.ActivityManagerWrapper", cl);
                        Object amw = XposedHelpers.callStaticMethod(k, "getInstance");
                        for (Integer id : ids) {
                            try {
                                XposedHelpers.callMethod(amw, "removeTask", id);
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + "removeTask: " + t);
                            }
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "removeTasks: " + t);
                    }
                }
            }).start();
        } catch (Throwable t) {
            XposedBridge.log(TAG + "finishClear: " + t);
        } finally {
            clearing = false;
        }
    }

    /** First frame after the removal: settle the scroll and set up the glide of the kept cards. */
    private static void prepareGlide(ViewGroup rv) {
        glidePending = false;
        try {
            View focus = glideFocus;
            int idx = focus != null ? rv.indexOfChild(focus) : -1;
            try {
                if (idx >= 0) XposedHelpers.callMethod(rv, "snapToPageImmediately", idx);
                else XposedHelpers.callMethod(rv, "updateCurrentPageScroll");
            } catch (Throwable t) {
                XposedBridge.log(TAG + "snap: " + t);
            }
            if (style == Prefs.STYLE_ORIGINAL) {
                GLIDE_OLD.clear();
                return; // stock layout: no glide (the style code isn't active)
            }
            buildOrder(rv, true); // positions with the new layout, no glide yet
            long now = SystemClock.uptimeMillis();
            for (Map.Entry<View, Float> e : new ArrayList<>(GLIDE_OLD.entrySet())) {
                View c = e.getKey();
                if (c == null || c.getParent() != rv) continue;
                float nc = c.getLeft() + c.getWidth() / 2f + c.getTranslationX() - rv.getScrollX();
                float delta = e.getValue() - nc;
                if (Math.abs(delta) > 1f) GLIDE.put(c, new float[]{delta, now});
            }
            GLIDE_OLD.clear();
            buildOrder(rv, true); // apply the starting offset right away
            rv.postInvalidateOnAnimation();
        } catch (Throwable t) {
            XposedBridge.log(TAG + "prepareGlide: " + t);
        }
    }

    /**
     * Dismisses every unlocked card. The launcher can lose some of its dismiss animations when
     * many start at once, so we check afterwards and go again for whatever is left.
     */
    private static void clearUnprotected(final ViewGroup rv, final int attempt) {
        try {
            Set<String> prot = effectiveProtected();
            List<View> targets = new ArrayList<>();
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < rv.getChildCount(); i++) {
                View c = rv.getChildAt(i);
                if (!c.getClass().getName().endsWith("TaskView")) continue;
                Object[] info = taskInfo(c);
                if (info == null || info[0] == null) continue; // unknown: keep, to be safe
                if (prot.contains((String) info[0])) continue;
                targets.add(c);
                ids.add((Integer) info[1]);
            }
            if (targets.isEmpty() || attempt >= 6) {
                clearing = false;
                return;
            }
            for (int i = 0; i < targets.size(); i++) {
                boolean done = false;
                if (attempt < 3) {
                    try {
                        XposedHelpers.callMethod(rv, "dismissTask", targets.get(i), true, true);
                        done = true;
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "dismissTask: " + t);
                    }
                }
                if (!done) removeTaskViaAm(rv, ids.get(i));
            }
            rv.postDelayed(new Runnable() {
                @Override
                public void run() {
                    clearUnprotected(rv, attempt + 1);
                }
            }, attempt == 0 ? 560L : 450L);
        } catch (Throwable t) {
            clearing = false;
            XposedBridge.log(TAG + "clearUnprotected: " + t);
        }
    }

    private static void removeTaskViaAm(ViewGroup rv, int taskId) {
        try {
            Class<?> k = XposedHelpers.findClass(
                    "com.android.systemui.shared.system.ActivityManagerWrapper",
                    rv.getClass().getClassLoader());
            Object amw = XposedHelpers.callStaticMethod(k, "getInstance");
            XposedHelpers.callMethod(amw, "removeTask", taskId);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "removeTask: " + t);
        }
    }

    private static void sendClean(Context ctx, Collection<String> pkgs) {
        String tk = token;
        if (!rootOn || tk == null) return;
        try {
            Intent i = new Intent(Prefs.ACTION_CLEAN);
            i.setClassName(Prefs.PKG, Prefs.PKG + ".CleanReceiver");
            i.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            i.putExtra("token", tk);
            i.putExtra("pkgs", pkgs.toArray(new String[0]));
            ctx.sendBroadcast(i);
        } catch (Throwable t) {
            XposedBridge.log(TAG + "sendClean: " + t);
        }
    }

    // ---------------- Clear all button look ----------------
    private static void styleButton(View v) {
        if (!(v instanceof TextView)) return;
        Integer applied = BTN_VER.get(v);
        if (applied != null && applied == cfgVersion) return;
        TextView tv = (TextView) v;
        Object[] o = BTN_ORIG.get(v);
        if (o == null) {
            o = new Object[]{tv.getBackground(), tv.getText(), tv.getTextColors(),
                    tv.getTextSize(), tv.getTypeface(),
                    tv.getPaddingLeft(), tv.getPaddingTop(), tv.getPaddingRight(),
                    tv.getPaddingBottom(), tv.getElevation(),
                    tv.getMinWidth(), tv.getMaxWidth(), tv.getMinHeight(), tv.getMaxHeight(),
                    tv.getMinimumWidth(), tv.getMinimumHeight(), tv.getGravity()};
            BTN_ORIG.put(v, o);
        }
        float d = tv.getResources().getDisplayMetrics().density;

        // start from the original look every time, then layer our settings on top
        tv.setBackground((Drawable) o[0]);
        tv.setText((CharSequence) o[1]);
        tv.setTextColor((ColorStateList) o[2]);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, (Float) o[3]);
        tv.setTypeface((Typeface) o[4]);
        tv.setPadding((Integer) o[5], (Integer) o[6], (Integer) o[7], (Integer) o[8]);
        tv.setElevation((Float) o[9]);
        tv.setMinWidth((Integer) o[10]);
        tv.setMaxWidth((Integer) o[11]);
        tv.setMinHeight((Integer) o[12]);
        tv.setMaxHeight((Integer) o[13]);
        tv.setMinimumWidth((Integer) o[14]);
        tv.setMinimumHeight((Integer) o[15]);
        tv.setGravity((Integer) o[16]);

        if (btnOn) {
            GradientDrawable gd = new GradientDrawable();
            gd.setColor(btnBg);
            gd.setCornerRadius(btnRadius * d);
            if (btnStroke > 0) gd.setStroke(Math.round(btnStroke * d), btnStrokeColor);
            tv.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), gd, null));

            tv.setTextColor(btnTextColor);
            if (btnTextSize > 0) tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, btnTextSize);
            tv.setTypeface((Typeface) o[4], btnBold ? Typeface.BOLD : Typeface.NORMAL);
            if (btnText.length() > 0) tv.setText(btnText);

            if (btnPadX > 0) {
                int px = Math.round(btnPadX * d);
                tv.setPadding(px, (Integer) o[6], px, (Integer) o[8]);
            }
            if (btnW > 0) {
                int w = Math.round(btnW * d);
                tv.setMinimumWidth(w);
                tv.setWidth(w);
            }
            if (btnH > 0) {
                int hh = Math.round(btnH * d);
                tv.setMinimumHeight(hh);
                tv.setHeight(hh);
            }
            tv.setGravity(Gravity.CENTER);
            tv.setElevation(btnShadow * d);
        }
        BTN_VER.put(v, cfgVersion);
        tv.requestLayout();
        tv.invalidate();
    }

    // ---------------- helpers ----------------
    private static void hookChain(Class<?> clazz, String name, XC_MethodHook hook) {
        for (Class<?> c = clazz; c != null && c != ViewGroup.class && c != View.class
                && c != Object.class; c = c.getSuperclass()) {
            try {
                XposedBridge.hookAllMethods(c, name, hook);
            } catch (Throwable t) {
                XposedBridge.log(TAG + "hook failed " + c.getName() + "." + name + ": " + t);
            }
        }
    }

    private static boolean isRecentsView(Class<?> c) {
        for (; c != null; c = c.getSuperclass()) {
            if (c.getName().equals(RV)) return true;
        }
        return false;
    }

    private static View findButton(ViewGroup rv) {
        for (int i = 0; i < rv.getChildCount(); i++) {
            View child = rv.getChildAt(i);
            if (child.getClass().getName().endsWith("ClearAllButton")) return child;
        }
        return null;
    }

    private static void apply(Object thiz) {
        try {
            if (!isRecentsView(thiz.getClass())) return;
            refreshCfg();
            ViewGroup rv = (ViewGroup) thiz;
            ensurePreDraw(rv);
            View b = findButton(rv);
            if (b != null) place(rv, b);
        } catch (Throwable t) {
            XposedBridge.log(TAG + t);
        }
    }

    private static void place(ViewGroup rv, View button) {
        if (placing || sizing) return;
        styleButton(button);
        enforceSize(button);
        if (!pinClear) {
            if (pinnedApplied) {
                pinnedApplied = false;
                button.setTranslationY(0f);
            }
            return;
        }
        if (rv.getWidth() == 0 || rv.getHeight() == 0 || button.getHeight() == 0) return;
        placing = true;
        try {
            float density = rv.getResources().getDisplayMetrics().density;

            float ty = rv.getHeight() - clearBottomDp * density
                    - button.getHeight() - button.getTop();
            if (Math.abs(button.getTranslationY() - ty) > 0.5f) button.setTranslationY(ty);

            float tx = rv.getScrollX() + rv.getWidth() / 2f
                    - button.getLeft() - button.getWidth() / 2f;
            if (Math.abs(button.getTranslationX() - tx) > 0.5f) button.setTranslationX(tx);

            if (button.getAlpha() != 1f) button.setAlpha(1f);
            pinnedApplied = true;
        } finally {
            placing = false;
        }
    }
}
