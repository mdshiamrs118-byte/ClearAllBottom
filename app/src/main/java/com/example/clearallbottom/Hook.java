package com.example.clearallbottom;

import android.graphics.Canvas;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Quickstep Recents tweaks:
 *  - "Clear all" pinned to bottom-center, always visible, tappable.
 *  - Stacked-cards look: the centered card is in front, neighbours shrink and
 *    tuck behind it. Swiping is still left/right.
 */
public class Hook implements IXposedHookLoadPackage {

    // ---------- tuning ----------
    private static final boolean STACK_EFFECT = true;
    /** Distance (dp) between Clear all's bottom edge and the bottom of Recents. */
    private static final int BOTTOM_OFFSET_DP = 72;
    /** How much each step away from the center shrinks a card. */
    private static final float SCALE_STEP = 0.07f;
    private static final float MIN_SCALE = 0.70f;
    /** Fraction of the normal card spacing kept between stacked cards (smaller = tighter pile). */
    private static final float SPACING = 0.32f;
    /** Cards further back are lifted this many dp per step (max 3 steps). */
    private static final float LIFT_DP = 10f;
    /** Cards further than this many steps from the center aren't drawn. */
    private static final float MAX_DEPTH = 3.6f;
    // ----------------------------

    private static final String TAG = "ClearAllBottom: ";
    private static final String RV = "com.android.quickstep.views.RecentsView";

    private static boolean placing = false;
    private static boolean tracking = false;
    private static Method drawChildMethod;

    /** Per-card record of what we last wrote, so we build on top of the launcher's own values. */
    private static final WeakHashMap<View, float[]> STATE = new WeakHashMap<>();

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
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
        hookTouchOrder();
        hookTouch();

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

    // ------------------------------------------------------------------ drawing

