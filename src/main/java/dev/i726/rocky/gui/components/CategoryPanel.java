package dev.i726.rocky.gui.components;

import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class CategoryPanel {

    public static final int WIDTH    = 140;
    public static final int HEADER_H = 18;

    private float x, y;
    private final String name;
    private final List<ModuleRow> rows = new ArrayList<>();

    private boolean dragging = false;
    private double dragOffsetX, dragOffsetY;
    private boolean collapsed = false;
    private float collapseAnim = 1f;

    public CategoryPanel(String name, List<Module> modules, float x, float y) {
        this.name = name;
        this.x    = x;
        this.y    = y;
        for (Module m : modules) rows.add(new ModuleRow(m));
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float collapseTarget = collapsed ? 0f : 1f;
        collapseAnim = RenderUtils.fast(collapseAnim, collapseTarget, 10f);

        int ix = (int) x, iy = (int) y;
        int contentH  = getContentHeight();
        int animatedH = (int) (contentH * collapseAnim);
        int totalH    = HEADER_H + animatedH;

        ctx.fill(ix + 2, iy + 2, ix + WIDTH + 2, iy + totalH + 2, GuiTheme.rgba(0, 0, 0, 55));
        ctx.fill(ix - 1, iy - 1, ix + WIDTH + 1, iy + totalH + 1, GuiTheme.border());
        ctx.fill(ix, iy, ix + WIDTH, iy + totalH, GuiTheme.panelBg());

        ctx.fill(ix, iy, ix + WIDTH, iy + HEADER_H, GuiTheme.headerBg());

        Color ac = GuiTheme.accent();
        ctx.fillGradient(ix, iy, ix + WIDTH, iy + HEADER_H,
                GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 40),
                GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 0));

        ctx.fill(ix, iy, ix + 3, iy + HEADER_H, GuiTheme.accentInt());

        String headerName = name.toUpperCase();
        ctx.drawText(MinecraftClient.getInstance().textRenderer,
                headerName, ix + 9, iy + 5, GuiTheme.textPrimary(), false);

        String arrow = collapsed ? "\u25B6" : "\u25BC";
        ctx.drawText(MinecraftClient.getInstance().textRenderer,
                arrow, ix + WIDTH - 12, iy + 5, GuiTheme.textSecondary(), false);

        if (animatedH > 0) {
            ctx.enableScissor(ix, iy + HEADER_H, ix + WIDTH, iy + HEADER_H + animatedH);

            int rowY = iy + HEADER_H;
            for (ModuleRow row : rows) {
                int rh = row.getHeight();
                row.render(ctx, ix, rowY, WIDTH, mouseX, mouseY, delta);
                rowY += rh;
                ctx.fill(ix + 4, rowY, ix + WIDTH - 4, rowY + 1, GuiTheme.separator());
            }

            ctx.disableScissor();
        }
    }

    private int getContentHeight() {
        int h = 0;
        for (ModuleRow row : rows) h += row.getHeight();
        return h;
    }

    public void clampToScreen(int screenW, int screenH) {
        x = Math.max(0, Math.min(screenW - WIDTH,    x));
        y = Math.max(0, Math.min(screenH - HEADER_H, y));
    }

    public boolean mouseClicked(double mx, double my, int button) {
        if (isOverHeader(mx, my)) {
            if (button == 0) {
                if (mx >= x + WIDTH - 18) {
                    collapsed = !collapsed;
                } else {
                    dragging = true;
                    dragOffsetX = mx - x;
                    dragOffsetY = my - y;
                }
                return true;
            } else if (button == 1) {
                collapsed = !collapsed;
                return true;
            }
        }

        if (!collapsed && collapseAnim > 0.1f) {
            int rowY = (int) y + HEADER_H;
            for (ModuleRow row : rows) {
                int rh = row.getHeight();
                if (my >= rowY && my < rowY + rh) {
                    if (row.mouseClicked(mx, my, button, (int) x, rowY, WIDTH)) return true;
                }
                rowY += rh;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (dragging) {
            x = (float) (mx - dragOffsetX);
            y = (float) (my - dragOffsetY);
            return true;
        }
        if (!collapsed) {
            int rowY = (int) y + HEADER_H;
            for (ModuleRow row : rows) {
                if (row.mouseDragged(mx, my, button, dx, dy, (int) x, rowY, WIDTH)) return true;
                rowY += row.getHeight();
            }
        }
        return false;
    }

    public void mouseReleased(double mx, double my, int button) {
        dragging = false;
        for (ModuleRow row : rows) row.mouseReleased(mx, my, button);
    }

    public boolean mouseScrolled(double mx, double my, double amount) {
        return false;
    }

    public boolean keyPressed(int key, int scan, int mods) {
        for (ModuleRow row : rows)
            if (row.keyPressed(key, scan, mods)) return true;
        return false;
    }

    public boolean charTyped(char chr, int mods) {
        for (ModuleRow row : rows)
            if (row.charTyped(chr, mods)) return true;
        return false;
    }

    private boolean isOverHeader(double mx, double my) {
        return mx >= x && mx < x + WIDTH && my >= y && my < y + HEADER_H;
    }
}
