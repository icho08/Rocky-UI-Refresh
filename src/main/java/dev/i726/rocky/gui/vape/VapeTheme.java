package dev.i726.rocky.gui.vape;

import java.awt.Color;

public final class VapeTheme {
    // Base surfaces — deep blacks matching the web UI
    public static final Color BACKGROUND      = new Color(5, 5, 5, 200);
    public static final Color PANEL_BG        = new Color(10, 10, 10, 235);
    public static final Color PANEL_HEADER    = new Color(8, 8, 8, 245);
    public static final Color MODULE_BG       = new Color(15, 15, 15, 210);
    public static final Color MODULE_ENABLED  = new Color(10, 30, 32, 215);
    public static final Color SETTING_BG      = new Color(11, 11, 11, 200);

    // Accent — electric cyan  rgb(34, 211, 238)
    public static final Color ACCENT          = new Color(34, 211, 238);
    public static final Color ACCENT_DIM      = new Color(34, 211, 238, 110);
    public static final Color ACCENT_GLOW     = new Color(34, 211, 238, 28);
    public static final Color ACCENT_FILL     = new Color(34, 211, 238, 16);

    // Text
    public static final Color TEXT            = new Color(255, 255, 255);
    public static final Color TEXT_DIM        = new Color(175, 175, 175);
    public static final Color TEXT_MUTED      = new Color(90, 90, 90);

    // Chrome
    public static final Color BORDER          = new Color(255, 255, 255, 10);
    public static final Color SEPARATOR       = new Color(255, 255, 255, 6);
    public static final Color HOVER_OVERLAY   = new Color(255, 255, 255, 8);

    // Switch / toggle
    public static final Color SWITCH_OFF      = new Color(38, 38, 42, 230);
    public static final Color SWITCH_ON       = new Color(34, 211, 238, 90);
}
