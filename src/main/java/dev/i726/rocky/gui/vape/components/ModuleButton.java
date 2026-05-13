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

    public static final int ROW_H = 24;

    /** Set each frame by render(); read by RockyGui to show description strip. */
    public static Module hoveredModule = null;

    private static final int SW_W = 28;  // toggle track width
    private static final int SW_H = 12;  // toggle track height

    private final Module module;
    private boolean expanded;
    private final List<Component> settingComponents = new ArrayList<>();
    private float switchAnim; // 0.0 = off, 1.0 = on

    public ModuleButton(Module module, double x, double y, double width) {
        super(x, y, width, ROW_H);
        this.module     = module;
        this.switchAnim = module.isEnabled() ? 1f : 0f;

        // Always add keybind row first
        settingComponents.add(new dev.i726.rocky.gui.vape.components.settings.KeybindSetting(module, x, 0, width, 20));

        for (Setting<?> s : module.getSettings()) {
            if      (s instanceof BooleanSetting) settingComponents.add(new CheckboxSetting((BooleanSetting) s, x, 0, width, 20));
            else if (s instanceof NumberSetting)  settingComponents.add(new SliderSetting((NumberSetting) s, x, 0, width, 26));
            else if (s instanceof ModeSetting)    settingComponents.add(new ModeSettingComponent((ModeSetting<?>) s, x, 0, width, 20));
            else if (s instanceof MinMaxSetting)  settingComponents.add(new MinMaxSettingComponent((MinMaxSetting) s, x, 0, width, 26));
            else if (s instanceof dev.i726.rocky.module.setting.StringSetting)
                settingComponents.add(new StringSettingComponent((dev.i726.rocky.module.setting.StringSetting) s, x, 0, width, 20));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        boolean enabled = module.isEnabled();
        boolean hovered = isHovered(mouseX, mouseY);

        // Animate switch
        float target  = enabled ? 1f : 0f;
        float diff    = target - switchAnim;
        switchAnim   += Math.signum(diff) * Math.min(Math.abs(diff), (float)(RenderUtils.deltaTime() * 12f));

        if (hovered) hoveredModule = module;

        // ── Row background ─────────────────────────────────────────────────
        if (enabled) {
            // Dark base fill
            context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                    new Color(8, 22, 24, 245).getRGB());
            // Horizontal gradient overlay: cyan-tint left → transparent right
            context.fillGradient((int)x, (int)y, (int)(x + width), (int)(y + height),
                    new Color(34, 211, 238, 28).getRGB(),
                    new Color(34, 211, 238, 0).getRGB());
            // Bold left accent bar (4px)
            context.fill((int)x, (int)y, (int)x + 4, (int)(y + height), VapeTheme.ACCENT.getRGB());
            // Soft fade next to bar
            context.fillGradient((int)x + 4, (int)y, (int)x + 14, (int)(y + height),
                    new Color(34, 211, 238, 45).getRGB(),
                    new Color(34, 211, 238, 0).getRGB());
            // Subtle right glow
            context.fill((int)(x + width - 1), (int)y, (int)(x + width), (int)(y + height),
                    new Color(34, 211, 238, 30).getRGB());
        } else {
            context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                    new Color(13, 13, 13, 230).getRGB());
            if (hovered)
                context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                        new Color(255, 255, 255, 12).getRGB());
        }

        // Bottom separator
        context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height),
                new Color(255, 255, 255, 14).getRGB());

        MinecraftClient mc = MinecraftClient.getInstance();

        // ── Module name ─────────────────────────────────────────────────────
        int textLeft  = enabled ? (int)(x + 12) : (int)(x + 7);
        int nameMaxW  = (int)(width - textLeft - SW_W - 12);
        String name   = module.getName().toString();
        
        // Only trim if name is actually too wide
        if (!name.isEmpty() && mc.textRenderer.getWidth(name) > nameMaxW && nameMaxW > 10) {
            name = mc.textRenderer.trimToWidth(name, nameMaxW - 10) + "..";
        }

        int nameColor = enabled ? VapeTheme.ACCENT.getRGB() : new Color(148, 148, 155).getRGB();
        context.drawText(mc.textRenderer, name,
                textLeft, (int)(y + (ROW_H - 8) / 2.0), nameColor, enabled);

        // ── Toggle switch — rounded pill using RenderUtils ──────────────────
        RenderUtils.renderSwitch(context, enabled, switchAnim,
                x + width - SW_W - 5, y + (ROW_H - SW_H) / 2.0,
                SW_W, SW_H, VapeTheme.ACCENT);

        // ── Settings rows (expanded with right-click) ───────────────────────
        if (expanded) {
            double sy = y + ROW_H;
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
        if (expanded)
            for (Component s : settingComponents) s.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (expanded)
            for (Component s : settingComponents) s.mouseReleased(mouseX, mouseY, button);
    }

    public boolean onKey(int key) {
        if (!expanded) return false;
        for (Component s : settingComponents) {
            if (s instanceof dev.i726.rocky.gui.vape.components.settings.KeybindSetting         && ((dev.i726.rocky.gui.vape.components.settings.KeybindSetting)s).onKey(key))         return true;
            if (s instanceof StringSettingComponent && ((StringSettingComponent)s).onKey(key)) return true;
        }
        return false;
    }

    public Module  getModule()  { return module; }

    public double getFullHeight() {
        if (!expanded) return height;
        double h = height;
        for (Component s : settingComponents) h += s.height;
        return h;
    }
}
