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
import java.awt.Color;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public final class ItemESP extends Module implements GameRenderListener {

    public enum EspMode { Box, Tracer, Both }

    private final ModeSetting<EspMode> mode = new ModeSetting<>(
            EncryptedString.of("Mode"), EspMode.Both, EspMode.class
    ).setDescription(EncryptedString.of("Render mode for item ESP"));

    private final BooleanSetting fill = new BooleanSetting(
            EncryptedString.of("Fill"), true
    ).setDescription(EncryptedString.of("Fill the bounding box"));

    private final NumberSetting fillOpacity = new NumberSetting(
            EncryptedString.of("Fill Opacity"), 0, 255, 40, 5
    ).setDescription(EncryptedString.of("Transparency of the box fill (0 = invisible, 255 = solid)"));

    private final NumberSetting outlineOpacity = new NumberSetting(
            EncryptedString.of("Outline Opacity"), 0, 255, 220, 5
    ).setDescription(EncryptedString.of("Transparency of the box outline (0 = invisible, 255 = solid)"));

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
        addSettings(mode, fill, fillOpacity, outlineOpacity, maxRange);
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
        if (mc == null || mc.level == null || mc.player == null) return;

        Color accent = GuiTheme.accent();
        int fA = (int) fillOpacity.getValue();
        int oA = (int) outlineOpacity.getValue();
        Color outlineColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), oA);
        Color fillColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), fA);

        double rangeSq = maxRange.getValue() * maxRange.getValue();
        EspMode currentMode = mode.getMode();
        boolean drawBox = currentMode == EspMode.Box || currentMode == EspMode.Both;
        boolean drawTracer = currentMode == EspMode.Tracer || currentMode == EspMode.Both;
        float tickDelta = event.delta;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity)) continue;
            if (mc.player.distanceToSqr(entity) > rangeSq) continue;

            Vec3 lerpedPos = entity.getPosition(tickDelta);
            float hw = entity.getBbWidth() / 2f;
            float h = entity.getBbHeight();
            AABB box = new AABB(
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