    /** Replaces RecentsView's child drawing so cards are painted back-to-front by depth. */
    private static void hookDraw(Class<?> recentsView) {
        for (Class<?> c = recentsView; c != null && c != ViewGroup.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod("dispatchDraw", Canvas.class);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (!isRecentsView(param.thisObject.getClass())) return;
                        try {
                            ViewGroup rv = (ViewGroup) param.thisObject;
                            apply(rv);
                            List<View> order = buildOrder(rv, true);
                            Canvas canvas = (Canvas) param.args[0];
                            long time = rv.getDrawingTime();
                            Method dc = getDrawChild();
                            for (View v : order) {
                                if (v.getVisibility() != View.VISIBLE) continue;
                                if (skipped.contains(v)) continue;
                                dc.invoke(rv, canvas, v, time);
                            }
                            param.setResult(null); // we drew everything
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + "draw: " + t); // fall back to original draw
                        }
                    }
                });
                XposedBridge.log(TAG + "draw hooked in " + c.getName());
                return; // most-derived declaration only
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

    /** Cards we decided not to draw this frame (too deep in the stack). */
    private static final java.util.Set<View> skipped =
            java.util.Collections.newSetFromMap(new WeakHashMap<View, Boolean>());

    /**
     * Returns ALL children ordered back-to-front (last = topmost).
     * When applyTransforms is true, also applies the stack transforms to the cards.
     */
    private static List<View> buildOrder(ViewGroup rv, boolean applyTransforms) {
        final int n = rv.getChildCount();
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

        final java.util.IdentityHashMap<View, Float> depth = new java.util.IdentityHashMap<>();
        if (STACK_EFFECT && !cards.isEmpty()) {
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

            for (View c : cards) {
                float layoutCenter = c.getLeft() + c.getWidth() / 2f;
                float t = (layoutCenter - rv.getScrollX() - rvCenter) / step;
                float tt = Math.abs(t);
                depth.put(c, tt);
                if (!applyTransforms) continue;

                if (tt > MAX_DEPTH) {
                    skipped.add(c);
                    continue;
                }
                skipped.remove(c);

                float scale = Math.max(MIN_SCALE, 1f - SCALE_STEP * tt);
                float eff = tt <= 1f ? tt : 1f + 0.35f * (tt - 1f);
                float visualOffset = Math.signum(t) * step * SPACING * eff;
                float dx = visualOffset - t * step;
                float dy = -LIFT_DP * density * Math.min(tt, 3f);
                applyOnTopOfLauncher(c, scale, dx, dy);
            }
        }

        // Back-to-front: deepest first, centered card last.
        Collections.sort(cards, new Comparator<View>() {
            @Override
            public int compare(View a, View b) {
                Float da = depth.get(a), db = depth.get(b);
                float fa = da == null ? 0f : da, fb = db == null ? 0f : db;
                int c = Float.compare(fb, fa);
                return c != 0 ? c : Integer.compare(rv_indexOf(b), rv_indexOf(a));
            }

            private int rv_indexOf(View v) {
                ViewGroup p = (ViewGroup) v.getParent();
                return p == null ? 0 : p.indexOfChild(v);
            }
        });

        List<View> order = new ArrayList<>(n);
        order.addAll(others);
        order.addAll(cards);
        if (clearAll != null) order.add(clearAll);
        return order;
    }

    /**
     * Sets scale/translation as (launcher's own value) combined with our effect,
     * remembering what we wrote so we can tell when the launcher changes it again.
     */
    private static void applyOnTopOfLauncher(View v, float scale, float dx, float dy) {
        float[] st = STATE.get(v);
        if (st == null) {
            st = new float[8];
            for (int i = 0; i < st.length; i++) st[i] = Float.NaN;
            STATE.put(v, st);
        }
        // st: [lastSX, baseSX, lastSY, baseSY, lastTX, baseTX, lastTY, baseTY]
        float baseSX = (v.getScaleX() == st[0]) ? st[1] : v.getScaleX();
        float baseSY = (v.getScaleY() == st[2]) ? st[3] : v.getScaleY();
        float baseTX = (v.getTranslationX() == st[4]) ? st[5] : v.getTranslationX();
        float baseTY = (v.getTranslationY() == st[6]) ? st[7] : v.getTranslationY();

        v.setScaleX(baseSX * scale);
        v.setScaleY(baseSY * scale);
        v.setTranslationX(baseTX + dx);
        v.setTranslationY(baseTY + dy);

        st[0] = v.getScaleX();  st[1] = baseSX;
        st[2] = v.getScaleY();  st[3] = baseSY;
        st[4] = v.getTranslationX(); st[5] = baseTX;
        st[6] = v.getTranslationY(); st[7] = baseTY;
    }

    // ------------------------------------------------------------------ touch

    /** Makes touch hit-testing use the same front-to-back order as drawing. */
    private static void hookTouchOrder() {
        try {
            XposedHelpers.findAndHookMethod(ViewGroup.class, "buildTouchDispatchChildList",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                if (!STACK_EFFECT || !isRecentsView(param.thisObject.getClass())) return;
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

    /**
     * The Clear all button is drawn away from its layout position, so route taps on its
     * visible area to it ourselves.
     */
    private static void hookTouch() {
        try {
            XposedHelpers.findAndHookMethod(ViewGroup.class, "dispatchTouchEvent",
                    MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    try {
                        if (!isRecentsView(param.thisObject.getClass())) return;
                        ViewGroup rv = (ViewGroup) param.thisObject;
                        View b = findButton(rv);
                        if (b == null || b.getVisibility() != View.VISIBLE) return;
                        MotionEvent ev = (MotionEvent) param.args[0];
                        int action = ev.getActionMasked();

                        float pad = 8 * rv.getResources().getDisplayMetrics().density;
                        float l = b.getX() - rv.getScrollX() - pad;
                        float t = b.getY() - pad;
                        float r = l + b.getWidth() + 2 * pad;
                        float bt = t + b.getHeight() + 2 * pad;
                        boolean inside = ev.getX() >= l && ev.getX() <= r
                                && ev.getY() >= t && ev.getY() <= bt;

                        if (action == MotionEvent.ACTION_DOWN) {
                            tracking = inside;
                            if (tracking) {
                                b.setPressed(true);
                                param.setResult(true);
                            }
                        } else if (tracking) {
                            if (action == MotionEvent.ACTION_UP) {
                                tracking = false;
                                b.setPressed(false);
                                if (inside) b.performClick();
                            } else if (action == MotionEvent.ACTION_CANCEL) {
                                tracking = false;
                                b.setPressed(false);
                            }
                            param.setResult(true);
                        }
                    } catch (Throwable t) {
                        XposedBridge.log(TAG + "touch: " + t);
                    }
                }
            });
            XposedBridge.log(TAG + "touch hooked");
        } catch (Throwable t) {
            XposedBridge.log(TAG + "hookTouch: " + t);
        }
    }

    // ------------------------------------------------------------------ helpers

    /** Hooks every method called `name` declared in clazz and its superclasses up to ViewGroup. */
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
            ViewGroup rv = (ViewGroup) thiz;
            View b = findButton(rv);
            if (b != null) place(rv, b);
        } catch (Throwable t) {
            XposedBridge.log(TAG + t);
        }
    }

    private static void place(ViewGroup rv, View button) {
        if (placing || rv.getWidth() == 0 || rv.getHeight() == 0 || button.getHeight() == 0) return;
        placing = true;
        try {
            float density = rv.getResources().getDisplayMetrics().density;

            float ty = rv.getHeight() - BOTTOM_OFFSET_DP * density
                    - button.getHeight() - button.getTop();
            if (Math.abs(button.getTranslationY() - ty) > 0.5f) button.setTranslationY(ty);

            float tx = rv.getScrollX() + rv.getWidth() / 2f
                    - button.getLeft() - button.getWidth() / 2f;
            if (Math.abs(button.getTranslationX() - tx) > 0.5f) button.setTranslationX(tx);

            if (button.getAlpha() != 1f) button.setAlpha(1f);
        } finally {
            placing = false;
        }
    }
}
