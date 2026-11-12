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
    public static final String K_FLAT = "flat_tilt";
    public static final String K_TAP_HOME = "tap_home";
    public static final String K_BTN_ON = "btn_custom";
    public static final String K_BTN_TEXT = "btn_text";
    public static final String K_BTN_TEXT_COLOR = "btn_text_color";
    public static final String K_BTN_TEXT_SIZE = "btn_text_size";
    public static final String K_BTN_BOLD = "btn_bold";
    public static final String K_BTN_BG = "btn_bg";
    public static final String K_BTN_RADIUS = "btn_radius";
    public static final String K_BTN_W = "btn_w";
    public static final String K_BTN_H = "btn_h";
    public static final String K_BTN_PADX = "btn_padx";
    public static final String K_BTN_STROKE = "btn_stroke";
    public static final String K_BTN_STROKE_COLOR = "btn_stroke_color";
    public static final String K_BTN_SHADOW = "btn_shadow";
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
    public static final boolean D_FLAT = false;
    public static final boolean D_TAP_HOME = true;
    public static final boolean D_BTN_ON = true;
    public static final String D_BTN_TEXT = "";            // empty = launcher's own label
    public static final int D_BTN_TEXT_COLOR = 0xFFFFFFFF;
    public static final int D_BTN_TEXT_SIZE = 14;          // sp, 0 = stock
    public static final boolean D_BTN_BOLD = true;
    public static final int D_BTN_BG = 0xCC202020;
    public static final int D_BTN_RADIUS = 24;             // dp
    public static final int D_BTN_W = 0;                   // dp, 0 = auto
    public static final int D_BTN_H = 44;                  // dp, 0 = auto
    public static final int D_BTN_PADX = 24;               // dp, 0 = stock
    public static final int D_BTN_STROKE = 0;              // dp
    public static final int D_BTN_STROKE_COLOR = 0x66FFFFFF;
    public static final int D_BTN_SHADOW = 4;              // dp
    public static final boolean D_PIN = true;
    public static final int D_CLEAR_BOTTOM = 72;
}
