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

        // ── Outer border — 1px around entire panel ─────────────────────────
        int borderColor = new Color(40, 40, 42, 200).getRGB();
        context.fill((int)x - 1, (int)y - 1, (int)(x + width) + 1, (int)(y + totalH) + 1, borderColor);

        // ── Panel body background ───────────────────────────────────────────
        context.fill((int)x, (int)y, (int)(x + width), (int)(y + totalH),
                new Color(10, 10, 10, 235).getRGB());

        // ── Header — flat dark rectangle ───────────────────────────────────
        context.fill((int)x, (int)y, (int)(x + width), (int)(y + HEADER_H),
                new Color(16, 16, 16, 245).getRGB());

        // Cyan bottom edge of header (1px)
        context.fill((int)x, (int)(y + HEADER_H - 1), (int)(x + width), (int)(y + HEADER_H),
                VapeTheme.ACCENT.getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();

        // ── Active badge — left of header ──────────────────────────────────
        long active = buttons.stream().filter(b -> b.getModule().isEnabled()).count();
        int headerTextY = (int)(y + (HEADER_H - 8) / 2.0);

        if (active > 0) {
            String badge = String.valueOf(active);
            int bw = mc.textRenderer.getWidth(badge) + 6;
            int bx = (int)(x + 4);
            int by = (int)(y + (HEADER_H - 10) / 2.0);
            context.fill(bx, by, bx + bw, by + 10, new Color(34, 211, 238, 30).getRGB());
            context.fill(bx, by, bx + bw, by + 1, VapeTheme.ACCENT.getRGB());
            context.drawText(mc.textRenderer, badge, bx + 3, by + 1, VapeTheme.ACCENT.getRGB(), false);
        }

        // ── Category name centered ──────────────────────────────────────────
        String title = category.getName().toUpperCase();
        int tw = mc.textRenderer.getWidth(title);
        context.drawText(mc.textRenderer, title, (int)(x + (width - tw) / 2.0), headerTextY,
                VapeTheme.TEXT.getRGB(), false);

        // ── Collapse arrow right ────────────────────────────────────────────
        String arrow = expanded ? "v" : ">";
        context.drawText(mc.textRenderer, arrow,
                (int)(x + width - mc.textRenderer.getWidth(arrow) - 5), headerTextY,
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

    public List<ModuleButton> getButtons() { return buttons; }

    public double getTotalHeight() {
        if (!expanded) return HEADER_H;
        double h = HEADER_H;
        for (ModuleButton btn : buttons) h += btn.getFullHeight();
        return h;
    }
}
