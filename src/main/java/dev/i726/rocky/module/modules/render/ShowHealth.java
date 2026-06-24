package dev.i726.rocky.module.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import java.awt.Color;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public final class ShowHealth extends Module implements GameRenderListener {

    private final BooleanSetting absorption = new BooleanSetting(
            EncryptedString.of("Absorption"), true
    ).setDescription(EncryptedString.of("Include absorption hearts in the health display"));

    private final NumberSetting range = new NumberSetting(
            EncryptedString.of("Range"), 5, 100, 30, 5
    ).setDescription(EncryptedString.of("Maximum distance to show health labels"));

    private final NumberSetting yOffset = new NumberSetting(
            EncryptedString.of("Y Offset"), -2.0, 2.0, 0.3, 0.05
    ).setDescription(EncryptedString.of("Move the text up (+) or down (-) relative to the head"));

    public ShowHealth() {
        super(
                EncryptedString.of("ShowHealth"),
                EncryptedString.of("Displays health values above player heads"),
                -1,
                CategoryManager.ESP
        );
        addSettings(absorption, range, yOffset);
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
        if (mc == null || mc.player == null || mc.level == null) return;

        PoseStack matrices = event.matrices;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 camPos = cam.position();
        MultiBufferSource.BufferSource vertexConsumers = mc.renderBuffers().bufferSource();
        float tickDelta = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        for (Player player : mc.level.players()) {
            if (player == mc.player || !player.isAlive()) continue;
            if (mc.player.distanceTo(player) > range.getValue()) continue;

            float health = player.getHealth();
            float abs = absorption.getValue() ? player.getAbsorptionAmount() : 0f;
            float total = health + abs;
            float maxHp = player.getMaxHealth();

            float ratio = maxHp > 0 ? Math.min(total / maxHp, 1f) : 0f;
            int r = (int) ((1f - ratio) * 255);
            int g = (int) (ratio * 200);
            int textColor = new Color(r, g, 40, 255).getRGB();

            String label = String.format("%.1f", total);

            Vec3 pos = player.getPosition(tickDelta);
            double x = pos.x - camPos.x;
            double y = pos.y - camPos.y + player.getBbHeight() + yOffset.getValue();
            double z = pos.z - camPos.z;

            matrices.pushPose();
            matrices.translate(x, y, z);
            matrices.mulPose(Axis.YP.rotationDegrees(-cam.yRot()));
            matrices.mulPose(Axis.XP.rotationDegrees(cam.xRot()));
            matrices.scale(-0.025f, -0.025f, 0.025f);

            float textWidth = mc.font.width(label);
            mc.font.drawInBatch(label, -textWidth / 2f, 0, textColor, false,
                    matrices.last().pose(), vertexConsumers,
                    net.minecraft.client.gui.Font.DisplayMode.SEE_THROUGH, 0, 15728880);

            matrices.popPose();
        }

        vertexConsumers.endBatch();
    }
}
