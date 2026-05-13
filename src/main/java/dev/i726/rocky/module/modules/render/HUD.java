package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.events.HudListener;
import dev.i726.rocky.gui.ClickGuiScreen;
import dev.i726.rocky.module.Category;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.modules.client.ClickGUI;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RenderUtils;
import dev.i726.rocky.utils.TextRenderer;
import dev.i726.rocky.utils.Utils;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;

import java.awt.*;
import java.util.List;

public final class HUD extends Module implements HudListener {
        private static final CharSequence hydrogen = EncryptedString.of("Rocky |");
        private final BooleanSetting info = new BooleanSetting(EncryptedString.of("Info"), true);
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

                DrawContext context = event.context;
                final List<Module> enabledModules = Rocky.INSTANCE.getModuleManager().getModules().stream()
                                .filter(Module::isEnabled)
                                .sorted((m1, m2) -> Integer.compare(TextRenderer.getWidth(m2.getName()), TextRenderer.getWidth(m1.getName())))
                                .toList();

                Color accentStart = new Color(99, 102, 241);
                Color accentEnd = new Color(139, 92, 246);

                // 1. Info Bar
                if (info.getValue() && mc.player != null) {
                        String ping = "0ms";
                        if (mc.getNetworkHandler() != null) {
                                PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
                                if (entry != null) ping = entry.getLatency() + "ms";
                        }
                        String fps = mc.getCurrentFps() + " FPS";
                        String server = mc.getCurrentServerEntry() == null ? "Singleplayer" : mc.getCurrentServerEntry().address;
                        
                        String fullText = "ROCKY | " + fps + " | " + ping + " | " + server;
                        int totalWidth = TextRenderer.getWidth(fullText) + 30;
                        
                        // RenderUtils.renderRoundedQuad(context, new Color(10, 10, 20, 180), 10, 10, 10 + totalWidth, 40, 8, 15);
                        // RenderUtils.renderRoundedOutline(context, new Color(255, 255, 255, 20), 10, 10, 10 + totalWidth, 40, 8, 8, 8, 8, 1, 15);
                        context.fill(10, 10, 10 + totalWidth, 40, new Color(10, 10, 20, 180).getRGB());
                        
                        // Gradient "ROCKY" text
                        TextRenderer.drawString("ROCKY", context, 20, 16, accentStart.getRGB());
                        TextRenderer.drawString(" | " + fps + " | " + ping + " | " + server, context, 20 + TextRenderer.getWidth("ROCKY"), 16, Color.WHITE.getRGB());
                }

                // 2. Module List (Waterfall)
                if (modules.getValue()) {
                        int offset = 50;
                        int screenWidth = mc.getWindow().getScaledWidth();
                        
                        for (Module module : enabledModules) {
                                String name = module.getName().toString();
                                int modWidth = TextRenderer.getWidth(name);
                                int x = screenWidth * 2 - modWidth - 30;
                                
                                // Background
                                // RenderUtils.renderRoundedQuad(context, new Color(10, 10, 20, 150), x - 10, offset, screenWidth * 2, offset + 26, 0, 0, 0, 0, 1);
                                context.fill(x - 10, offset, screenWidth * 2, offset + 26, new Color(10, 10, 20, 150).getRGB());
                                
                                // Right accent bar
                                // RenderUtils.renderGradientRoundedQuad(context, accentStart, accentEnd, screenWidth * 2 - 4, offset, screenWidth * 2, offset + 26, 0, 1);
                                context.fill(screenWidth * 2 - 4, offset, screenWidth * 2, offset + 26, accentStart.getRGB());
                                
                                // Text
                                TextRenderer.drawString(name, context, x, offset + 4, Color.WHITE.getRGB());
                                
                                offset += 28;
                        }
                }
        }
}