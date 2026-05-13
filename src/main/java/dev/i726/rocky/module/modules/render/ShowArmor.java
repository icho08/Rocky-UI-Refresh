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
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public final class ShowArmor extends Module implements GameRenderListener {
    private final NumberSetting range = new NumberSetting(EncryptedString.of("Range"), 10, 100, 30, 5);

    public ShowArmor() {
        super(EncryptedString.of("Armor Display"),
                EncryptedString.of("Shows player armor"), 
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

            String armorInfo = getArmorInfo(player);
            if (armorInfo.isEmpty()) continue;

            float tickDelta = mc.getRenderTickCounter().getTickProgress(true);

            matrices.push();
            
            double x = MathHelper.lerp(tickDelta, player.lastRenderX, player.getX()) - camPos.x;
            double y = MathHelper.lerp(tickDelta, player.lastRenderY, player.getY()) - camPos.y + player.getHeight() + 0.8;
            double z = MathHelper.lerp(tickDelta, player.lastRenderZ, player.getZ()) - camPos.z;
            
            matrices.translate(x, y, z);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cam.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cam.getPitch()));
            matrices.scale(-0.025f, -0.025f, 0.025f);

            float textWidth = mc.textRenderer.getWidth(armorInfo);
            mc.textRenderer.draw(armorInfo, -textWidth / 2f, 0, 0xE5E7EB, false, 
                               matrices.peek().getPositionMatrix(), vertexConsumers, 
                               net.minecraft.client.font.TextRenderer.TextLayerType.SEE_THROUGH, 0, 15728880);

            matrices.pop();
        }
        
        vertexConsumers.draw();
    }

    private String getArmorInfo(PlayerEntity player) {
        StringBuilder info = new StringBuilder();
        ItemStack[] armor = {
            player.getInventory().getStack(3 + 36), // Helmet
            player.getInventory().getStack(2 + 36), // Chestplate
            player.getInventory().getStack(1 + 36), // Leggings
            player.getInventory().getStack(0 + 36)  // Boots
        };
        
        String[] names = {"H", "C", "L", "B"};
        
        for (int i = 0; i < armor.length; i++) {
            ItemStack stack = armor[i];
            if (!stack.isEmpty()) {
                if (info.length() > 0) info.append(" ");
                if (stack.isDamageable()) {
                    int durability = stack.getMaxDamage() - stack.getDamage();
                    info.append(names[i]).append(":").append(durability);
                } else {
                    info.append(names[i]).append(":∞");
                }
            }
        }
        
        return info.toString();
    }
}
