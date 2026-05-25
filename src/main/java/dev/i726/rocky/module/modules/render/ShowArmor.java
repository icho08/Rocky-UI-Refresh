package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

public final class ShowArmor extends Module implements GameRenderListener {

    private final NumberSetting range = new NumberSetting(
            EncryptedString.of("Range"), 10, 100, 30, 5);

    private final BooleanSetting showDurability = new BooleanSetting(
            EncryptedString.of("Show Durability"), true)
            .setDescription(EncryptedString.of("Show remaining durability numbers"));

    private final BooleanSetting showPiece = new BooleanSetting(
            EncryptedString.of("Show Piece Label"), true)
            .setDescription(EncryptedString.of("Show H/C/L/B labels next to durability"));

    private final BooleanSetting colorWarning = new BooleanSetting(
            EncryptedString.of("Low Durability Warning"), true)
            .setDescription(EncryptedString.of("Turn red when a piece is below 10% durability"));

    public ShowArmor() {
        super(EncryptedString.of("Armor Display"),
                EncryptedString.of("Shows player armor durability above their head"),
                -1, CategoryManager.ESP);
        addSettings(range, showDurability, showPiece, colorWarning);
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

        Camera    cam    = mc.gameRenderer.getCamera();
        Vec3d     camPos = cam.getPos();
        float     tickDelta = mc.getRenderTickCounter().getTickProgress(true);

        VertexConsumerProvider.Immediate immediate =
                mc.getBufferBuilders().getEntityVertexConsumers();

        MatrixStack matrices = event.matrices;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) continue;
            if (mc.player.distanceTo(player) > range.getValue()) continue;

            String text = buildArmorText(player);
            if (text.isEmpty()) continue;

            Vec3d pos = player.getLerpedPos(tickDelta);
            double dx = pos.x - camPos.x;
            double dy = pos.y - camPos.y + player.getHeight() + 0.35;
            double dz = pos.z - camPos.z;

            boolean lowDurability = hasLowDurability(player);
            int textColor = (colorWarning.getValue() && lowDurability) ? 0xFF4444 : 0xE5E7EB;

            matrices.push();
            matrices.translate(dx, dy, dz);
            matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cam.getYaw()));
            matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cam.getPitch()));
            matrices.scale(-0.025f, -0.025f, 0.025f);

            float w = mc.textRenderer.getWidth(text) / 2f;
            mc.textRenderer.draw(
                    text, -w, 0, textColor, false,
                    matrices.peek().getPositionMatrix(),
                    immediate,
                    TextRenderer.TextLayerType.SEE_THROUGH,
                    0x50000000,
                    15728880
            );

            matrices.pop();
        }

        immediate.draw();
    }

    private String buildArmorText(PlayerEntity player) {
        EquipmentSlot[] slots  = { EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET };
        String[]        labels = { "H", "C", "L", "B" };
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < slots.length; i++) {
            ItemStack stack = player.getEquippedStack(slots[i]);
            if (stack.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            if (showPiece.getValue()) sb.append(labels[i]).append(':');
            if (showDurability.getValue()) {
                if (stack.isDamageable()) {
                    sb.append(stack.getMaxDamage() - stack.getDamage());
                } else {
                    sb.append('∞');
                }
            }
        }
        return sb.toString();
    }

    private boolean hasLowDurability(PlayerEntity player) {
        for (EquipmentSlot slot : new EquipmentSlot[]{ EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET }) {
            ItemStack s = player.getEquippedStack(slot);
            if (s.isEmpty() || !s.isDamageable()) continue;
            double ratio = (double)(s.getMaxDamage() - s.getDamage()) / s.getMaxDamage();
            if (ratio < 0.10) return true;
        }
        return false;
    }
}
