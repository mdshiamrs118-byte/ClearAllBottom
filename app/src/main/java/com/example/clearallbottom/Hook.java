package com.example.clearallbottom;

import android.view.View;
import android.view.ViewGroup;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

/**
 * Moves the Quickstep Recents "Clear all" button to the bottom of RecentsView.
 */
public class Hook implements IXposedHookLoadPackage {

    // Distance (dp) between the button's bottom edge and the bottom of the Recents view.
    // Increase this if the button overlaps the Screenshot/Select actions row.
    private static final int BOTTOM_OFFSET_DP = 96;

    private static final String TAG = "ClearAllBottom: ";

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) {
        final Class<?> recentsView = XposedHelpers.findClassIfExists(
                "com.android.quickstep.views.RecentsView", lpparam.classLoader);
        if (recentsView == null) {
            return; // not a Quickstep launcher process
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

        // Re-apply on layout, scroll and draw so the framework can't reset it.
        hookChain(recentsView, "onLayout", fix);
        hookChain(recentsView, "dispatchDraw", fix);
        hookChain(recentsView, "onPageScroll", fix);
        hookChain(recentsView, "updateClearAllButtonAlpha", fix);

        // Also hook the button itself if the class exists.
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
            hookChain(clearAll, "setTranslationX", buttonFix);
            hookChain(clearAll, "setTranslationY", buttonFix);
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

    private static void apply(Object thiz) {
        try {
            if (!thiz.getClass().getName().contains("RecentsView")
                    && !isRecentsView(thiz.getClass())) {
                return; // PagedView hooks also fire for Workspace etc.
            }
            ViewGroup rv = (ViewGroup) thiz;
            for (int i = 0; i < rv.getChildCount(); i++) {
                View child = rv.getChildAt(i);
                if (child.getClass().getName().endsWith("ClearAllButton")) {
                    place(rv, child);
                    return;
                }
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + t);
        }
    }

    private static boolean isRecentsView(Class<?> c) {
        for (; c != null; c = c.getSuperclass()) {
            if (c.getName().equals("com.android.quickstep.views.RecentsView")) return true;
        }
        return false;
    }

    private static boolean placing = false;

    private static void place(ViewGroup rv, View button) {
        if (placing || rv.getHeight() == 0 || button.getHeight() == 0) return;
        placing = true;
        try {
            float density = rv.getResources().getDisplayMetrics().density;
            float target = rv.getHeight() - BOTTOM_OFFSET_DP * density
                    - button.getHeight() - button.getTop();
            if (Math.abs(button.getTranslationY() - target) > 0.5f) {
                button.setTranslationY(target);
            }
        } finally {
            placing = false;
        }
    }
}
