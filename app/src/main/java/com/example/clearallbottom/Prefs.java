package com.example.clearallbottom;

/** Setting keys and defaults, shared by the settings app and the Xposed hook. */
public final class Prefs {
    private Prefs() {}

    public static final String PKG = "com.example.clearallbottom";
    public static final String NAME = "settings";

    public static final int STYLE_ORIGINAL = 0;
    public static final int STYLE_STACK = 1;
    public static final int STYLE_COVERFLOW = 2;
    public static final int STYLE_IPHONE = 3;

    public static final String K_STYLE = "style";
    public static final String K_WIDTH = "width_pct";
    public static final String K_HEIGHT = "height_pct";
    public static final String K_RADIUS = "radius_dp";
    public static final String K_YOFF = "y_offset_dp";
    public static final String K_SPACING = "spacing_pct";
    public static final String K_SHRINK = "shrink_pct";
    public static final String K_FADE = "fade_pct";
    public static final String K_TILT = "tilt_deg";
    public static final String K_DEPTH = "depth";
    public static final String K_LIFT = "lift_dp";
    public static final String K_INVERT = "invert_tilt";
    public static final String K_PIN = "pin_clear_all";
    public static final String K_CLEAR_BOTTOM = "clear_bottom_dp";

    public static final int D_STYLE = STYLE_STACK;
    public static final int D_WIDTH = 100;
    public static final int D_HEIGHT = 100;
    public static final int D_RADIUS = 0;      // 0 = leave launcher's own corners
    public static final int D_YOFF = 0;
    public static final int D_SPACING = 32;
    public static final int D_SHRINK = 7;
    public static final int D_FADE = 0;
    public static final int D_TILT = 45;
    public static final int D_DEPTH = 3;
    public static final int D_LIFT = 10;
    public static final boolean D_INVERT = false;
    public static final boolean D_PIN = true;
    public static final int D_CLEAR_BOTTOM = 72;
}
