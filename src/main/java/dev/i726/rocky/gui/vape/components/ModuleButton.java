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

    public static final int ROW_H = 22;

    // Which module the mouse is over right now (reset each frame by RockyGui)
    public static Module hoveredModule = null;

    private static final double SW_W = 22;
    private static final double SW_H = 10;

    private final Module module;
    private boolean expanded;
    private final List<Component> settingComponents = new ArrayList<>();
    private float switchAnim;

    public ModuleButton(Module module, double x, double y, double width) {
        super(x, y, width, ROW_H);
        this.module    = module;
        this.switchAnim = module.isEnabled() ? 1f : 0f;

        settingComponents.add(new KeybindSetting(module, x, 0, width, 20));
        for (Setting<?> s : module.getSettings()) {
            if      (s instanceof BooleanSetting)  settingComponents.add(new CheckboxSetting((BooleanSetting) s, x, 0, width, 20));
            else if (s instanceof NumberSetting)   settingComponents.add(new SliderSetting((NumberSetting) s, x, 0, width, 28));
            else if (s instanceof ModeSetting)     settingComponents.add(new ModeSettingComponent((ModeSetting<?>) s, x, 0, width, 20));
            else if (s instanceof MinMaxSetting)   settingComponents.add(new MinMaxSettingComponent((MinMaxSetting) s, x, 0, width, 28));
            else if (s instanceof dev.i726.rocky.module.setting.StringSetting)
                settingComponents.add(new StringSettingComponent((dev.i726.rocky.module.setting.StringSetting) s, x, 0, width, 20));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Smooth toggle animation
        float target  = module.isEnabled() ? 1f : 0f;
        float diff    = target - switchAnim;
        switchAnim   += Math.signum(diff) * Math.min(Math.abs(diff), (float)(RenderUtils.deltaTime() * 14f));

        boolean hovered = isHovered(mouseX, mouseY);
        boolean enabled = module.isEnabled();

        if (hovered) hoveredModule = module;

        // ── Row background — flat rectangle ────────────────────────────────
        if (enabled) {
            // Enabled: dark cyan tint
            context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                    new Color(10, 26, 28, 220).getRGB());
            // Left accent bar  (solid 2px + soft 3px glow)
            context.fill((int)x, (int)y, (int)x + 2, (int)(y + height),
                    VapeTheme.ACCENT.getRGB());
            context.fill((int)x + 2, (int)y, (int)x + 5, (int)(y + height),
                    new Color(34, 211, 238, 45).getRGB());
        } else {
            context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                    new Color(14, 14, 14, 215).getRGB());
            if (hovered) context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                    new Color(255, 255, 255, 8).getRGB());
        }

        // Bottom separator
        context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height),
                new Color(255, 255, 255, 7).getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();

        // ── Module name ─────────────────────────────────────────────────────
        int textX   = enabled ? (int)(x + 8)  : (int)(x + 5);
        int maxNameW = (int)(width - 10 - SW_W - 14);
        String name = module.getName().toString();
        if (mc.textRenderer.getWidth(name) > maxNameW)
            name = mc.textRenderer.trimToWidth(name, maxNameW - 4) + "..";

        int nameColor = enabled ? VapeTheme.ACCENT.getRGB() : new Color(195, 195, 195).getRGB();
        context.drawText(mc.textRenderer, name, textX, (int)(y + (ROW_H - 8) / 2.0), nameColor, enabled);

        // ── Settings count badge (right of name, small) ─────────────────────
        int nSettings = settingComponents.size() - 1; // minus keybind entry
        if (nSettings > 0) {
            String ct = String.valueOf(nSettings);
            int bx = textX + mc.textRenderer.getWidth(mc.textRenderer.trimToWidth(name, maxNameW)) + 4;
            int by = (int)(y + (ROW_H - 8) / 2.0);
            context.drawText(mc.textRenderer, ct, bx, by, new Color(55, 55, 60).getRGB(), false);
        }

        // ── Toggle switch ────────────────────────────────────────────────────
        double swX = x + width - SW_W - 5;
        double swY = y + (ROW_H - SW_H) / 2.0;
        RenderUtils.renderSwitch(context, enabled, switchAnim, swX, swY, SW_W, SW_H, VapeTheme.ACCENT);

        // ── Settings rows (expanded) ──────────────────────────────────────────
        if (expanded) {
            double sy = y + height;
            for (Component s : settingComponents) {
                s.x = x;
                s.y = sy;
                s.render(context, mouseX, mouseY, delta);
                sy += s.height;
            }
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered((int)mouseX, (int)mouseY)) {
            if      (button == 0) module.toggle();
            else if (button == 1 && settingComponents.size() > 1) expanded = !expanded;
            return;
        }
        if (expanded) for (Component s : settingComponents) s.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (expanded) for (Component s : settingComponents) s.mouseReleased(mouseX, mouseY, button);
    }

    public boolean onKey(int key) {
        if (expanded) {
            for (Component s : settingComponents) {
                if (s instanceof KeybindSetting         && ((KeybindSetting) s).onKey(key))         return true;
                if (s instanceof StringSettingComponent && ((StringSettingComponent) s).onKey(key)) return true;
            }
        }
        return false;
    }

    public Module  getModule()     { return module; }
    public boolean isExpanded()    { return expanded; }

    public double getFullHeight() {
        if (!expanded) return height;
        double h = height;
        for (Component s : settingComponents) h += s.height;
        return h;
    }
}
