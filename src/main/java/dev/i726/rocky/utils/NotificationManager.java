package dev.i726.rocky.utils;

import dev.i726.rocky.gui.GuiTheme;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Lightweight in-game toast notifications.
 * Call {@link #push(String, String, Type)} to show a toast.
 * Call {@link #render(GuiGraphicsExtractor, int)} from the HUD listener each frame.
 */
public final class NotificationManager {

    public enum Type { INFO, SUCCESS, ERROR, WARNING }

    private static final List<Toast> toasts = new ArrayList<>();
    private static final long DURATION_MS   = 2800;
    private static final long FADE_MS       = 300;
    private static final int  TOAST_W       = 190;
    private static final int  TOAST_H       = 36;
    private static final int  PADDING       = 6;
    private static final int  MARGIN        = 8;

    // ── Public API ─────────────────────────────────────────────────────────────

    public static void push(String title, String body, Type type) {
        synchronized (toasts) {
            toasts.add(new Toast(title, body, type, System.currentTimeMillis()));
        }
    }

    public static void info(String title, String body)    { push(title, body, Type.INFO);    }
    public static void success(String title, String body) { push(title, body, Type.SUCCESS); }
    public static void error(String title, String body)   { push(title, body, Type.ERROR);   }
    public static void warn(String title, String body)    { push(title, body, Type.WARNING); }

    /** Called from HUD every frame. {@code screenH} = scaled window height. */
    public static void render(GuiGraphicsExtractor ctx, int screenH) {
        synchronized (toasts) {
            long now = System.currentTimeMillis();
            Iterator<Toast> it = toasts.iterator();
            while (it.hasNext()) {
                if (now - it.next().createdAt > DURATION_MS + FADE_MS) it.remove();
            }
        }

        List<Toast> snapshot;
        synchronized (toasts) { snapshot = new ArrayList<>(toasts); }

        int y = screenH - MARGIN;
        long now = System.currentTimeMillis();

        // Render newest at bottom, stacking upward
        for (int i = snapshot.size() - 1; i >= 0; i--) {
            Toast t = snapshot.get(i);
            y -= TOAST_H + MARGIN;
            float alpha = computeAlpha(t, now);
            renderToast(ctx, t, MARGIN, y, alpha);
        }
    }

    // ── Internal ───────────────────────────────────────────────────────────────

    private static float computeAlpha(Toast t, long now) {
        long age = now - t.createdAt;
        if (age < FADE_MS) return (float) age / FADE_MS;
        if (age > DURATION_MS) return 1f - Math.min(1f, (float)(age - DURATION_MS) / FADE_MS);
        return 1f;
    }

    private static void renderToast(GuiGraphicsExtractor ctx, Toast t, int x, int y, float alpha) {
        int a = (int)(alpha * 255);
        if (a <= 0) return;

        Color typeColor = switch (t.type) {
            case SUCCESS -> new Color(34, 197, 94);
            case ERROR   -> new Color(239, 68, 68);
            case WARNING -> new Color(249, 115, 22);
            default      -> GuiTheme.accent();
        };

        // Shadow
        ctx.fill(x + 2, y + 2, x + TOAST_W + 2, y + TOAST_H + 2,
                GuiTheme.rgba(0, 0, 0, (int)(a * 0.35f)));

        // Border
        ctx.fill(x - 1, y - 1, x + TOAST_W + 1, y + TOAST_H + 1,
                withAlpha(GuiTheme.border(), a));

        // Background
        ctx.fill(x, y, x + TOAST_W, y + TOAST_H, withAlpha(GuiTheme.panelBg(), a));

        // Left accent bar
        ctx.fill(x, y, x + 3, y + TOAST_H,
                GuiTheme.rgba(typeColor.getRed(), typeColor.getGreen(), typeColor.getBlue(), a));

        // Top accent gradient
        ctx.fillGradient(x + 3, y, x + TOAST_W, y + 1,
                GuiTheme.rgba(typeColor.getRed(), typeColor.getGreen(), typeColor.getBlue(), (int)(a * 0.6f)),
                GuiTheme.rgba(typeColor.getRed(), typeColor.getGreen(), typeColor.getBlue(), 0));

        // Title
        int tx = x + PADDING + 3;
        TextRenderer.text(t.title, ctx, tx, y + PADDING,
                GuiTheme.rgba(typeColor.getRed(), typeColor.getGreen(), typeColor.getBlue(), a));

        // Body
        if (!t.body.isEmpty()) {
            TextRenderer.text(t.body, ctx, tx, y + PADDING + 13,
                    withAlpha(GuiTheme.textSecondary(), a));
        }
    }

    private static int withAlpha(int color, int a) {
        return (color & 0x00FFFFFF) | (a << 24);
    }

    // ── Toast record ───────────────────────────────────────────────────────────

    private record Toast(String title, String body, Type type, long createdAt) {}
}
