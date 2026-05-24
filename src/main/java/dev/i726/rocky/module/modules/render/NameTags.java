package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.events.HudListener;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TextRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.Window;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.awt.Color;

public final class NameTags extends Module implements HudListener {

    private final BooleanSetting showHealth = new BooleanSetting(
            EncryptedString.of("Health"), true)
            .setDescription(EncryptedString.of("Show player health"));

    private final BooleanSetting showPing = new BooleanSetting(
            EncryptedString.of("Ping"), true)
            .setDescription(EncryptedString.of("Show network latency"));

    private final BooleanSetting showDistance = new BooleanSetting(
            EncryptedString.of("Distance"), true)
            .setDescription(EncryptedString.of("Show distance to player in blocks"));

    private final BooleanSetting friendColor = new BooleanSetting(
            EncryptedString.of("Friend Color"), true)
            .setDescription(EncryptedString.of("Render friends' tags in green"));

    private final NumberSetting maxDist = new NumberSetting(
            EncryptedString.of("Max Distance"), 10, 128, 64, 1)
            .setDescription(EncryptedString.of("Skip players further than this many blocks"));

    public NameTags() {
        super(EncryptedString.of("Name Tags"),
                EncryptedString.of("Renders enhanced name tags above nearby players"),
                -1, CategoryManager.ESP);
        addSettings(showHealth, showPing, showDistance, friendColor, maxDist);
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
        if (mc.player == null || mc.world == null) return;
        DrawContext ctx = event.context;
        Camera cam = mc.gameRenderer.getCamera();
        Window win = mc.getWindow();

        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity target)) continue;
            if (target == mc.player) continue;

            float dist = target.distanceTo(mc.player);
            if (dist > maxDist.getValue()) continue;

            // World-to-screen projection
            Vec3d worldPos = target.getPos().add(0, target.getHeight() + 0.35, 0);
            int[] screen = worldToScreen(worldPos, cam, win);
            if (screen == null) continue;

            // Build label
            boolean isFriend = Rocky.INSTANCE.getFriendManager().isFriend(target.getUuidAsString());
            StringBuilder sb = new StringBuilder(target.getName().getString());

            if (showHealth.getValue()) {
                float hp = Math.min(target.getHealth() + target.getAbsorptionAmount(), 20f);
                sb.append(" §c").append(Math.round(hp)).append("hp");
            }
            if (showPing.getValue() && mc.getNetworkHandler() != null) {
                PlayerListEntry entry = mc.getNetworkHandler().getPlayerListEntry(target.getUuid());
                if (entry != null) sb.append(" §7").append(entry.getLatency()).append("ms");
            }
            if (showDistance.getValue()) {
                sb.append(" §8").append(Math.round(dist)).append("m");
            }

            String label = sb.toString();
            int textW = TextRenderer.getWidth(label);
            int padX = 5, padY = 3;
            int bw = textW + padX * 2;
            int bh = mc.textRenderer.fontHeight + padY * 2;
            int bx = screen[0] - bw / 2;
            int by = screen[1] - bh;

            // Background
            ctx.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, GuiTheme.border());
            ctx.fill(bx, by, bx + bw, by + bh, GuiTheme.panelBg());

            // Accent top line
            Color ac = isFriend && friendColor.getValue()
                    ? new Color(34, 197, 94)
                    : GuiTheme.accent();
            ctx.fill(bx, by, bx + bw, by + 1,
                    GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 200));

            // Text
            int nameColor = isFriend && friendColor.getValue()
                    ? GuiTheme.rgba(34, 197, 94, 255)
                    : GuiTheme.textPrimary();
            TextRenderer.drawString(label, ctx, bx + padX, by + padY, nameColor);
        }
    }

    /**
     * Projects a world-space Vec3d to screen-space pixel coordinates.
     * Returns null if the point is behind the camera.
     */
    private static int[] worldToScreen(Vec3d world, Camera cam, Window win) {
        Vec3d delta = world.subtract(cam.getPos());

        float yawRad   = (float) Math.toRadians(cam.getYaw());
        float pitchRad = (float) Math.toRadians(cam.getPitch());

        // Rotate delta by negative camera yaw (around Y axis)
        double cosY = Math.cos(yawRad),  sinY = Math.sin(yawRad);
        double rx =  delta.x * cosY + delta.z * sinY;
        double ry =  delta.y;
        double rz = -delta.x * sinY + delta.z * cosY;

        // Rotate delta by negative camera pitch (around X axis)
        double cosPi = Math.cos(-pitchRad), sinPi = Math.sin(-pitchRad);
        double ry2 = ry * cosPi - rz * sinPi;
        double rz2 = ry * sinPi + rz * cosPi;

        // rz2 is the depth along the look vector — must be positive (in front)
        if (rz2 < 0.1) return null;

        float fovRad  = (float) Math.toRadians(MinecraftClient.getInstance().options.getFov().getValue());
        float aspect  = (float) win.getScaledWidth() / win.getScaledHeight();
        float tanHalf = (float) Math.tan(fovRad / 2.0);

        int sx = (int)((rx / (rz2 * tanHalf * aspect) + 1.0) / 2.0 * win.getScaledWidth());
        int sy = (int)((-ry2 / (rz2 * tanHalf) + 1.0) / 2.0 * win.getScaledHeight());

        return new int[]{sx, sy};
    }
}
