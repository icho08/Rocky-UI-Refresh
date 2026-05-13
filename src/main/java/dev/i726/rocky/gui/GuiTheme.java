package dev.i726.rocky.gui;

import java.awt.Color;

public final class GuiTheme {

    public enum ThemeColor {
        PURPLE, CYAN, BLUE, GREEN, PINK, RED, ORANGE, WHITE
    }

    private static ThemeColor currentTheme = ThemeColor.PURPLE;

    public static void setTheme(ThemeColor t) {
        currentTheme = t;
    }

    public static ThemeColor getTheme() {
        return currentTheme;
    }

    public static Color accent() {
        return switch (currentTheme) {
            case PURPLE -> new Color(139, 92, 246);
            case CYAN   -> new Color(34, 211, 238);
            case BLUE   -> new Color(59, 130, 246);
            case GREEN  -> new Color(34, 197, 94);
            case PINK   -> new Color(236, 72, 153);
            case RED    -> new Color(239, 68, 68);
            case ORANGE -> new Color(249, 115, 22);
            case WHITE  -> new Color(210, 208, 235);
        };
    }

    public static int panelBg()        { return rgba(13, 12, 20, 242); }
    public static int headerBg()       { return rgba(10, 9, 16, 252); }
    public static int border()         { return rgba(40, 36, 60, 150); }
    public static int hoverBg()        { return rgba(255, 255, 255, 10); }
    public static int settingBg()      { return rgba(9, 8, 14, 220); }
    public static int sliderTrack()    { return rgba(28, 26, 42, 255); }
    public static int separator()      { return rgba(255, 255, 255, 14); }

    public static int textPrimary()    { return rgba(228, 224, 255, 255); }
    public static int textSecondary()  { return rgba(112, 108, 148, 255); }
    public static int textAccent()     {
        Color a = accent();
        return rgba(a.getRed(), a.getGreen(), a.getBlue(), 255);
    }

    public static int toggleOff()      { return rgba(30, 28, 44, 255); }
    public static int toggleOn()       {
        Color a = accent();
        return rgba(a.getRed(), a.getGreen(), a.getBlue(), 190);
    }
    public static int toggleThumb()    { return rgba(222, 220, 245, 255); }

    public static int accentInt()      {
        Color a = accent();
        return rgba(a.getRed(), a.getGreen(), a.getBlue(), 255);
    }
    public static int accentDim()      {
        Color a = accent();
        return rgba(a.getRed(), a.getGreen(), a.getBlue(), 80);
    }
    public static int accentFaint()    {
        Color a = accent();
        return rgba(a.getRed(), a.getGreen(), a.getBlue(), 25);
    }

    public static int rgba(int r, int g, int b, int a) {
        return (a & 0xFF) << 24 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    public static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | ((alpha & 0xFF) << 24);
    }

    public static int lerpColor(int a, int b, float t) {
        int aa = (a >> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return rgba(
            (int)(ar + (br - ar) * t),
            (int)(ag + (bg - ag) * t),
            (int)(ab + (bb - ab) * t),
            (int)(aa + (ba - aa) * t)
        );
    }
}
