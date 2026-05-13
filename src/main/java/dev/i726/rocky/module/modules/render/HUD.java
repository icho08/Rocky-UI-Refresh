package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.events.HudListener;
import dev.i726.rocky.gui.ClickGuiScreen;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

import java.awt.Color;
import java.util.List;

public final class HUD extends Module implements HudListener {

    private final BooleanSetting info    = new BooleanSetting(EncryptedString.of("Info"), true);
    private final BooleanSetting modules = new BooleanSetting("Modules", true)
            .setDescription(EncryptedString.of("Renders module array list"));

    public HUD() {
        super(EncryptedString.of("HUD"),
                EncryptedString.of("Heads-up display"),
                -1,
                CategoryManager.GUI);
        addSettings(info, modules);
    }

    @Override
    public void onEnable() {
        eventManager.add(HudListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(HudListener.class, this);
        super.onDisable();
    }

    @Override
    public void onRenderHud(HudEvent event) {
        if (mc.currentScreen instanceof ClickGuiScreen) return;

        DrawContext ctx = event.context;
        Color ac = GuiTheme.accent();
        int accentArgb = GuiTheme.accentInt();

        // ── 1. Info Bar (top-left) ────────────────────────────────────────────
        if (info.getValue() && mc.player != null) {
            String ping = "0ms";
            if (mc.getNetworkHandler() != null) {
                PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                if (entry != null) ping = entry.getLatency() + "ms";
            }
            String fps    = mc.getCurrentFps() + " FPS";
            String server = mc.getCurrentServerEntry() == null
                    ? "Singleplayer" : mc.getCurrentServerEntry().address;

            String suffix   = "  |  " + fps + "  |  " + ping + "  |  " + server;
            int rockyW      = TextRenderer.getWidth("ROCKY");
            int suffixW     = TextRenderer.getWidth(suffix);
            int totalW      = rockyW + suffixW;

            int bx = 8, by = 8;
            int bw = totalW + 20;
            int bh = 20;

            // Shadow
            ctx.fill(bx + 2, by + 2, bx + bw + 2, by + bh + 2, GuiTheme.rgba(0, 0, 0, 50));
            // Border
            ctx.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, GuiTheme.border());
            // Background
            ctx.fill(bx, by, bx + bw, by + bh, GuiTheme.panelBg());
            // Left accent bar (3px) — same as panels
            ctx.fill(bx, by, bx + 3, by + bh, accentArgb);
            // Top accent fade
            ctx.fillGradient(bx + 3, by, bx + bw, by + 1,
                    GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 70),
                    GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 0));

            // Text
            int tx = bx + 9;
            int ty = by + 6;
            TextRenderer.drawString("ROCKY", ctx, tx, ty, accentArgb);
            TextRenderer.drawString(suffix, ctx, tx + rockyW, ty, GuiTheme.textPrimary());
        }

        // ── 2. Module Arraylist (top-right) ──────────────────────────────────
        if (modules.getValue()) {
            List<Module> enabledModules = Rocky.INSTANCE.getModuleManager().getModules().stream()
                    .filter(Module::isEnabled)
                    .sorted((a, b) -> Integer.compare(
                            TextRenderer.getWidth(b.getName()),
                            TextRenderer.getWidth(a.getName())))
                    .toList();

            int screenW  = mc.getWindow().getScaledWidth();
            int accentBarW = 3;
            int paddingX   = 8;
            int entryH     = 18;
            int gap        = 2;
            int startY     = 8;

            for (Module mod : enabledModules) {
                String name  = mod.getName().toString();
                int textW    = TextRenderer.getWidth(name);
                int entryW   = textW + paddingX * 2 + accentBarW;

                int ex = screenW - entryW - 1;
                int ey = startY;
                int er = screenW - 1;       // leave 1px screen edge gap

                // Shadow
                ctx.fill(ex + 2, ey + 2, er + 2, ey + entryH + 2, GuiTheme.rgba(0, 0, 0, 45));
                // Border
                ctx.fill(ex - 1, ey - 1, er + 1, ey + entryH + 1, GuiTheme.border());
                // Background
                ctx.fill(ex, ey, er, ey + entryH, GuiTheme.panelBg());

                // Right accent bar (3px) — mirror of panel's left bar
                ctx.fill(er - accentBarW, ey, er, ey + entryH, accentArgb);

                // Top accent fade (left-to-right, fades toward right like the panel header)
                ctx.fillGradient(ex, ey, er - accentBarW, ey + 1,
                        GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 0),
                        GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 70));

                // Module name text
                TextRenderer.drawString(name, ctx,
                        ex + paddingX, ey + 5,
                        GuiTheme.textPrimary());

                startY += entryH + gap;
            }
        }
    }
}
