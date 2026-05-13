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

    public  static final int HEADER_H = 26;

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

        for (Module module : Rocky.INSTANCE.getModuleManager().getModulesInCategory(category)) {
            if (matchesSearch(module))
                buttons.add(new ModuleButton(module, x, 0, width));
        }
        layoutButtons();
    }

    private boolean matchesSearch(Module module) {
        if (searchFilter == null || searchFilter.isEmpty()) return true;
        String f = searchFilter;
        return module.getName().toString().toLowerCase().contains(f)
            || module.getDescription().toString().toLowerCase().contains(f);
    }

    public Category getCategory() { return category; }
    public double   getX()        { return x; }
    public double   getY()        { return y; }

    private void layoutButtons() {
        double sy = y + HEADER_H;
        for (ModuleButton btn : buttons) {
            btn.x = x;
            btn.y = sy;
            sy += btn.getFullHeight();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (dragging) {
            x = mouseX - dragX;
            y = mouseY - dragY;
            Rocky.INSTANCE.getProfileManager().setPanelPosition(category.getName(), x, y);
        }
        layoutButtons();

        double totalH = getTotalHeight();
        long active = buttons.stream().filter(b -> b.getModule().isEnabled()).count();

        // ── Outer drop shadow ──────────────────────────────────────────────
        RenderUtils.renderRoundedQuad(context, new Color(0, 0, 0, 80),
                x + 3, y + 4, x + width + 3, y + totalH + 4, 5, 6);

        // ── Active panel: neon cyan outer glow ─────────────────────────────
        if (active > 0) {
            RenderUtils.renderRoundedOutline(context,
                    new Color(34, 211, 238, 55),
                    x - 2, y - 2, x + width + 2, y + totalH + 2,
                    5, 5, 5, 5, 1.5, 12);
            RenderUtils.renderRoundedOutline(context,
                    new Color(34, 211, 238, 20),
                    x - 4, y - 4, x + width + 4, y + totalH + 4,
                    7, 7, 7, 7, 1.5, 12);
        }

        // ── Outer border ───────────────────────────────────────────────────
        RenderUtils.renderRoundedQuad(context, new Color(55, 55, 62, 255),
                x - 1, y - 1, x + width + 1, y + totalH + 1, 4, 8);

        // ── Panel body ─────────────────────────────────────────────────────
        RenderUtils.renderRoundedQuad(context, new Color(8, 8, 10, 245),
                x, y, x + width, y + totalH, 3, 8);

        // ── Header — gradient from lighter top to darker bottom ────────────
        context.fillGradient((int)x, (int)y, (int)(x + width), (int)(y + HEADER_H),
                new Color(28, 28, 32, 255).getRGB(), new Color(16, 16, 18, 255).getRGB());

        // Header bottom: 1px full-width accent line
        context.fill((int)x, (int)(y + HEADER_H - 1), (int)(x + width), (int)(y + HEADER_H),
                active > 0 ? VapeTheme.ACCENT.getRGB() : new Color(45, 45, 50, 255).getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();
        int headerMidY = (int)(y + (HEADER_H - 8) / 2.0);

        // ── Active module count badge — top-left of header (rounded pill) ──
        int leftPad = 5;
        {
            String badge = String.valueOf(active);
            int bw = mc.textRenderer.getWidth(badge) + 10;
            int bTop    = (int)(y + (HEADER_H - 14) / 2.0);
            int bBottom = bTop + 14;
            // Rounded badge
            RenderUtils.renderRoundedQuad(context,
                    active > 0 ? new Color(34, 211, 238, 40) : new Color(255, 255, 255, 8),
                    x + leftPad, bTop, x + leftPad + bw, bBottom, 3, 8);
            RenderUtils.renderRoundedOutline(context,
                    active > 0 ? new Color(34, 211, 238, 160) : new Color(255, 255, 255, 22),
                    x + leftPad, bTop, x + leftPad + bw, bBottom, 3, 3, 3, 3, 1, 8);
            int badgeTextColor = active > 0 ? VapeTheme.ACCENT.getRGB() : new Color(75, 75, 82).getRGB();
            context.drawText(mc.textRenderer, badge,
                    (int)x + leftPad + 5, (int)(y + (HEADER_H - 8) / 2.0),
                    badgeTextColor, false);
            leftPad += bw + 4;
        }

        // ── Category name — centered ────────────────────────────────────────
        String title = category.getName().toUpperCase();
        int tw = mc.textRenderer.getWidth(title);
        context.drawText(mc.textRenderer, title,
                (int)(x + (width - tw) / 2.0), headerMidY,
                active > 0 ? new Color(220, 220, 225).getRGB() : VapeTheme.TEXT_DIM.getRGB(),
                false);

        // ── Collapse indicator — top right ──────────────────────────────────
        String arrow = expanded ? "−" : "+";
        int arrowW = mc.textRenderer.getWidth(arrow);
        context.drawText(mc.textRenderer, arrow,
                (int)(x + width - arrowW - 6), headerMidY,
                VapeTheme.ACCENT_DIM.getRGB(), false);

        // ── Module rows ─────────────────────────────────────────────────────
        if (expanded) {
            for (ModuleButton btn : buttons)
                btn.render(context, mouseX, mouseY, delta);
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isOverHeader(mouseX, mouseY)) {
            if      (button == 0) { dragging = true; dragX = mouseX - x; dragY = mouseY - y; }
            else if (button == 1) { expanded = !expanded; }
            return;
        }
        if (expanded)
            for (ModuleButton mb : buttons) mb.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        if (expanded)
            for (ModuleButton mb : buttons) mb.mouseReleased(mouseX, mouseY, button);
    }

    public boolean isOverHeader(double mx, double my) {
        return mx >= x && mx <= x + width && my >= y && my <= y + HEADER_H;
    }

    public List<ModuleButton> getButtons() { return buttons; }

    public double getTotalHeight() {
        if (!expanded) return HEADER_H;
        double h = HEADER_H;
        for (ModuleButton btn : buttons) h += btn.getFullHeight();
        return h;
    }
}
