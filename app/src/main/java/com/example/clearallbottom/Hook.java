package com.example.clearallbottom;

import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;

import java.lang.reflect.Method;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Pins the Quickstep Recents "Clear all" button to the bottom-center of RecentsView,
 * keeps it fully visible at all times, and draws it even when the launcher
 * would normally skip its (off-screen) page.
 */
public class Hook implements IXposedHookLoadPackage {

    // Distance (dp) between the button's bottom edge and the bottom of the Recents view.
    private static final int BOTTOM_OFFSET_DP = 96;

    private static final String TAG = "ClearAllBottom: ";
    private static final String RV = "com.android.quickstep.views.RecentsView";

    private static boolean placing = false;
    private static boolean drawing = false;

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

        // dispatchDraw: place before, and draw the button ourselves after
        // (PagedView only draws pages that are in the visible range).
        hookDraw(recentsView);

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

    private static void hookDraw(Class<?> recentsView) {
        for (Class<?> c = recentsView; c != null && c != ViewGroup.class; c = c.getSuperclass()) {
            try {
                Method m = c.getDeclaredMethod("dispatchDraw", Canvas.class);
                XposedBridge.hookMethod(m, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) {
                        if (isRecentsView(param.thisObject.getClass())) apply(param.thisObject);
                    }

                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!isRecentsView(param.thisObject.getClass()) || drawing) return;
                        drawing = true;
                        try {
                            ViewGroup rv = (ViewGroup) param.thisObject;
                            View b = findButton(rv);
                            if (b != null && b.getVisibility() == View.VISIBLE) {
                                Canvas canvas = (Canvas) param.args[0];
                                XposedHelpers.callMethod(rv, "drawChild", canvas, b,
                                        rv.getDrawingTime());
                            }
                        } catch (Throwable t) {
                            XposedBridge.log(TAG + "draw: " + t);
                        } finally {
                            drawing = false;
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

            // Bottom, a bit above the bottom edge.
            float ty = rv.getHeight() - BOTTOM_OFFSET_DP * density
                    - button.getHeight() - button.getTop();
            if (Math.abs(button.getTranslationY() - ty) > 0.5f) button.setTranslationY(ty);

            // Horizontally centered on screen, cancelling the RecentsView scroll.
            float tx = rv.getScrollX() + rv.getWidth() / 2f
                    - button.getLeft() - button.getWidth() / 2f;
            if (Math.abs(button.getTranslationX() - tx) > 0.5f) button.setTranslationX(tx);

            // Always visible.
            if (button.getAlpha() != 1f) button.setAlpha(1f);
        } finally {
            placing = false;
        }
    }
}
