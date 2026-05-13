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

    private static final int HEADER_H  = 22;
    private static final float RADIUS   = 4f;

    private final Category category;
    private final List<ModuleButton> buttons = new ArrayList<>();
    private boolean dragging;
    private double dragX, dragY;
    public boolean expanded = true;
    private final String searchFilter;

    public Panel(Category category, double x, double y, double width, String searchFilter) {
        super(x, y, width, HEADER_H);
        this.category    = category;
        this.searchFilter = searchFilter;

        List<Module> modules = Rocky.INSTANCE.getModuleManager().getModulesInCategory(category);
        double buttonY = y + HEADER_H;
        for (Module module : modules) {
            if (matchesSearch(module)) {
                buttons.add(new ModuleButton(module, x, buttonY, width, 20));
                buttonY += 20;
            }
        }
    }

    private boolean matchesSearch(Module module) {
        if (searchFilter == null || searchFilter.isEmpty()) return true;
        return module.getName().toString().toLowerCase().contains(searchFilter)
            || module.getDescription().toString().toLowerCase().contains(searchFilter);
    }

    public Category getCategory()  { return category; }
    public double   getX()         { return x; }
    public double   getY()         { return y; }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (dragging) {
            x = mouseX - dragX;
            y = mouseY - dragY;
            Rocky.INSTANCE.getProfileManager().setPanelPosition(category.getName(), x, y);
        }
        updateButtonPositions();

        // Drop shadow
        RenderUtils.renderRoundedQuad(context, new Color(0, 0, 0, 55),
                x + 3, y + 3, x + width + 3, y + getTotalHeight() + 3, RADIUS, 10);

        double totalH = getTotalHeight();

        // Panel body background
        RenderUtils.renderRoundedQuad(context, VapeTheme.PANEL_BG,
                x, y, x + width, y + totalH, RADIUS, 10);

        // Subtle outer border
        RenderUtils.renderRoundedOutline(context, VapeTheme.BORDER,
                x, y, x + width, y + totalH, RADIUS, RADIUS, RADIUS, RADIUS, 0.5, 10);

        // Header background — slightly lighter
        RenderUtils.renderRoundedQuad(context, VapeTheme.PANEL_HEADER,
                x, y, x + width, y + HEADER_H,
                RADIUS, RADIUS, expanded ? 0 : RADIUS, expanded ? 0 : RADIUS, 10);

        // Cyan accent bar across the bottom of the header
        context.fill((int)(x + RADIUS), (int)(y + HEADER_H - 1),
                (int)(x + width - RADIUS), (int)(y + HEADER_H),
                VapeTheme.ACCENT.getRGB());

        // Category name — centered
        MinecraftClient mc = MinecraftClient.getInstance();
        String title  = category.getName().toUpperCase();
        int tw        = mc.textRenderer.getWidth(title);
        int centerX   = (int)(x + (width - tw) / 2);
        context.drawText(mc.textRenderer, title, centerX, (int)(y + (HEADER_H - 8) / 2),
                -1, false);

        // Collapse arrow — right side of header
        String arrow = expanded ? "v" : ">";
        context.drawText(mc.textRenderer, arrow,
                (int)(x + width - mc.textRenderer.getWidth(arrow) - 6),
                (int)(y + (HEADER_H - 8) / 2),
                VapeTheme.ACCENT_DIM.getRGB(), false);

        // Active-module count badge
        long active = buttons.stream().filter(b -> b.getModule().isEnabled()).count();
        if (active > 0) {
            String badge = String.valueOf(active);
            int bw = mc.textRenderer.getWidth(badge) + 6;
            int bh = 10;
            int bx = (int)(x + 6);
            int by = (int)(y + (HEADER_H - bh) / 2);
            RenderUtils.drawRoundedRect(context, bx, by, bx + bw, by + bh, 2,
                    VapeTheme.ACCENT_GLOW.getRGB());
            context.drawText(mc.textRenderer, badge, bx + 3, by + 1,
                    VapeTheme.ACCENT.getRGB(), false);
        }

        // Module buttons
        if (expanded) {
            for (ModuleButton button : buttons) {
                button.render(context, mouseX, mouseY, delta);
            }
            // Bottom rounded cap on last button
            if (!buttons.isEmpty()) {
                double capY = y + totalH - RADIUS;
                context.fill((int)x, (int)capY, (int)(x + width), (int)(y + totalH),
                        VapeTheme.PANEL_BG.getRGB());
            }
        }
    }

    private void updateButtonPositions() {
        double buttonY = y + HEADER_H;
        for (ModuleButton button : buttons) {
            button.x = x;
            button.y = buttonY;
            buttonY += button.getFullHeight();
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isOverHeader(mouseX, mouseY)) {
            if (button == 0) {
                dragging = true;
                dragX = mouseX - x;
                dragY = mouseY - y;
            } else if (button == 1) {
                expanded = !expanded;
            }
            return;
        }
        if (expanded) {
            for (ModuleButton mb : buttons) {
                mb.mouseClicked(mouseX, mouseY, button);
            }
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        if (expanded) {
            for (ModuleButton mb : buttons) {
                mb.mouseReleased(mouseX, mouseY, button);
            }
        }
    }

    public boolean isOverHeader(double mouseX, double mouseY) {
        return mouseX >= x && mouseX <= x + width
            && mouseY >= y && mouseY <= y + HEADER_H;
    }

    public void addModule(Module module) {
        double buttonY = y + HEADER_H;
        for (ModuleButton button : buttons) buttonY += button.getFullHeight();
        buttons.add(new ModuleButton(module, x, buttonY, width, 20));
    }

    public void removeModule(Module module) {
        buttons.removeIf(btn -> btn.getModule() == module);
    }

    public List<ModuleButton> getButtons() { return buttons; }

    public double getTotalHeight() {
        if (!expanded) return HEADER_H;
        double h = HEADER_H;
        for (ModuleButton button : buttons) h += button.getFullHeight();
        return h;
    }
}
