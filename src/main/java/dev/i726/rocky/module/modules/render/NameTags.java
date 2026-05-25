package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.Rocky;
import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.event.events.HudListener;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * Two-phase nametag rendering.
 *
 * Phase 1 (GameRenderListener): project world positions to screen using
 *   the view-rotation matrix from event.matrices (built in WorldRendererMixin as
 *   conjugate of camera.getRotation()) plus a manual perspective divide with the
 *   player's FOV setting. This fires at the end of the world-render pass when the
 *   camera is fully set up, so tags lock correctly to player positions.
 *
 * Phase 2 (HudListener): draw the collected 2-D tag data onto the HUD.
 */
public final class NameTags extends Module implements GameRenderListener, HudListener {

    private static final class TagData {
        final int    screenX, screenY;
        final String label;
        final boolean isFriend;
        TagData(int x, int y, String l, boolean f) {
            screenX = x; screenY = y; label = l; isFriend = f;
        }
    }

    private final List<TagData> pendingTags = new ArrayList<>();

    private final BooleanSetting showHealth = new BooleanSetting(
            EncryptedString.of("Health"), true)
            .setDescription(EncryptedString.of("Show player health"));

    private final BooleanSetting showPing = new BooleanSetting(
            EncryptedString.of("Ping"), true)
            .setDescription(EncryptedString.of("Show network latency"));

    private final BooleanSetting showDistance = new BooleanSetting(
            EncryptedString.of("Distance"), true)
            .setDescription(EncryptedString.of("Show distance in blocks"));

    private final BooleanSetting friendColor = new BooleanSetting(
            EncryptedString.of("Friend Color"), true)
            .setDescription(EncryptedString.of("Render friends tags in green"));

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
        eventManager.add(GameRenderListener.class, this);
        eventManager.add(HudListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(GameRenderListener.class, this);
        eventManager.remove(HudListener.class, this);
        pendingTags.clear();
        super.onDisable();
    }

    // ── Phase 1: world → screen projection ──────────────────────────────────

    @Override
    public void onGameRender(GameRenderEvent event) {
        pendingTags.clear();
        if (mc.player == null || mc.world == null) return;

        Camera cam    = mc.gameRenderer.getCamera();
        Vec3d  camPos = cam.getPos();
        int    winW   = mc.getWindow().getScaledWidth();
        int    winH   = mc.getWindow().getScaledHeight();
        float  maxD   = (float) maxDist.getValue();

        // event.matrices.peek().getPositionMatrix() is the view-rotation matrix
        // (conjugate of camera.getRotation() applied to an identity MatrixStack).
        // Transforming a camera-relative world vector with it yields eye space.
        Matrix4f viewRot = event.matrices.peek().getPositionMatrix();

        // FOV from player settings — used for the perspective divide.
        double fovYRad     = Math.toRadians(mc.options.getFov().getValue());
        double tanHalfFovY = Math.tan(fovYRad / 2.0);
        double aspect      = (double) winW / winH;

        for (var entity : mc.world.getEntities()) {
            if (!(entity instanceof PlayerEntity target)) continue;
            if (target == mc.player) continue;

            float dist = target.distanceTo(mc.player);
            if (dist > maxD) continue;

            Vec3d lerp = target.getLerpedPos(event.delta);
            float rx = (float)(lerp.x - camPos.x);
            float ry = (float)(lerp.y + target.getHeight() + 0.35 - camPos.y);
            float rz = (float)(lerp.z - camPos.z);

            // Transform to eye space using the view-rotation matrix.
            // w component stays 1 (no translation in this matrix).
            Vector4f eye = viewRot.transform(new Vector4f(rx, ry, rz, 1f));

            // In this coordinate frame, "forward" is the -Z axis (OpenGL eye space).
            // eye.z < 0 means the point is in front of the camera.
            if (eye.z >= -0.001f) continue;

            float fwdDepth = -eye.z;   // positive depth

            float ndcX = (float)(eye.x  / (fwdDepth * tanHalfFovY * aspect));
            float ndcY = (float)(eye.y  / (fwdDepth * tanHalfFovY));

            // Cull if off-screen (with a small margin)
            if (ndcX < -1.15f || ndcX > 1.15f || ndcY < -1.15f || ndcY > 1.15f) continue;

            int screenX = (int)((ndcX + 1f) * 0.5f * winW);
            int screenY = (int)((1f - ndcY) * 0.5f * winH);   // Y flipped: NDC +Y = top

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

            pendingTags.add(new TagData(screenX, screenY, sb.toString(), isFriend));
        }
    }

    // ── Phase 2: draw collected tags on the HUD ──────────────────────────────

    @Override
    public void onRenderHud(HudEvent event) {
        DrawContext ctx = event.context;
        for (TagData tag : pendingTags) {
            int textW = TextRenderer.getWidth(tag.label);
            int padX  = 5, padY = 3;
            int bw    = textW + padX * 2;
            int bh    = mc.textRenderer.fontHeight + padY * 2;
            int bx    = tag.screenX - bw / 2;
            int by    = tag.screenY - bh;

            ctx.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, GuiTheme.border());
            ctx.fill(bx, by, bx + bw, by + bh, GuiTheme.panelBg());

            Color ac = tag.isFriend && friendColor.getValue()
                    ? new Color(34, 197, 94) : GuiTheme.accent();
            ctx.fill(bx, by, bx + bw, by + 1,
                    GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), 200));

            int col = tag.isFriend && friendColor.getValue()
                    ? GuiTheme.rgba(34, 197, 94, 255) : GuiTheme.textPrimary();
            TextRenderer.drawString(tag.label, ctx, bx + padX, by + padY, col);
        }
    }
}
