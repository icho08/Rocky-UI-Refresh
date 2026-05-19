package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.util.math.Box;

import java.awt.Color;
import java.util.List;

public final class PlayerESP extends Module implements GameRenderListener {

    public enum EspMode { Box, Tracer, Both }

    private final ModeSetting<EspMode> mode = new ModeSetting<>(
            EncryptedString.of("Mode"), EspMode.Both, EspMode.class
    ).setDescription(EncryptedString.of("Render mode for player ESP"));

    private final BooleanSetting fill = new BooleanSetting(
            EncryptedString.of("Fill"), true
    ).setDescription(EncryptedString.of("Fill the bounding box"));

    public PlayerESP() {
        super(
                EncryptedString.of("PlayerESP"),
                EncryptedString.of("Highlights players through walls"),
                -1,
                CategoryManager.ESP
        );
        addSettings(mode, fill);
    }

    @Override
    public void onEnable() {
        eventManager.add(GameRenderListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(GameRenderListener.class, this);
        super.onDisable();
    }

    @Override
    public void onGameRender(GameRenderEvent event) {
        if (mc == null || mc.world == null || mc.player == null) return;

        Color accent = GuiTheme.accent();
        Color fillColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40);
        Color outlineColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 220);

        List<AbstractClientPlayerEntity> players = mc.world.getPlayers();
        EspMode currentMode = mode.getMode();

        boolean drawBox = currentMode == EspMode.Box || currentMode == EspMode.Both;
        boolean drawTracer = currentMode == EspMode.Tracer || currentMode == EspMode.Both;

        for (AbstractClientPlayerEntity player : players) {
            if (player == mc.player) continue;

            Box box = player.getBoundingBox();

            if (drawBox) {
                RenderUtils.drawOutlinedBox(event.matrices, box, outlineColor);
                if (fill.getValue()) {
                    RenderUtils.renderFilledBox(event.matrices,
                            box.minX, box.minY, box.minZ,
                            box.maxX, box.maxY, box.maxZ,
                            fillColor);
                }
            }

            if (drawTracer) {
                RenderUtils.drawTracer(event.matrices, box.getCenter(), outlineColor);
            }
        }
    }
}
