package dev.i726.rocky.gui.vape.components;

import dev.i726.rocky.gui.vape.VapeTheme;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.Rocky;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class Panel extends Component {

    public  static final int HEADER_H = 22;

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

        // ── Outer 1px border around the entire panel ───────────────────────
        context.fill((int)x - 1, (int)y - 1, (int)(x + width) + 1, (int)(y + totalH) + 1,
                new Color(38, 38, 42, 210).getRGB());

        // ── Panel body — flat black rectangle ──────────────────────────────
        context.fill((int)x, (int)y, (int)(x + width), (int)(y + totalH),
                new Color(11, 11, 11, 242).getRGB());

        // ── Header — slightly lighter flat rectangle ───────────────────────
        context.fill((int)x, (int)y, (int)(x + width), (int)(y + HEADER_H),
                new Color(18, 18, 18, 252).getRGB());

        // Header bottom: 1px full-width cyan line
        context.fill((int)x, (int)(y + HEADER_H - 1), (int)(x + width), (int)(y + HEADER_H),
                VapeTheme.ACCENT.getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();
        int headerMidY = (int)(y + (HEADER_H - 8) / 2.0);

        // ── Active module count badge — top-left of header ─────────────────
        long active = buttons.stream().filter(b -> b.getModule().isEnabled()).count();
        int leftPad = 5;
        if (active > 0) {
            String badge = String.valueOf(active);
            int bw = mc.textRenderer.getWidth(badge) + 6;
            // Flat badge box
            context.fill((int)x + leftPad, (int)(y + (HEADER_H - 11) / 2.0),
                    (int)x + leftPad + bw, (int)(y + (HEADER_H - 11) / 2.0) + 11,
                    new Color(34, 211, 238, 28).getRGB());
            // Top and bottom 1px border on badge
            context.fill((int)x + leftPad, (int)(y + (HEADER_H - 11) / 2.0),
                    (int)x + leftPad + bw, (int)(y + (HEADER_H - 11) / 2.0) + 1,
                    new Color(34, 211, 238, 130).getRGB());
            context.drawText(mc.textRenderer, badge,
                    (int)x + leftPad + 3, (int)(y + (HEADER_H - 8) / 2.0),
                    VapeTheme.ACCENT.getRGB(), false);
            leftPad += bw + 4;
        }

        // ── Category name — centered in remaining space ─────────────────────
        String title = category.getName().toUpperCase();
        int tw = mc.textRenderer.getWidth(title);
        context.drawText(mc.textRenderer, title,
                (int)(x + (width - tw) / 2.0), headerMidY,
                VapeTheme.TEXT.getRGB(), false);

        // ── Collapse indicator — top right ──────────────────────────────────
        String arrow = expanded ? "-" : "+";
        int arrowW = mc.textRenderer.getWidth(arrow);
        context.drawText(mc.textRenderer, arrow,
                (int)(x + width - arrowW - 5), headerMidY,
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
