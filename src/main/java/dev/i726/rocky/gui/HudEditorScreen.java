package dev.i726.rocky.gui;

import dev.i726.rocky.module.modules.render.HUD;
import dev.i726.rocky.utils.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.awt.Color;

/**
 * HUD layout editor — opened by pressing the "HUD Editor Key" keybind.
 * Drag the labeled panel handles to reposition each HUD element.
 * Positions are saved to rocky/hud_positions.txt when the screen closes.
 */
public final class HudEditorScreen extends Screen {

    private int dragPanel = -1;
    private int dragOffsetX, dragOffsetY;

    public HudEditorScreen() {
        super(Text.literal("HUD Editor"));
    }

    /** Default X for panel id given current screen dimensions. */
    private int defaultX(int id) {
        return switch (id) {
            case HUD.P_INFO, HUD.P_COORDS, HUD.P_ARMOR, HUD.P_POTIONS -> 8;
            case HUD.P_MODULES -> width - HUD.APPROX_W[HUD.P_MODULES] - 4;
            default -> 8;
        };
    }

    /** Default Y for panel id given current screen dimensions. */
    private int defaultY(int id) {
        return switch (id) {
            case HUD.P_INFO    -> 8;
            case HUD.P_COORDS  -> 36;
            case HUD.P_ARMOR   -> height - HUD.APPROX_H[HUD.P_ARMOR] - 8;
            case HUD.P_POTIONS -> height - HUD.APPROX_H[HUD.P_ARMOR] - HUD.APPROX_H[HUD.P_POTIONS] - 12;
            case HUD.P_MODULES -> 8;
            default -> 8;
        };
    }

    private int panelX(int id) {
        return HUD.px(id, defaultX(id));
    }

    private int panelY(int id) {
        return HUD.py(id, defaultY(id));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // Dim background
        ctx.fill(0, 0, width, height, GuiTheme.rgba(0, 0, 0, 160));

        // Title + hint
        ctx.drawCenteredTextWithShadow(textRenderer,
                "HUD Layout Editor — drag panels to reposition", width / 2, 6,
                GuiTheme.textPrimary());
        ctx.drawCenteredTextWithShadow(textRenderer,
                "Press ESC to save & close", width / 2, 18, GuiTheme.textSecondary());

        // Reset all button
        int rbW = 100, rbH = 18;
        int rbX = width / 2 - rbW / 2, rbY = height - 28;
        boolean hoverReset = mouseX >= rbX && mouseX <= rbX + rbW && mouseY >= rbY && mouseY <= rbY + rbH;
        ctx.fill(rbX, rbY, rbX + rbW, rbY + rbH,
                hoverReset ? GuiTheme.rgba(220, 60, 60, 200) : GuiTheme.rgba(180, 40, 40, 180));
        ctx.drawCenteredTextWithShadow(textRenderer, "Reset All", rbX + rbW / 2, rbY + 5,
                0xFFFFFFFF);

        // Draw each panel handle
        for (int id = 0; id < HUD.NUM_PANELS; id++) {
            int px = (dragPanel == id) ? panelXDrag() : panelX(id);
            int py = (dragPanel == id) ? panelYDrag() : panelY(id);
            int pw = HUD.APPROX_W[id];
            int ph = HUD.APPROX_H[id];

            boolean hover = mouseX >= px && mouseX <= px + pw && mouseY >= py && mouseY <= py + ph;
            Color bg = hover || dragPanel == id
                    ? new Color(60, 55, 80, 220)
                    : new Color(30, 28, 44, 200);

            ctx.fill(px - 1, py - 1, px + pw + 1, py + ph + 1, GuiTheme.border());
            ctx.fill(px, py, px + pw, py + ph, GuiTheme.rgba(bg.getRed(), bg.getGreen(), bg.getBlue(), bg.getAlpha()));

            // Accent bar on left
            Color ac = GuiTheme.accent();
            ctx.fill(px, py, px + 3, py + ph, GuiTheme.accentInt());

            // Label
            String label = HUD.PANEL_NAMES[id];
            int lx = px + 9;
            int ly = py + ph / 2 - 4;
            TextRenderer.drawString(label, ctx, lx, ly, GuiTheme.textPrimary());

            // Drag icon hint
            String grip = "\u2261";
            ctx.drawText(textRenderer, grip, px + pw - 14, py + ph / 2 - 4,
                    GuiTheme.textSecondary(), false);

            // Position readout
            String pos = px + ", " + py;
            int posW = TextRenderer.getWidth(pos);
            ctx.drawText(textRenderer, pos, px + pw - posW - 18, py + ph / 2 - 4,
                    GuiTheme.textSecondary(), false);
        }

        // cursor
        if (dragPanel >= 0) {
            ctx.drawCenteredTextWithShadow(textRenderer, "Dragging: " + HUD.PANEL_NAMES[dragPanel],
                    width / 2, height - 48, GuiTheme.accentInt());
        }
    }

    // ── drag state tracking (raw mouse pos available through Screen API) ───

    private int lastMouseX, lastMouseY;

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX, my = (int) mouseY;

        // Check reset button
        int rbW = 100, rbH = 18;
        int rbX = width / 2 - rbW / 2, rbY = height - 28;
        if (mx >= rbX && mx <= rbX + rbW && my >= rbY && my <= rbY + rbH) {
            for (int i = 0; i < HUD.NUM_PANELS; i++) {
                HUD.storedX[i] = Float.NaN;
                HUD.storedY[i] = Float.NaN;
            }
            return true;
        }

        // Start drag on panel hit
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
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragPanel >= 0) {
            lastMouseX = (int) mouseX;
            lastMouseY = (int) mouseY;
            HUD.storedX[dragPanel] = lastMouseX - dragOffsetX;
            HUD.storedY[dragPanel] = lastMouseY - dragOffsetY;
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        dragPanel = -1;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void removed() {
        HUD.savePositions();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // Used while dragging to show current drag position
    private int panelXDrag() {
        return lastMouseX - dragOffsetX;
    }

    private int panelYDrag() {
        return lastMouseY - dragOffsetY;
    }
}
