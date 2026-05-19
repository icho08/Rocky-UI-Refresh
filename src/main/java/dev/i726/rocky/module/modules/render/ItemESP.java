package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;

public final class ItemESP extends Module implements GameRenderListener {

    public enum EspMode { Box, Tracer, Both }

    private final ModeSetting<EspMode> mode = new ModeSetting<>(
            EncryptedString.of("Mode"), EspMode.Both, EspMode.class
    ).setDescription(EncryptedString.of("Render mode for item ESP"));

    private final BooleanSetting fill = new BooleanSetting(
            EncryptedString.of("Fill"), true
    ).setDescription(EncryptedString.of("Fill the bounding box"));

    private final NumberSetting maxRange = new NumberSetting(
            EncryptedString.of("Range"), 10, 256, 64, 5
    ).setDescription(EncryptedString.of("Maximum distance to show item ESP"));

    public ItemESP() {
        super(
                EncryptedString.of("ItemESP"),
                EncryptedString.of("Highlights dropped items on the ground"),
                -1,
                CategoryManager.ESP
        );
        addSettings(mode, fill, maxRange);
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
        Color outlineColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 220);
        Color fillColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40);

        double rangeSq = maxRange.getValue() * maxRange.getValue();
        EspMode currentMode = mode.getMode();
        boolean drawBox = currentMode == EspMode.Box || currentMode == EspMode.Both;
        boolean drawTracer = currentMode == EspMode.Tracer || currentMode == EspMode.Both;
        float tickDelta = event.delta;

        for (Entity entity : mc.world.getEntities()) {
            if (!(entity instanceof ItemEntity)) continue;
            if (mc.player.squaredDistanceTo(entity) > rangeSq) continue;

            Vec3d lerpedPos = entity.getLerpedPos(tickDelta);
            float hw = entity.getWidth() / 2f;
            float h = entity.getHeight();
            Box box = new Box(
                    lerpedPos.x - hw, lerpedPos.y,      lerpedPos.z - hw,
                    lerpedPos.x + hw, lerpedPos.y + h,  lerpedPos.z + hw
            );

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
