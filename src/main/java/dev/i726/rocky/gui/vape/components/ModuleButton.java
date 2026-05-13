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

    private static final int BTN_H = 20;

    private final Module module;
    private boolean expanded;
    private final List<Component> settingComponents = new ArrayList<>();
    private boolean dragging;
    private double dragX, dragY;

    // Per-button animation state (0 = off, 1 = on)
    private float switchAnim = 0f;

    public ModuleButton(Module module, double x, double y, double width, double height) {
        super(x, y, width, BTN_H);
        this.module = module;
        switchAnim  = module.isEnabled() ? 1f : 0f;

        // Keybind row always first in settings
        settingComponents.add(new KeybindSetting(module, x, 0, width, 18));

        for (Setting<?> setting : module.getSettings()) {
            if (setting instanceof BooleanSetting) {
                settingComponents.add(new CheckboxSetting((BooleanSetting) setting, x, 0, width, 18));
            } else if (setting instanceof NumberSetting) {
                settingComponents.add(new SliderSetting((NumberSetting) setting, x, 0, width, 26));
            } else if (setting instanceof ModeSetting) {
                settingComponents.add(new ModeSettingComponent((ModeSetting<?>) setting, x, 0, width, 18));
            } else if (setting instanceof MinMaxSetting) {
                settingComponents.add(new MinMaxSettingComponent((MinMaxSetting) setting, x, 0, width, 26));
            } else if (setting instanceof dev.i726.rocky.module.setting.StringSetting) {
                settingComponents.add(new StringSettingComponent(
                        (dev.i726.rocky.module.setting.StringSetting) setting, x, 0, width, 18));
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (dragging) { x = mouseX - dragX; y = mouseY - dragY; }

        // Animate switch
        float target = module.isEnabled() ? 1f : 0f;
        float speed  = (float)(RenderUtils.deltaTime() * 12f);
        switchAnim   = switchAnim + Math.signum(target - switchAnim) * Math.min(Math.abs(target - switchAnim), speed);

        boolean hovered = isHovered(mouseX, mouseY) && !dragging;

        // ── Row background ────────────────────────────────────────────────
        Color bg = module.isEnabled() ? VapeTheme.MODULE_ENABLED : VapeTheme.MODULE_BG;
        context.fill((int)x, (int)y, (int)(x + width), (int)(y + height), bg.getRGB());

        if (hovered) {
            context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                    VapeTheme.HOVER_OVERLAY.getRGB());
        }

        // Separator line at bottom of row
        context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height),
                VapeTheme.SEPARATOR.getRGB());

        // ── Enabled left accent bar ────────────────────────────────────────
        if (module.isEnabled()) {
            // Glowing 2px bar
            context.fill((int)x, (int)y, (int)x + 2, (int)(y + height), VapeTheme.ACCENT.getRGB());
            // Soft glow spread
            context.fill((int)x + 2, (int)y, (int)x + 5, (int)(y + height), VapeTheme.ACCENT_GLOW.getRGB());
        }

        // ── Module name ───────────────────────────────────────────────────
        int textColor = module.isEnabled() ? VapeTheme.ACCENT.getRGB() : VapeTheme.TEXT_DIM.getRGB();
        String name = module.getName().toString();
        MinecraftClient mc = MinecraftClient.getInstance();
        int maxNameW = (int)(width - 30);
        if (mc.textRenderer.getWidth(name) > maxNameW) {
            name = mc.textRenderer.trimToWidth(name, maxNameW - 6) + "..";
        }
        context.drawText(mc.textRenderer, name,
                (int)(x + 8), (int)(y + (height - 8) / 2), textColor, false);

        // ── Toggle switch ─────────────────────────────────────────────────
        double swW  = 20;
        double swH  = 10;
        double swX  = x + width - swW - 5;
        double swY  = y + (height - swH) / 2;
        RenderUtils.renderSwitch(context, module.isEnabled(), switchAnim, swX, swY, swW, swH, VapeTheme.ACCENT);

        // ── Expand chevron (only if has settings) ─────────────────────────
        if (!settingComponents.isEmpty()) {
            String chevron = expanded ? "-" : "+";
            int chColor = expanded ? VapeTheme.ACCENT.getRGB() : VapeTheme.TEXT_MUTED.getRGB();
            context.drawText(mc.textRenderer, chevron,
                    (int)(x + width - swW - mc.textRenderer.getWidth(chevron) - 10),
                    (int)(y + (height - 8) / 2), chColor, false);
        }

        // ── Hover tooltip with description ────────────────────────────────
        if (hovered && module.getDescription() != null
                && !module.getDescription().toString().isEmpty()) {
            String desc = module.getDescription().toString();
            int tw = mc.textRenderer.getWidth(desc);
            int tx = mouseX + 10;
            int ty = mouseY - 14;
            // Clamp to screen
            if (tx + tw + 8 > (int)(x + width * 5)) tx = mouseX - tw - 14;
            RenderUtils.drawRoundedRect(context, tx - 4, ty - 2, tx + tw + 4, ty + 10, 2,
                    new Color(8, 8, 8, 220).getRGB());
            RenderUtils.renderRoundedOutline(context, VapeTheme.ACCENT_DIM,
                    tx - 4, ty - 2, tx + tw + 4, ty + 10, 2, 2, 2, 2, 0.5, 8);
            context.drawText(mc.textRenderer, desc, tx, ty, VapeTheme.TEXT_DIM.getRGB(), false);
        }

        // ── Settings rows ─────────────────────────────────────────────────
        if (expanded && !dragging) {
            double settingY = y + height;
            for (Component setting : settingComponents) {
                setting.x = x;
                setting.y = settingY;
                setting.render(context, mouseX, mouseY, delta);
                settingY += setting.height;
            }
        }
    }

    @Override
    public void mouseClicked(double mouseX, double mouseY, int button) {
        if (isHovered((int)mouseX, (int)mouseY)) {
            if (button == 0) {
                module.toggle();
            } else if (button == 1) {
                if (!settingComponents.isEmpty()) expanded = !expanded;
            } else if (button == 2) {
                dragging = true;
                dragX = mouseX - x;
                dragY = mouseY - y;
            }
            return;
        }
        if (expanded && !dragging) {
            for (Component s : settingComponents) s.mouseClicked(mouseX, mouseY, button);
        }
    }

    @Override
    public void mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 2) dragging = false;
        if (expanded && !dragging) {
            for (Component s : settingComponents) s.mouseReleased(mouseX, mouseY, button);
        }
    }

    public boolean isDragging()  { return dragging; }
    public Module  getModule()   { return module;   }

    public boolean onKey(int key) {
        if (expanded) {
            for (Component setting : settingComponents) {
                if (setting instanceof KeybindSetting) {
                    if (((KeybindSetting) setting).onKey(key)) return true;
                }
                if (setting instanceof StringSettingComponent) {
                    if (((StringSettingComponent) setting).onKey(key)) return true;
                }
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
