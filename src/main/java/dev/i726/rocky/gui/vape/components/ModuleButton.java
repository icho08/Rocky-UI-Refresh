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

    // Card height: name row + description + footer strip
    public static final int CARD_H = 36;

    // Switch
    private static final double SW_W = 22;
    private static final double SW_H = 10;

    private final Module module;
    private boolean expanded;
    private final List<Component> settingComponents = new ArrayList<>();
    private boolean dragging;
    private double dragX, dragY;
    private float switchAnim;

    public ModuleButton(Module module, double x, double y, double width, double height) {
        super(x, y, width, CARD_H);   // always CARD_H, ignore passed height
        this.module    = module;
        this.switchAnim = module.isEnabled() ? 1f : 0f;

        // Keybind always first
        settingComponents.add(new KeybindSetting(module, x, 0, width, 20));

        for (Setting<?> s : module.getSettings()) {
            if      (s instanceof BooleanSetting)   settingComponents.add(new CheckboxSetting((BooleanSetting) s, x, 0, width, 20));
            else if (s instanceof NumberSetting)    settingComponents.add(new SliderSetting((NumberSetting) s, x, 0, width, 28));
            else if (s instanceof ModeSetting)      settingComponents.add(new ModeSettingComponent((ModeSetting<?>) s, x, 0, width, 20));
            else if (s instanceof MinMaxSetting)    settingComponents.add(new MinMaxSettingComponent((MinMaxSetting) s, x, 0, width, 28));
            else if (s instanceof dev.i726.rocky.module.setting.StringSetting)
                settingComponents.add(new StringSettingComponent((dev.i726.rocky.module.setting.StringSetting) s, x, 0, width, 20));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (dragging) { x = mouseX - dragX; y = mouseY - dragY; }

        // Smooth switch animation
        float target = module.isEnabled() ? 1f : 0f;
        float diff   = target - switchAnim;
        switchAnim  += Math.signum(diff) * Math.min(Math.abs(diff), (float)(RenderUtils.deltaTime() * 14f));

        boolean hovered = isHovered(mouseX, mouseY) && !dragging;
        boolean enabled = module.isEnabled();

        MinecraftClient mc = MinecraftClient.getInstance();
        String name  = module.getName().toString();
        String desc  = (module.getDescription() != null) ? module.getDescription().toString() : "";
        int    nSettings = settingComponents.size() - 1; // minus keybind

        // ── Card background ────────────────────────────────────────────────
        if (enabled) {
            // Gradient: subtle cyan wash on left → dark enabled bg
            context.fillGradient(
                    (int)x, (int)y, (int)(x + width * 0.6), (int)(y + height),
                    new Color(34, 211, 238, 30).getRGB(),
                    new Color(10, 25, 26, 215).getRGB());
            context.fill((int)(x + width * 0.6), (int)y, (int)(x + width), (int)(y + height),
                    new Color(10, 25, 26, 215).getRGB());
        } else {
            context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                    new Color(15, 15, 15, 215).getRGB());
        }

        if (hovered && !enabled) {
            context.fill((int)x, (int)y, (int)(x + width), (int)(y + height),
                    new Color(255, 255, 255, 8).getRGB());
        }

        // ── Card borders (like the web UI card outline) ────────────────────
        if (enabled) {
            // Top border — cyan
            context.fill((int)x, (int)y, (int)(x + width), (int)y + 1,
                    new Color(34, 211, 238, 160).getRGB());
            // Bottom border — cyan dim
            context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height),
                    new Color(34, 211, 238, 60).getRGB());
            // Left enabled accent bar + glow
            context.fill((int)x, (int)y, (int)x + 2, (int)(y + height),
                    VapeTheme.ACCENT.getRGB());
            context.fill((int)x + 2, (int)y, (int)x + 6, (int)(y + height),
                    new Color(34, 211, 238, 40).getRGB());
            context.fill((int)x + 6, (int)y, (int)x + 10, (int)(y + height),
                    new Color(34, 211, 238, 12).getRGB());
        } else {
            // Subtle top + bottom border for the card feel
            context.fill((int)x, (int)y, (int)(x + width), (int)y + 1,
                    new Color(255, 255, 255, 12).getRGB());
            context.fill((int)x, (int)(y + height - 1), (int)(x + width), (int)(y + height),
                    new Color(255, 255, 255, 6).getRGB());
        }

        // ── Top-right corner fold accent (web UI has this!) ───────────────
        int cornerSize = 5;
        int cornerColor = enabled
                ? new Color(34, 211, 238, 200).getRGB()
                : new Color(255, 255, 255, 25).getRGB();
        // Horizontal part of corner L
        context.fill((int)(x + width - cornerSize), (int)y,
                (int)(x + width), (int)y + 1, cornerColor);
        // Vertical part of corner L
        context.fill((int)(x + width - 1), (int)y,
                (int)(x + width), (int)(y + cornerSize), cornerColor);

        // ── Module name (row 1) ────────────────────────────────────────────
        int nameTextX = enabled ? (int)(x + 10) : (int)(x + 6);
        // Truncate to leave room for switch
        int maxNameW = (int)(width - 10 - SW_W - 14);
        if (mc.textRenderer.getWidth(name) > maxNameW)
            name = mc.textRenderer.trimToWidth(name, maxNameW - 4) + "..";

        int nameColor = enabled
                ? VapeTheme.ACCENT.getRGB()           // cyan when on (with shadow = glow effect)
                : new Color(200, 200, 200).getRGB();  // bright white-ish when off
        context.drawText(mc.textRenderer, name, nameTextX, (int)(y + 5), nameColor, enabled);

        // ── Toggle switch (top-right area) ────────────────────────────────
        double swX = x + width - SW_W - 6;
        double swY = y + 4;
        RenderUtils.renderSwitch(context, enabled, switchAnim, swX, swY, SW_W, SW_H, VapeTheme.ACCENT);

        // ── Description text (row 2) ───────────────────────────────────────
        if (!desc.isEmpty()) {
            int maxDescW = (int)(width - 10 - 4);
            String descTrunc = desc;
            if (mc.textRenderer.getWidth(descTrunc) > maxDescW)
                descTrunc = mc.textRenderer.trimToWidth(descTrunc, maxDescW - 6) + "..";
            context.drawText(mc.textRenderer, descTrunc,
                    (int)(x + 6), (int)(y + 16),
                    new Color(90, 90, 90).getRGB(), false);
        }

        // ── Footer: settings count (bottom strip) ─────────────────────────
        if (nSettings > 0) {
            // Footer separator line
            context.fill((int)x, (int)(y + height - 10), (int)(x + width), (int)(y + height - 9),
                    new Color(255, 255, 255, 5).getRGB());

            String settingsStr = "● " + nSettings + " SETTINGS";
            int settingsColor = expanded
                    ? VapeTheme.ACCENT.getRGB()
                    : new Color(70, 70, 75).getRGB();
            context.drawText(mc.textRenderer, settingsStr,
                    (int)(x + 6), (int)(y + height - 8),
                    settingsColor, false);

            // Right-side chevron for expand state
            String chev = expanded ? "▲" : "▼";
            int chW = mc.textRenderer.getWidth(chev);
            context.drawText(mc.textRenderer, chev,
                    (int)(x + width - chW - 5), (int)(y + height - 8),
                    expanded ? VapeTheme.ACCENT_DIM.getRGB() : new Color(55, 55, 60).getRGB(), false);
        }

        // ── Settings rows (expanded) ───────────────────────────────────────
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
        if (isHovered((int)mouseX, (int)mouseY)) {
            if      (button == 0) module.toggle();
            else if (button == 1 && settingComponents.size() > 1) expanded = !expanded;
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
