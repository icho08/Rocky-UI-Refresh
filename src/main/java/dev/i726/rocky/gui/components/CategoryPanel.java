package dev.i726.rocky.gui.components;

import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CategoryPanel {

    public static final int WIDTH    = 140;
    public static final int HEADER_H = 18;

    private static final int SCROLL_SPEED = 10;
    private static final int SCROLLBAR_W  = 3;

    private float x, y;
    private final String name;
    private final List<ModuleRow> rows = new ArrayList<>();

    private boolean dragging = false;
    private double dragOffsetX, dragOffsetY;
    private boolean collapsed = false;
    private float collapseAnim = 1f;

    private float scrollOffset     = 0f;
    private float scrollOffsetAnim = 0f;
    private int   maxScroll        = 0;

    private String filterQuery = "";

    public CategoryPanel(String name, List<Module> modules, float x, float y) {
        this.name = name;
        this.x    = x;
        this.y    = y;
        for (Module m : modules) rows.add(new ModuleRow(m));
    }

    public void setFilter(String query) {
        this.filterQuery   = query.toLowerCase();
        this.scrollOffset  = 0f;
        this.scrollOffsetAnim = 0f;
    }

    private List<ModuleRow> getVisibleRows() {
        if (filterQuery.isEmpty()) return rows;
        return rows.stream()
                .filter(r -> r.getModuleName().toLowerCase().contains(filterQuery))
                .collect(Collectors.toList());
    }

    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        float collapseTarget = collapsed ? 0f : 1f;
        collapseAnim = RenderUtils.fast(collapseAnim, collapseTarget, 10f);

        List<ModuleRow> visible  = getVisibleRows();
        int screenH   = MinecraftClient.getInstance().getWindow().getScaledHeight();
        int ix        = (int) x;
        int iy        = (int) y;
        int contentH  = getContentHeight(visible);
        int maxVisible = Math.max(0, screenH - iy - HEADER_H - 6);
        int clampedH  = Math.min(contentH, maxVisible);
        int animatedH = (int) (clampedH * collapseAnim);
        int totalH    = HEADER_H + animatedH;

        maxScroll = Math.max(0, contentH - maxVisible);
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
        scrollOffsetAnim = RenderUtils.fast(scrollOffsetAnim, scrollOffset, 14f);

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
            int bodyTop = iy + HEADER_H;
            int bodyBot = bodyTop + animatedH;

            ctx.enableScissor(ix, bodyTop, ix + WIDTH, bodyBot);

            int rowY = bodyTop - (int) scrollOffsetAnim;
            for (ModuleRow row : visible) {
                int rh = row.getHeight();
                row.render(ctx, ix, rowY, WIDTH, mouseX, mouseY, delta);
                rowY += rh;
                ctx.fill(ix + 4, rowY, ix + WIDTH - 4, rowY + 1, GuiTheme.separator());
            }

            ctx.disableScissor();

            if (maxScroll > 0) {
                float trackH  = animatedH;
                float thumbH  = Math.max(16, trackH * ((float) maxVisible / contentH));
                float thumbY  = bodyTop + (trackH - thumbH) * (scrollOffsetAnim / maxScroll);
                int   sbX     = ix + WIDTH - SCROLLBAR_W;
                ctx.fill(sbX, bodyTop, sbX + SCROLLBAR_W, bodyBot, GuiTheme.rgba(0, 0, 0, 60));
                ctx.fill(sbX, (int) thumbY, sbX + SCROLLBAR_W, (int) (thumbY + thumbH),
                        GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 160));
            }
        }
    }

    private int getContentHeight(List<ModuleRow> visible) {
        int h = 0;
        for (ModuleRow row : visible) h += row.getHeight();
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
                    if (!collapsed) scrollOffset = 0;
                } else {
                    dragging = true;
                    dragOffsetX = mx - x;
                    dragOffsetY = my - y;
                }
                return true;
            } else if (button == 1) {
                collapsed = !collapsed;
                if (!collapsed) scrollOffset = 0;
                return true;
            }
        }

        if (!collapsed && collapseAnim > 0.1f && isOverBody(mx, my)) {
            double adjustedMy = my + scrollOffsetAnim;
            int rowY = (int) y + HEADER_H;
            for (ModuleRow row : getVisibleRows()) {
                int rh = row.getHeight();
                int visualRowY = rowY - (int) scrollOffsetAnim;
                if (my >= visualRowY && my < visualRowY + rh) {
                    if (row.mouseClicked(mx, adjustedMy, button, (int) x, rowY, WIDTH)) return true;
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
            double adjustedMy = my + scrollOffsetAnim;
            int rowY = (int) y + HEADER_H;
            for (ModuleRow row : getVisibleRows()) {
                if (row.mouseDragged(mx, adjustedMy, button, dx, dy, (int) x, rowY, WIDTH)) return true;
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
        if (!collapsed && isOverBody(mx, my)) {
            scrollOffset -= (float) (amount * SCROLL_SPEED);
            scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
            return true;
        }
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

    public float getX()    { return x; }
    public float getY()    { return y; }
    public String getName() { return name; }

    private boolean isOverHeader(double mx, double my) {
        return mx >= x && mx < x + WIDTH && my >= y && my < y + HEADER_H;
    }

    private boolean isOverBody(double mx, double my) {
        List<ModuleRow> visible = getVisibleRows();
        int screenH     = MinecraftClient.getInstance().getWindow().getScaledHeight();
        int contentH    = getContentHeight(visible);
        int maxVisible  = Math.max(0, screenH - (int) y - HEADER_H - 6);
        int visibleH    = (int) (Math.min(contentH, maxVisible) * collapseAnim);
        return mx >= x && mx < x + WIDTH && my >= y + HEADER_H && my < y + HEADER_H + visibleH;
    }
}
