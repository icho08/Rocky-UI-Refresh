package dev.i726.rocky.gui.vape.components;

import dev.i726.rocky.gui.vape.VapeTheme;
import dev.i726.rocky.gui.vape.components.settings.*;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.*;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public class ModuleButton extends Component {

    private final Module module;
    private boolean expanded;
    private final List<Component> settingComponents = new ArrayList<>();
    private boolean dragging;
    private double dragX, dragY;
    private float switchAnim;

    // Switch dimensions
    private static final double SW_W = 24;
    private static final double SW_H = 12;

    public ModuleButton(Module module, double x, double y, double width, double height) {
        super(x, y, width, height);
        this.module    = module;
        this.switchAnim = module.isEnabled() ? 1f : 0f;

        settingComponents.add(new KeybindSetting(module, x, 0, width, 20));

        for (Setting<?> s : module.getSettings()) {
            if      (s instanceof BooleanSetting)    settingComponents.add(new CheckboxSetting((BooleanSetting) s, x, 0, width, 20));
            else if (s instanceof NumberSetting)     settingComponents.add(new SliderSetting((NumberSetting) s, x, 0, width, 28));
            else if (s instanceof ModeSetting)       settingComponents.add(new ModeSettingComponent((ModeSetting<?>) s, x, 0, width, 20));
            else if (s instanceof MinMaxSetting)     settingComponents.add(new MinMaxSettingComponent((MinMaxSetting) s, x, 0, width, 28));
            else if (s instanceof dev.i726.rocky.module.setting.StringSetting)
                settingComponents.add(new StringSettingComponent((dev.i726.rocky.module.setting.StringSetting) s, x, 0, width, 20));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (dragging) { x = mouseX - dragX; y = mouseY - dragY; }

        // Smooth switch animation
        float target = module.isEnabled() ? 1f : 0f;
        float speed  = (float)(RenderUtils.deltaTime() * 14f);
        float diff   = target - switchAnim;
        switchAnim  += Math.signum(diff) * Math.min(Math.abs(diff), speed);

        boolean hovered = isHovered(mouseX, mouseY) && !dragging;
        boolean enabled = module.isEnabled();

        // ── Row background ─────────────────────────────────────────────────
        if (enabled) {
            // Gradient: cyan-tinted left edge fading to dark — shows depth
            context.fillGradient(
                    (int) x, (int) y, (int) (x + width * 0.55), (int) (y + height),
                    new Color(34, 211, 238, 38).getRGB(),
                    VapeTheme.MODULE_ENABLED.getRGB());
            context.fill(
                    (int) (x + width * 0.55), (int) y,
                    (int) (x + width), (int) (y + height),
                    VapeTheme.MODULE_ENABLED.getRGB());
        } else {
            context.fill((int) x, (int) y, (int) (x + width), (int) (y + height),
                    VapeTheme.MODULE_BG.getRGB());
        }

        if (hovered) {
            context.fill((int) x, (int) y, (int) (x + width), (int) (y + height),
                    new Color(255, 255, 255, 12).getRGB());
        }

        // Row bottom separator
        context.fill((int) x, (int) (y + height - 1), (int) (x + width), (int) (y + height),
                new Color(255, 255, 255, 6).getRGB());

        // ── Enabled accent left bar (3 px solid + glow) ───────────────────
        if (enabled) {
            // Core bar
            context.fill((int) x, (int) y, (int) x + 3, (int) (y + height),
                    VapeTheme.ACCENT.getRGB());
            // Glow layer 1
            context.fill((int) x + 3, (int) y, (int) x + 7, (int) (y + height),
                    new Color(34, 211, 238, 50).getRGB());
            // Glow layer 2 (wider fade)
            context.fill((int) x + 7, (int) y, (int) x + 12, (int) (y + height),
                    new Color(34, 211, 238, 16).getRGB());
        }

        // ── Module name ───────────────────────────────────────────────────
        MinecraftClient mc = MinecraftClient.getInstance();
        // Reserve space: accent bar (12) + padding (4) + switch (SW_W) + expand btn (10) + margin (6)
        int maxNameW = (int) (width - 12 - 4 - SW_W - 10 - 6);
        String name = module.getName().toString();
        if (mc.textRenderer.getWidth(name) > maxNameW)
            name = mc.textRenderer.trimToWidth(name, maxNameW - 4) + "..";

        int textX = enabled ? (int) (x + 16) : (int) (x + 8);
        int textColor = enabled ? VapeTheme.TEXT.getRGB() : VapeTheme.TEXT_DIM.getRGB();
        // Shadow text for enabled modules gives a premium look
        context.drawText(mc.textRenderer, name, textX, (int) (y + (height - 8) / 2),
                textColor, enabled);

        // ── Toggle switch ─────────────────────────────────────────────────
        double swX = x + width - SW_W - 5;
        double swY = y + (height - SW_H) / 2;
        RenderUtils.renderSwitch(context, enabled, switchAnim, swX, swY, SW_W, SW_H, VapeTheme.ACCENT);

        // ── Expand/collapse chevron ────────────────────────────────────────
        if (!settingComponents.isEmpty()) {
            String ch = expanded ? "-" : "+";
            int chX = (int) (swX - mc.textRenderer.getWidth(ch) - 6);
            int chY = (int) (y + (height - 8) / 2);
            context.drawText(mc.textRenderer, ch, chX, chY,
                    expanded ? VapeTheme.ACCENT.getRGB() : new Color(120, 120, 120).getRGB(), false);
        }

        // ── Description tooltip (hover) ────────────────────────────────────
        if (hovered && module.getDescription() != null
                && !module.getDescription().toString().isEmpty()) {
            String desc = module.getDescription().toString();
            int dw = mc.textRenderer.getWidth(desc);
            int tx = mouseX + 12;
            int ty = mouseY - 16;
            // Tooltip background
            RenderUtils.renderRoundedQuad(context, new Color(6, 6, 6, 230),
                    tx - 5, ty - 3, tx + dw + 5, ty + 11, 3, 8);
            // Cyan left accent on tooltip
            context.fill(tx - 5, ty - 3, tx - 3, ty + 11, VapeTheme.ACCENT.getRGB());
            RenderUtils.renderRoundedOutline(context, new Color(34, 211, 238, 60),
                    tx - 5, ty - 3, tx + dw + 5, ty + 11, 3, 3, 3, 3, 0.5, 8);
            context.drawText(mc.textRenderer, desc, tx, ty, VapeTheme.TEXT_DIM.getRGB(), false);
        }

        // ── Settings rows ─────────────────────────────────────────────────
        if (expanded && !dragging) {
            double sy = y + height;
            for (Component setting : settingComponents) {
                setting.x = x;
                setting.y = sy;
                setting.render(context, mouseX, mouseY, delta);
                sy += setting.height;
            }
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered((int) mouseX, (int) mouseY)) {
            if      (button == 0) module.toggle();
            else if (button == 1 && !settingComponents.isEmpty()) expanded = !expanded;
            else if (button == 2) { dragging = true; dragX = mouseX - x; dragY = mouseY - y; }
            return;
        }
        if (expanded && !dragging)
            for (Component s : settingComponents) s.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 2) dragging = false;
        if (expanded && !dragging)
            for (Component s : settingComponents) s.mouseReleased(mouseX, mouseY, button);
    }

    public boolean isDragging() { return dragging; }
    public Module  getModule()  { return module; }

    public boolean onKey(int key) {
        if (expanded) {
            for (Component s : settingComponents) {
                if (s instanceof KeybindSetting         && ((KeybindSetting) s).onKey(key))         return true;
                if (s instanceof StringSettingComponent && ((StringSettingComponent) s).onKey(key)) return true;
            }
        }
        return false;
    }

    public double getFullHeight() {
        if (!expanded) return height;
        double h = height;
        for (Component s : settingComponents) h += s.height;
        return h;
    }
}
