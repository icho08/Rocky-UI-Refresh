package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public final class ShowHealth extends Module implements GameRenderListener {
    private final NumberSetting range = new NumberSetting(EncryptedString.of("Range"), 10, 100, 50, 5);

    public ShowHealth() {
        super(EncryptedString.of("Health Display"),
                EncryptedString.of("Shows player health"), 
              -1, 
              CategoryManager.ESP);
        addSettings(range);
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
        if (mc.player == null || mc.world == null) return;

        MatrixStack matrices = event.matrices;
        Camera cam = mc.gameRenderer.getCamera();
        Vec3d camPos = cam.getPos();
        VertexConsumerProvider.Immediate vertexConsumers = mc.getBufferBuilders().getEntityVertexConsumers();

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) continue;
            if (mc.player.distanceTo(player) > range.getValue()) continue;

            float tickDelta = mc.getRenderTickCounter().getTickProgress(true);
            float health = player.getHealth() + player.getAbsorptionAmount();
            String healthText = String.format("%.1f", health);
            
            int color = health > 15 ? 0x22C55E : health > 10 ? 0xFBBF24 : 0xEF4444;

            matrices.push();
            
            double x = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX()) - camPos.x;
            double y = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY()) - camPos.y + player.getHeight() + 0.5;
            double z = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ()) - camPos.z;
            
            matrices.translate(x, y, z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cam.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cam.getPitch()));
            matrices.scale(-0.025f, -0.025f, 0.025f);

            float textWidth = mc.textRenderer.getWidth(healthText);
            mc.textRenderer.draw(healthText, -textWidth / 2f, 0, color, false, 
                               matrices.peek().getPositionMatrix(), vertexConsumers, 
                               net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);

            matrices.pop();
        }
        
        vertexConsumers.draw();
    }
}
