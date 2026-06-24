package dev.i726.rocky.gui.components;

import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.gui.components.settings.*;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.*;
import dev.i726.rocky.utils.RenderUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ModuleRow {

    public static final int BASE_HEIGHT = 18;

    private final Module module;
    private final List<SettingComponent> components = new ArrayList<>();

    private float switchAnim = 0f;
    private float expandAnim = 0f;
    private float hoverAnim  = 0f;
    private boolean expanded = false;

    public String getModuleName() { return module.getName().toString(); }
    public Module getModule()     { return module; }

    public ModuleRow(Module module) {
        this.module = module;
        for (Setting<?> s : module.getSettings()) {
            if      (s instanceof BooleanSetting b)  components.add(new BooleanComponent(b));
            else if (s instanceof NumberSetting  n)  components.add(new SliderComponent(n));
            else if (s instanceof ModeSetting<?> m)  components.add(new ModeComponent(m));
            else if (s instanceof MinMaxSetting  mm) components.add(new MinMaxComponent(mm));
            else if (s instanceof KeybindSetting k)  components.add(new KeybindComponent(k));
            else if (s instanceof StringSetting  st) components.add(new StringComponent(st));
        }
    }

    public int getHeight() {
        if (components.isEmpty() || expandAnim < 0.005f) return BASE_HEIGHT;
        int settingsH = components.stream().mapToInt(SettingComponent::getHeight).sum();
        return BASE_HEIGHT + (int) (settingsH * expandAnim);
    }

    public int getTotalSettingsHeight() {
        return components.stream().mapToInt(SettingComponent::getHeight).sum();
    }

    public void render(GuiGraphicsExtractor ctx, int absX, int absY, int panelWidth, int mouseX, int mouseY, float delta) {
        boolean hovering = mouseX >= absX && mouseX < absX + panelWidth
                        && mouseY >= absY && mouseY < absY + BASE_HEIGHT;

        float targetSwitch = module.isEnabled() ? 1f : 0f;
        float targetExpand = expanded ? 1f : 0f;
        float targetHover  = hovering ? 1f : 0f;

        switchAnim = RenderUtils.fast(switchAnim, targetSwitch, 12f);
        expandAnim = RenderUtils.fast(expandAnim, targetExpand, 10f);
        hoverAnim  = RenderUtils.fast(hoverAnim,  targetHover,  15f);

        if (hoverAnim > 0.01f) {
            ctx.fill(absX, absY, absX + panelWidth, absY + BASE_HEIGHT,
                    GuiTheme.withAlpha(GuiTheme.hoverBg(), (int) (10 * hoverAnim)));
        }

        if (switchAnim > 0.01f) {
            ctx.fill(absX, absY, absX + 2, absY + BASE_HEIGHT,
                    GuiTheme.withAlpha(GuiTheme.accentInt(), (int) (255 * switchAnim)));
        }

        int nameColor = hovering ? GuiTheme.textPrimary()
                : GuiTheme.withAlpha(GuiTheme.textPrimary(), 200);
        ctx.text(Minecraft.getInstance().font,
                module.getName().toString(), absX + 8, absY + 5, nameColor, false);

        if (!components.isEmpty()) {
            String arrow = expanded ? "-" : "+";
            ctx.text(Minecraft.getInstance().font,
                    arrow, absX + panelWidth - 40, absY + 5, GuiTheme.textSecondary(), false);
        }

        drawSwitch(ctx, absX + panelWidth - 28, absY + 4, switchAnim);

        if (expandAnim > 0.005f) {
            int settingsH = getTotalSettingsHeight();
            int clipTop    = absY + BASE_HEIGHT;
            int clipBottom = clipTop + (int) (settingsH * expandAnim);
            ctx.enableScissor(absX, clipTop, absX + panelWidth, clipBottom);
            int curY = clipTop;
            for (SettingComponent comp : components) {
                comp.render(ctx, absX, curY, panelWidth, mouseX, mouseY, delta);
                curY += comp.getHeight();
            }
            ctx.disableScissor();
        }

        // Queue tooltip if hovered and module has a description
        if (hovering) {
            CharSequence desc = module.getDescription();
            if (desc != null && !desc.toString().isBlank()) {
                dev.i726.rocky.gui.ClickGuiScreen.queueTooltip(desc.toString(), mouseX, mouseY);
            }
        }
    }

    private void drawSwitch(GuiGraphicsExtractor ctx, int x, int y, float anim) {
        int trackColor = GuiTheme.lerpColor(GuiTheme.toggleOff(), GuiTheme.toggleOn(), anim);
        ctx.fill(x + 1, y,     x + 21, y + 10, trackColor);
        ctx.fill(x,     y + 1, x + 22, y + 9,  trackColor);

        int thumbX = x + 2 + (int) (10f * anim);
        ctx.fill(thumbX,     y + 1, thumbX + 8, y + 9, GuiTheme.toggleThumb());
        ctx.fill(thumbX + 1, y,     thumbX + 7, y + 10, GuiTheme.toggleThumb());
    }

    public boolean mouseClicked(double mx, double my, int button, int absX, int absY, int panelWidth) {
        if (expandAnim > 0.05f && !components.isEmpty()) {
            int sY = absY + BASE_HEIGHT;
            for (SettingComponent comp : components) {
                if (comp.mouseClicked(mx, my, button, absX, sY, panelWidth)) return true;
                sY += comp.getHeight();
            }
        }

        if (mx >= absX && mx < absX + panelWidth && my >= absY && my < absY + BASE_HEIGHT) {
            int sx = absX + panelWidth - 28;
            if (button == 0 && mx >= sx && mx < sx + 22) {
                module.toggle();
                return true;
            }
            if (button == 0) {
                if (!components.isEmpty()) expanded = !expanded;
                return true;
            }
            if (button == 1) {
                module.toggle();
                return true;
            }
        }
        return false;
    }

    public boolean mouseDragged(double mx, double my, int button, double dx, double dy, int absX, int absY, int panelWidth) {
        if (expandAnim > 0.05f) {
            int sY = absY + BASE_HEIGHT;
            for (SettingComponent comp : components) {
                if (comp.mouseDragged(mx, my, button, dx, dy, absX, sY, panelWidth)) return true;
                sY += comp.getHeight();
            }
        }
        return false;
    }

    public void mouseReleased(double mx, double my, int button) {
        for (SettingComponent comp : components)
            comp.mouseReleased(mx, my, button, 0, 0, 0);
    }

    public boolean keyPressed(int key, int scan, int mods) {
        for (SettingComponent comp : components)
            if (comp.keyPressed(key, scan, mods)) return true;
        return false;
    }

    public boolean charTyped(char chr, int mods) {
        for (SettingComponent comp : components)
            if (comp.charTyped(chr, mods)) return true;
        return false;
    }
}
