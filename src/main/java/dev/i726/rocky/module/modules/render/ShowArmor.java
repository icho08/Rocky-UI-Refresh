package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.event.events.HudListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Two-phase armor display rendering (mirrors the NameTags pattern).
 *
 * Phase 1 (GameRenderListener): project each player's head position to screen
 *   coords using the view-rotation matrix + manual perspective divide.
 *
 * Phase 2 (HudListener): draw armor text at the collected 2-D positions.
 */
public final class ShowArmor extends Module implements GameRenderListener, HudListener {

    private static final class ArmorData {
        final int    screenX, screenY;
        final String text;
        final boolean lowDurability;
        ArmorData(int x, int y, String t, boolean low) {
            screenX = x; screenY = y; text = t; lowDurability = low;
        }
    }

    private final List<ArmorData> pending = new ArrayList<>();

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
        eventManager.add(HudListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(GameRenderListener.class, this);
        eventManager.remove(HudListener.class, this);
        pending.clear();
        super.onDisable();
    }

    // ── Phase 1: world → screen projection ──────────────────────────────────

    @Override
    public void onGameRender(GameRenderEvent event) {
        pending.clear();
        if (mc.player == null || mc.world == null) return;

        Camera cam    = mc.gameRenderer.getCamera();
        Vec3d  camPos = cam.getPos();
        int    winW   = mc.getWindow().getScaledWidth();
        int    winH   = mc.getWindow().getScaledHeight();
        float  maxD   = (float) range.getValue();

        Matrix4f viewRot = event.matrices.peek().getPositionMatrix();

        double fovYRad     = Math.toRadians(mc.options.getFov().getValue());
        double tanHalfFovY = Math.tan(fovYRad / 2.0);
        double aspect      = (double) winW / winH;

        for (PlayerEntity player : mc.world.getPlayers()) {
            if (player == mc.player || !player.isAlive()) continue;
            if (mc.player.distanceTo(player) > maxD) continue;

            String text = buildArmorText(player);
            if (text.isEmpty()) continue;

            Vec3d lerp = player.getLerpedPos(event.delta);
            float rx = (float)(lerp.x - camPos.x);
            float ry = (float)(lerp.y + player.getHeight() + 0.35 - camPos.y);
            float rz = (float)(lerp.z - camPos.z);

            Vector4f eye = viewRot.transform(new Vector4f(rx, ry, rz, 1f));

            if (eye.z >= -0.001f) continue;

            float fwdDepth = -eye.z;

            float ndcX = (float)(eye.x / (fwdDepth * tanHalfFovY * aspect));
            float ndcY = (float)(eye.y / (fwdDepth * tanHalfFovY));

            if (ndcX < -1.15f || ndcX > 1.15f || ndcY < -1.15f || ndcY > 1.15f) continue;

            int screenX = (int)((ndcX + 1f) * 0.5f * winW);
            int screenY = (int)((1f - ndcY) * 0.5f * winH);

            boolean low = hasLowDurability(player);
            pending.add(new ArmorData(screenX, screenY, text, low));
        }
    }

    // ── Phase 2: draw on HUD ─────────────────────────────────────────────────

    @Override
    public void onRenderHud(HudEvent event) {
        DrawContext ctx = event.context;
        for (ArmorData data : pending) {
            int color = (colorWarning.getValue() && data.lowDurability) ? 0xFFFF4444 : 0xFFE5E7EB;
            int tw = mc.textRenderer.getWidth(data.text);
            int tx = data.screenX - tw / 2;
            int ty = data.screenY - mc.textRenderer.fontHeight;

            ctx.fill(tx - 2, ty - 1, tx + tw + 2, ty + mc.textRenderer.fontHeight + 1, 0x80000000);
            ctx.drawText(mc.textRenderer, data.text, tx, ty, color, false);
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

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
