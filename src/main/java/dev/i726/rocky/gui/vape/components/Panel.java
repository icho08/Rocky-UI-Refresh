package dev.i726.rocky.gui.vape.components;

import dev.i726.rocky.gui.vape.VapeTheme;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.Rocky;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Panel extends Component {

    public  static final int   HEADER_H = 26;
    private static final float RADIUS   = 5f;

    private final Category category;
    private final List<ModuleButton> buttons = new ArrayList<>();
    private boolean dragging;
    private double dragX, dragY;
    public boolean expanded = true;
    private final String searchFilter;

    public Panel(Category category, double x, double y, double width, String searchFilter) {
        super(x, y, width, HEADER_H);
        this.category     = category;
        this.searchFilter = searchFilter;

        List<Module> modules = Rocky.INSTANCE.getModuleManager().getModulesInCategory(category);
        double buttonY = y + HEADER_H;
        for (Module module : modules) {
            if (matchesSearch(module)) {
                // Pass dummy height — ModuleButton always uses CARD_H
                buttons.add(new ModuleButton(module, x, buttonY, width, ModuleButton.CARD_H));
                buttonY += ModuleButton.CARD_H;
            }
        }
    }

    private boolean matchesSearch(Module module) {
        if (searchFilter == null || searchFilter.isEmpty()) return true;
        return module.getName().toString().toLowerCase().contains(searchFilter)
            || module.getDescription().toString().toLowerCase().contains(searchFilter);
    }

    public Category getCategory() { return category; }
    public double   getX()        { return x; }
    public double   getY()        { return y; }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (dragging) {
            x = mouseX - dragX;
            y = mouseY - dragY;
            Rocky.INSTANCE.getProfileManager().setPanelPosition(category.getName(), x, y);
        }
        updateButtonPositions();

        double totalH = getTotalHeight();

        // ── Drop shadow ────────────────────────────────────────────────────
        RenderUtils.renderRoundedQuad(context, new Color(0, 0, 0, 90),
                x + 4, y + 5, x + width + 4, y + totalH + 5, RADIUS, 8);

        // ── Panel body ─────────────────────────────────────────────────────
        RenderUtils.renderRoundedQuad(context, new Color(10, 10, 10, 240),
                x, y, x + width, y + totalH, RADIUS, 12);

        // Outer border — thin white rim
        RenderUtils.renderRoundedOutline(context, new Color(255, 255, 255, 12),
                x, y, x + width, y + totalH, RADIUS, RADIUS, RADIUS, RADIUS, 0.5, 12);

        // ── Header — gradient ─────────────────────────────────────────────
        // Top-rounded corners
        RenderUtils.renderRoundedQuad(context, new Color(18, 18, 18, 252),
                x, y, x + width, y + HEADER_H,
                RADIUS, RADIUS, expanded ? 0 : RADIUS, expanded ? 0 : RADIUS, 12);
        // Gradient overlay (top lighter, bottom darker)
        context.fillGradient(
                (int)(x + RADIUS), (int)y, (int)(x + width - RADIUS), (int)(y + HEADER_H),
                new Color(28, 28, 28, 0).getRGB(),
                new Color(0, 0, 0, 60).getRGB());

        // ── Cyan accent bar — bottom of header ────────────────────────────
        int ay = (int)(y + HEADER_H - 1);
        context.fillGradient((int)x, ay, (int)(x + width / 2), ay + 1,
                new Color(34, 211, 238, 60).getRGB(), VapeTheme.ACCENT.getRGB());
        context.fillGradient((int)(x + width / 2), ay, (int)(x + width), ay + 1,
                VapeTheme.ACCENT.getRGB(), new Color(34, 211, 238, 60).getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();

        // ── Category name — centered, all-caps ────────────────────────────
        String title = category.getName().toUpperCase();
        int tw = mc.textRenderer.getWidth(title);
        context.drawText(mc.textRenderer, title,
                (int)(x + (width - tw) / 2), (int)(y + (HEADER_H - 8) / 2),
                VapeTheme.TEXT.getRGB(), false);

        // ── Collapse chevron ───────────────────────────────────────────────
        String arrow = expanded ? "▼" : "▶";
        context.drawText(mc.textRenderer, arrow,
                (int)(x + width - mc.textRenderer.getWidth(arrow) - 6),
                (int)(y + (HEADER_H - 8) / 2),
                VapeTheme.ACCENT_DIM.getRGB(), false);

        // ── Active-module badge ────────────────────────────────────────────
        long active = buttons.stream().filter(b -> b.getModule().isEnabled()).count();
        if (active > 0) {
            String badge = String.valueOf(active);
            int bw = mc.textRenderer.getWidth(badge) + 8;
            int bh = 11;
            int bx = (int)(x + 5);
            int by = (int)(y + (HEADER_H - bh) / 2);
            RenderUtils.renderRoundedQuad(context, new Color(34, 211, 238, 35),
                    bx, by, bx + bw, by + bh, 2, 8);
            RenderUtils.renderRoundedOutline(context, new Color(34, 211, 238, 90),
                    bx, by, bx + bw, by + bh, 2, 2, 2, 2, 0.5, 8);
            context.drawText(mc.textRenderer, badge, bx + 4, by + 2,
                    VapeTheme.ACCENT.getRGB(), false);
        }

        // ── Module cards ───────────────────────────────────────────────────
        if (expanded) {
            // Small gap between header and first card
            context.fill((int)x, (int)(y + HEADER_H), (int)(x + width), (int)(y + HEADER_H + 2),
                    new Color(0, 0, 0, 100).getRGB());

            for (ModuleButton btn : buttons)
                btn.render(context, mouseX, mouseY, delta);

            // Bottom rounding cap
            RenderUtils.renderRoundedQuad(context, new Color(10, 10, 10, 240),
                    x, y + totalH - RADIUS, x + width, y + totalH, RADIUS, 8);
        }
    }

    private void updateButtonPositions() {
        double buttonY = y + HEADER_H + 2;   // +2 for gap after header
        for (ModuleButton btn : buttons) {
            btn.x = x;
            btn.y = buttonY;
            buttonY += btn.getFullHeight();
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isOverHeader(mouseX, mouseY)) {
            if      (button == 0) { dragging = true; dragX = mouseX - x; dragY = mouseY - y; }
            else if (button == 1) { expanded = !expanded; }
            return;
        }
        if (expanded) for (ModuleButton mb : buttons) mb.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        if (expanded) for (ModuleButton mb : buttons) mb.mouseReleased(mouseX, mouseY, button);
    }

    public boolean isOverHeader(double mx, double my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + HEADER_H;
    }

    public void addModule(Module module) {
        double buttonY = y + HEADER_H + 2;
        for (ModuleButton btn : buttons) buttonY += btn.getFullHeight();
        buttons.add(new ModuleButton(module, x, buttonY, width, ModuleButton.CARD_H));
    }

    public void removeModule(Module module) {
        buttons.removeIf(btn -> btn.getModule() == module);
    }

    public List<ModuleButton> getButtons() { return buttons; }

    public double getTotalHeight() {
        if (!expanded) return HEADER_H;
        double h = HEADER_H + 2;
        for (ModuleButton btn : buttons) h += btn.getFullHeight();
        return h;
    }
}
