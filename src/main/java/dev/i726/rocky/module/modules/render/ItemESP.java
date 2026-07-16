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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Item ESP using 2D screen-space projection.
 *
 * Two-phase rendering (GameRenderListener → collect, HudListener → draw).
 * Works in MC 26.1.2 where RenderType.lines was removed.
 */
public final class ItemESP extends Module implements GameRenderListener, HudListener {

    public enum EspMode { Box, Tracer, Both }

    private static final class EspData {
        final int minX, minY, maxX, maxY, outlineColor, fillColor;
        final boolean doFill, doTracer;
        final int originX, originY;
        EspData(int x1, int y1, int x2, int y2, int outline, int fill,
                boolean doFill, boolean doTracer, int ox, int oy) {
            minX = x1; minY = y1; maxX = x2; maxY = y2;
            outlineColor = outline; fillColor = fill;
            this.doFill = doFill; this.doTracer = doTracer;
            originX = ox; originY = oy;
        }
    }

    private final List<EspData> pending = new ArrayList<>();
    private int screenOriginX, screenOriginY;

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

    // ── Phase 1: project item AABB corners → 2D bounding rect ────────────

    @Override
    public void onGameRender(GameRenderEvent event) {
        pending.clear();
        if (mc == null || mc.level == null || mc.player == null) return;

        Camera cam    = mc.gameRenderer.getMainCamera();
        Vec3   camPos = cam.position();
        int    winW   = mc.getWindow().getGuiScaledWidth();
        int    winH   = mc.getWindow().getGuiScaledHeight();

        screenOriginX = winW / 2;
        screenOriginY = winH;

        Matrix4f viewRot  = event.matrices.last().pose();
        Matrix4f projMat  = event.projMatrix;
        double   rangeSq     = maxRange.getValue() * maxRange.getValue();

        Color  accent  = GuiTheme.accent();
        int    fA      = (int) fillOpacity.getValue();
        int    oA      = (int) outlineOpacity.getValue();
        int    fillCol = GuiTheme.rgba(accent.getRed(), accent.getGreen(), accent.getBlue(), fA);
        int    outCol  = GuiTheme.rgba(accent.getRed(), accent.getGreen(), accent.getBlue(), oA);

        EspMode currentMode = mode.getMode();
        boolean drawBox    = currentMode == EspMode.Box    || currentMode == EspMode.Both;
        boolean drawTracer = currentMode == EspMode.Tracer || currentMode == EspMode.Both;

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (!(entity instanceof ItemEntity)) continue;
            if (mc.player.distanceToSqr(entity) > rangeSq) continue;

            Vec3  lerpedPos = entity.getPosition(event.delta);
            float hw        = entity.getBbWidth() / 2f;
            float h         = entity.getBbHeight();
            AABB  box       = new AABB(
                    lerpedPos.x - hw, lerpedPos.y,     lerpedPos.z - hw,
                    lerpedPos.x + hw, lerpedPos.y + h, lerpedPos.z + hw
            );

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
            boolean any = false;

            for (Vec3 corner : corners) {
                int[] sc = RenderUtils.projectToScreen(corner, viewRot, projMat, camPos, winW, winH);
                if (sc == null) continue;
                any = true;
                if (sc[0] < minSX) minSX = sc[0];
                if (sc[1] < minSY) minSY = sc[1];
                if (sc[0] > maxSX) maxSX = sc[0];
                if (sc[1] > maxSY) maxSY = sc[1];
            }

            if (!any) continue;

            int[] centre = RenderUtils.projectToScreen(box.getCenter(), viewRot, projMat, camPos, winW, winH);
            int cx = centre != null ? centre[0] : (minSX + maxSX) / 2;
            int cy = centre != null ? centre[1] : (minSY + maxSY) / 2;

            pending.add(new EspData(minSX, minSY, maxSX, maxSY, outCol, fillCol,
                    fill.getValue() && drawBox, drawTracer && drawBox, cx, cy));
        }
    }

    // ── Phase 2: draw on the HUD ──────────────────────────────────────────

    @Override
    public void onRenderHud(HudEvent event) {
        if (pending.isEmpty()) return;
        GuiGraphicsExtractor ctx = event.context;
        EspMode currentMode = mode.getMode();
        boolean drawBox    = currentMode == EspMode.Box    || currentMode == EspMode.Both;
        boolean drawTracer = currentMode == EspMode.Tracer || currentMode == EspMode.Both;

        for (EspData d : pending) {
            if (drawBox) {
                RenderUtils.drawRect2D(ctx, d.minX, d.minY, d.maxX, d.maxY, d.outlineColor);
                if (d.doFill) ctx.fill(d.minX + 1, d.minY + 1, d.maxX, d.maxY, d.fillColor);
            }
            if (drawTracer) {
                RenderUtils.drawLine2D(ctx, screenOriginX, screenOriginY, d.originX, d.originY, d.outlineColor);
            }
        }
    }
}
