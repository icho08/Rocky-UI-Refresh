package dev.i726.rocky.gui;

import dev.i726.rocky.module.modules.render.HUD;
import dev.i726.rocky.utils.TextRenderer;
import java.awt.Color;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * HUD layout editor — opened by pressing the "HUD Editor Key" keybind (default: Numpad 0).
 * Drag the labeled panel handles to reposition each HUD element.
 * Positions are saved to rocky/hud_positions.txt when the screen closes (ESC).
 */
public final class HudEditorScreen extends Screen {

    private int    dragPanel   = -1;
    private double dragOffsetX, dragOffsetY;
    private double lastMouseX, lastMouseY;

    public HudEditorScreen() {
        super(Component.literal("HUD Editor"));
    }

    // ── Default positions (computed from current screen size) ─────────────

    private int defaultX(int id) {
        return switch (id) {
            case HUD.P_MODULES -> width - HUD.APPROX_W[HUD.P_MODULES] - 4;
            default            -> 8;
        };
    }

    private int defaultY(int id) {
        return switch (id) {
            case HUD.P_INFO    -> 8;
            case HUD.P_COORDS  -> 36;
            case HUD.P_ARMOR   -> height - HUD.APPROX_H[HUD.P_ARMOR]   - 8;
            case HUD.P_POTIONS -> height - HUD.APPROX_H[HUD.P_ARMOR]
                                         - HUD.APPROX_H[HUD.P_POTIONS] - 12;
            case HUD.P_MODULES -> 8;
            default            -> 8;
        };
    }

    private int panelX(int id) { return HUD.px(id, defaultX(id)); }
    private int panelY(int id) { return HUD.py(id, defaultY(id)); }

    // ── Render ────────────────────────────────────────────────────────────

    @Override
    public void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, GuiTheme.rgba(0, 0, 0, 160));

        ctx.centeredText(font,
                "HUD Layout Editor  —  drag panels to reposition",
                width / 2, 6, GuiTheme.textPrimary());
        ctx.centeredText(font,
                "ESC to save & close", width / 2, 18, GuiTheme.textSecondary());

        // Reset-all button
        int rbW = 100, rbH = 18;
        int rbX = width / 2 - rbW / 2, rbY = height - 28;
        boolean hoverReset = mouseX >= rbX && mouseX <= rbX + rbW
                          && mouseY >= rbY && mouseY <= rbY + rbH;
        ctx.fill(rbX, rbY, rbX + rbW, rbY + rbH,
                hoverReset ? GuiTheme.rgba(220, 60, 60, 220) : GuiTheme.rgba(160, 40, 40, 180));
        ctx.centeredText(font, "Reset All",
                rbX + rbW / 2, rbY + 5, 0xFFFFFFFF);

        // Panel handles
        for (int id = 0; id < HUD.NUM_PANELS; id++) {
            int px = (dragPanel == id) ? (int)(lastMouseX - dragOffsetX) : panelX(id);
            int py = (dragPanel == id) ? (int)(lastMouseY - dragOffsetY) : panelY(id);
            int pw = HUD.APPROX_W[id];
            int ph = HUD.APPROX_H[id];

            boolean hover = mouseX >= px && mouseX <= px + pw
                         && mouseY >= py && mouseY <= py + ph;

            Color bg = (hover || dragPanel == id)
                    ? new Color(60, 55, 80, 220) : new Color(30, 28, 44, 200);

            ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, GuiTheme.border());
            ctx.fill(px, py, px + pw, py + ph,
                    GuiTheme.rgba(bg.getRed(), bg.getGreen(), bg.getBlue(), bg.getAlpha()));
            ctx.fill(px, py, px + 3, py + ph, GuiTheme.accentInt());

            String label = HUD.PANEL_NAMES[id];
            TextRenderer.text(label, ctx, px + 9, py + ph / 2 - 4, GuiTheme.textPrimary());

            String pos = px + ", " + py;
            int posW = TextRenderer.getWidth(pos);
            ctx.text(font, pos,
                    px + pw - posW - 6, py + ph / 2 - 4,
                    GuiTheme.textSecondary(), false);
        }

        if (dragPanel >= 0) {
            ctx.centeredText(font,
                    "Dragging: " + HUD.PANEL_NAMES[dragPanel],
                    width / 2, height - 48, GuiTheme.accentInt());
        }
    }

    // ── Mouse events — MC 1.21 Click API ─────────────────────────────────

    @Override
    public boolean mouseClicked(MouseButtonEvent click, boolean canDoubleClick) {
        double mx = click.x(), my = click.y();

        // Reset-all button
        int rbW = 100, rbH = 18;
        int rbX = width / 2 - rbW / 2, rbY = height - 28;
        if (mx >= rbX && mx <= rbX + rbW && my >= rbY && my <= rbY + rbH) {
            for (int i = 0; i < HUD.NUM_PANELS; i++) {
                HUD.storedX[i] = Float.NaN;
                HUD.storedY[i] = Float.NaN;
            }
            return true;
        }

        // Start drag
        for (int id = 0; id < HUD.NUM_PANELS; id++) {
            int px = panelX(id), py = panelY(id);
            int pw = HUD.APPROX_W[id], ph = HUD.APPROX_H[id];
            if (mx >= px && mx <= px + pw && my >= py && my <= py + ph) {
                dragPanel    = id;
                dragOffsetX  = mx - px;
                dragOffsetY  = my - py;
                lastMouseX   = mx;
                lastMouseY   = my;
                return true;
            }
        }
        return super.mouseClicked(click, canDoubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent click, double deltaX, double deltaY) {
        if (dragPanel >= 0) {
            lastMouseX = click.x();
            lastMouseY = click.y();
            HUD.storedX[dragPanel] = (float)(lastMouseX - dragOffsetX);
            HUD.storedY[dragPanel] = (float)(lastMouseY - dragOffsetY);
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent click) {
        dragPanel = -1;
        return super.mouseReleased(click);
    }

    @Override
    public void removed() {
        HUD.savePositions();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
