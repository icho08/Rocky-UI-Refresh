package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.event.events.HudListener;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.ModeSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RenderUtils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Player ESP using 2D screen-space bounding boxes.
 *
 * Two-phase rendering (same pattern as NameTags / Tracers):
 *   Phase 1 – GameRenderListener: project the 8 AABB corners to screen,
 *              compute the min/max 2-D bounding rect.
 *   Phase 2 – HudListener: draw the outline (and optional fill) rect.
 *
 * Works in MC 26.1.2 where RenderType.lines was removed.
 */
public final class PlayerESP extends Module implements GameRenderListener, HudListener {

    public enum EspMode { Box, Tracer, Both }

    private static final class EspData {
        final int minX, minY, maxX, maxY;
        final int outlineColor, fillColor;
        final boolean doFill, doTracer;
        final int tracerX, tracerY;
        EspData(int x1, int y1, int x2, int y2,
                int outline, int fill, boolean doFill,
                boolean doTracer, int tx, int ty) {
            minX = x1; minY = y1; maxX = x2; maxY = y2;
            outlineColor = outline; fillColor = fill;
            this.doFill = doFill; this.doTracer = doTracer;
            tracerX = tx; tracerY = ty;
        }
    }

    private final List<EspData> pending = new ArrayList<>();
    private int originX, originY;

    private final ModeSetting<EspMode> mode = new ModeSetting<>(
            EncryptedString.of("Mode"), EspMode.Both, EspMode.class
    ).setDescription(EncryptedString.of("Render mode for player ESP"));

    private final BooleanSetting fill = new BooleanSetting(
            EncryptedString.of("Fill"), true
    ).setDescription(EncryptedString.of("Fill the bounding box"));

    private final NumberSetting fillOpacity = new NumberSetting(
            EncryptedString.of("Fill Opacity"), 0, 255, 40, 5
    ).setDescription(EncryptedString.of("Transparency of the box fill (0 = invisible, 255 = solid)"));

    private final NumberSetting outlineOpacity = new NumberSetting(
            EncryptedString.of("Outline Opacity"), 0, 255, 220, 5
    ).setDescription(EncryptedString.of("Transparency of the box outline (0 = invisible, 255 = solid)"));

    public PlayerESP() {
        super(
                EncryptedString.of("PlayerESP"),
                EncryptedString.of("Highlights players through walls"),
                -1,
                CategoryManager.ESP
        );
        addSettings(mode, fill, fillOpacity, outlineOpacity);
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

    // ── Phase 1: project AABB corners → 2D bounding rect ─────────────────

    @Override
    public void onGameRender(GameRenderEvent event) {
        pending.clear();
        if (mc == null || mc.level == null || mc.player == null) return;

        Camera cam    = mc.gameRenderer.getMainCamera();
        Vec3   camPos = cam.position();
        int    winW   = mc.getWindow().getGuiScaledWidth();
        int    winH   = mc.getWindow().getGuiScaledHeight();

        originX = winW / 2;
        originY = winH;

        Matrix4f viewRot     = event.matrices.last().pose();
        double   fovYRad     = Math.toRadians(mc.options.fov().get());
        double   tanHalfFovY = Math.tan(fovYRad / 2.0);
        double   aspect      = (double) winW / winH;

        Color   accent  = GuiTheme.accent();
        int     fA      = (int) fillOpacity.getValue();
        int     oA      = (int) outlineOpacity.getValue();
        int     fillCol = GuiTheme.rgba(accent.getRed(), accent.getGreen(), accent.getBlue(), fA);
        int     outCol  = GuiTheme.rgba(accent.getRed(), accent.getGreen(), accent.getBlue(), oA);

        EspMode currentMode = mode.getMode();
        boolean drawBox    = currentMode == EspMode.Box    || currentMode == EspMode.Both;
        boolean drawTracer = currentMode == EspMode.Tracer || currentMode == EspMode.Both;

        for (AbstractClientPlayer player : mc.level.players()) {
            if (player == mc.player) continue;

            Vec3  lerpedPos = player.getPosition(event.delta);
            float hw        = player.getBbWidth() / 2f;
            float h         = player.getBbHeight();
            AABB  box       = new AABB(
                    lerpedPos.x - hw, lerpedPos.y,     lerpedPos.z - hw,
                    lerpedPos.x + hw, lerpedPos.y + h, lerpedPos.z + hw
            );

            // Project all 8 AABB corners to screen
            Vec3[] corners = {
                new Vec3(box.minX, box.minY, box.minZ),
                new Vec3(box.maxX, box.minY, box.minZ),
                new Vec3(box.minX, box.maxY, box.minZ),
                new Vec3(box.maxX, box.maxY, box.minZ),
                new Vec3(box.minX, box.minY, box.maxZ),
                new Vec3(box.maxX, box.minY, box.maxZ),
                new Vec3(box.minX, box.maxY, box.maxZ),
                new Vec3(box.maxX, box.maxY, box.maxZ),
            };

            int minSX = Integer.MAX_VALUE, minSY = Integer.MAX_VALUE;
            int maxSX = Integer.MIN_VALUE, maxSY = Integer.MIN_VALUE;
            boolean anyVisible = false;

            for (Vec3 corner : corners) {
                int[] sc = RenderUtils.projectToScreen(corner, viewRot, camPos, winW, winH, tanHalfFovY, aspect);
                if (sc == null) continue;
                anyVisible = true;
                if (sc[0] < minSX) minSX = sc[0];
                if (sc[1] < minSY) minSY = sc[1];
                if (sc[0] > maxSX) maxSX = sc[0];
                if (sc[1] > maxSY) maxSY = sc[1];
            }

            if (!anyVisible) continue;

            // Centre for tracer
            int[] centre = RenderUtils.projectToScreen(
                    box.getCenter(), viewRot, camPos, winW, winH, tanHalfFovY, aspect);
            int tx = centre != null ? centre[0] : (minSX + maxSX) / 2;
            int ty = centre != null ? centre[1] : (minSY + maxSY) / 2;

            pending.add(new EspData(minSX, minSY, maxSX, maxSY, outCol, fillCol,
                    fill.getValue() && drawBox, drawTracer, tx, ty));
        }
    }

    // ── Phase 2: draw ESP elements on the HUD ────────────────────────────

    @Override
    public void onRenderHud(HudEvent event) {
        if (pending.isEmpty()) return;
        GuiGraphicsExtractor ctx = event.context;

        for (EspData d : pending) {
            // Outline box
            RenderUtils.drawRect2D(ctx, d.minX, d.minY, d.maxX, d.maxY, d.outlineColor);

            // Fill
            if (d.doFill) {
                ctx.fill(d.minX + 1, d.minY + 1, d.maxX, d.maxY, d.fillColor);
            }

            // Tracer from bottom-centre to entity centre
            if (d.doTracer) {
                RenderUtils.drawLine2D(ctx, originX, originY, d.tracerX, d.tracerY, d.outlineColor);
            }
        }
    }
}
