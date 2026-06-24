package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.event.events.HudListener;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
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
 * Chams — coloured player overlay visible through walls.
 *
 * Implemented via 2D screen-space projection (same pattern as NameTags / PlayerESP)
 * so it works in MC 26.1.2 where the 3D RenderType line/fill API was removed.
 *
 * Because 2D projection ignores world geometry, the overlay is inherently
 * "through-walls" regardless of block occlusion — which is exactly the Chams effect.
 *
 * Phase 1 – GameRenderListener : project player AABB corners to screen coords.
 * Phase 2 – HudListener        : draw a filled coloured box over each player.
 */
public final class Chams extends Module implements GameRenderListener, HudListener {

    private static final class ChamsData {
        final int minX, minY, maxX, maxY, fillColor, outlineColor;
        ChamsData(int x1, int y1, int x2, int y2, int fill, int outline) {
            minX = x1; minY = y1; maxX = x2; maxY = y2;
            fillColor = fill; outlineColor = outline;
        }
    }

    private final List<ChamsData> pending = new ArrayList<>();

    public final BooleanSetting throughWalls = new BooleanSetting(
            EncryptedString.of("Through Walls"), true)
            .setDescription(EncryptedString.of("See players through solid blocks"));

    public final NumberSetting opacity = new NumberSetting(
            EncryptedString.of("Opacity"), 0, 255, 150, 5)
            .setDescription(EncryptedString.of("Fill opacity of the chams overlay"));

    public Chams() {
        super(EncryptedString.of("Chams"),
                EncryptedString.of("See players through walls"),
                -1,
                CategoryManager.ESP);
        addSettings(throughWalls, opacity);
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

    /** Legacy API used by some modules that call getColor() directly. */
    public Color getColor() {
        Color a = GuiTheme.accent();
        return new Color(a.getRed(), a.getGreen(), a.getBlue(), opacity.getValueInt());
    }

    // ── Phase 1: project player AABB corners → 2D bounding rect ──────────

    @Override
    public void onGameRender(GameRenderEvent event) {
        pending.clear();
        if (mc == null || mc.level == null || mc.player == null) return;

        Camera cam    = mc.gameRenderer.getMainCamera();
        Vec3   camPos = cam.position();
        int    winW   = mc.getWindow().getGuiScaledWidth();
        int    winH   = mc.getWindow().getGuiScaledHeight();

        Matrix4f viewRot     = event.matrices.last().pose();
        double   fovYRad     = Math.toRadians(mc.options.fov().get());
        double   tanHalfFovY = Math.tan(fovYRad / 2.0);
        double   aspect      = (double) winW / winH;

        Color ac      = GuiTheme.accent();
        int   fillA   = opacity.getValueInt();
        int   fillCol = GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(), fillA);
        int   outCol  = GuiTheme.rgba(ac.getRed(), ac.getGreen(), ac.getBlue(),
                Math.min(255, fillA + 60));

        for (AbstractClientPlayer player : mc.level.players()) {
            if (player == mc.player) continue;

            Vec3  lerpedPos = player.getPosition(event.delta);
            float hw        = player.getBbWidth() / 2f;
            float h         = player.getBbHeight();
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
                int[] sc = RenderUtils.projectToScreen(corner, viewRot, camPos, winW, winH, tanHalfFovY, aspect);
                if (sc == null) continue;
                any = true;
                if (sc[0] < minSX) minSX = sc[0];
                if (sc[1] < minSY) minSY = sc[1];
                if (sc[0] > maxSX) maxSX = sc[0];
                if (sc[1] > maxSY) maxSY = sc[1];
            }

            if (!any) continue;
            pending.add(new ChamsData(minSX, minSY, maxSX, maxSY, fillCol, outCol));
        }
    }

    // ── Phase 2: draw chams overlay on the HUD ───────────────────────────

    @Override
    public void onRenderHud(HudEvent event) {
        if (pending.isEmpty()) return;
        GuiGraphicsExtractor ctx = event.context;

        for (ChamsData d : pending) {
            // Filled box (the "chams" coloured silhouette)
            ctx.fill(d.minX, d.minY, d.maxX, d.maxY, d.fillColor);
            // Outline for clarity
            RenderUtils.drawRect2D(ctx, d.minX, d.minY, d.maxX, d.maxY, d.outlineColor);
        }
    }
}
