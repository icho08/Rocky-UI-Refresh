package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;

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
        if (mc == null || mc.player == null || mc.world == null) return;

        MatrixStack matrices = event.matrices;
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d camPos = cam.getPos();
        VertexConsumerProvider.Immediate vertexConsumers = mc.getBufferBuilders().getEntityVertexConsumers();
        float tickDelta = mc.getRenderTickCounter().getTickProgress(true);

        for (PlayerEntity player : mc.world.getPlayers()) {
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

            Vec3d pos = player.getLerpedPos(tickDelta);
            double x = pos.x - camPos.x;
            double y = pos.y - camPos.y + player.getHeight() + yOffset.getValue();
            double z = pos.z - camPos.z;

            matrices.push();
            matrices.translate(x, y, z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cam.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cam.getPitch()));
            matrices.scale(-0.025f, -0.025f, 0.025f);

            float textWidth = mc.textRenderer.getWidth(label);
            mc.textRenderer.draw(label, -textWidth / 2f, 0, textColor, false,
                    matrices.peek().getPositionMatrix(), vertexConsumers,
                    net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);

            matrices.pop();
        }

        vertexConsumers.draw();
    }
}
