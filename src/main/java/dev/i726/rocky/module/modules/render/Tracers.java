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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Draws 2D tracer lines from the screen centre to nearby entities.
 *
 * Two-phase rendering (same pattern as NameTags):
 *   Phase 1 – GameRenderListener: project world positions to screen coords.
 *   Phase 2 – HudListener: draw the collected lines onto the HUD.
 *
 * Avoids all MC 26.1.2 3D-rendering API changes (RenderType.lines removed).
 */
public final class Tracers extends Module implements GameRenderListener, HudListener {

    private static final class TracerData {
        final int sx, sy;
        final int color;
        TracerData(int sx, int sy, int color) { this.sx = sx; this.sy = sy; this.color = color; }
    }

    private final List<TracerData> pending = new ArrayList<>();
    private int originX, originY;

    private final BooleanSetting players = new BooleanSetting(
            EncryptedString.of("Players"), true
    ).setDescription(EncryptedString.of("Draw tracers to other players"));

    private final BooleanSetting mobs = new BooleanSetting(
            EncryptedString.of("Mobs"), true
    ).setDescription(EncryptedString.of("Draw tracers to mobs"));

    private final BooleanSetting hostilesOnly = new BooleanSetting(
            EncryptedString.of("Hostiles Only"), true
    ).setDescription(EncryptedString.of("Only draw tracers to hostile mobs"));

    private final BooleanSetting items = new BooleanSetting(
            EncryptedString.of("Items"), false
    ).setDescription(EncryptedString.of("Draw tracers to dropped items"));

    private final NumberSetting maxRange = new NumberSetting(
            EncryptedString.of("Range"), 10, 512, 128, 8
    ).setDescription(EncryptedString.of("Maximum tracer render distance"));

    public Tracers() {
        super(
                EncryptedString.of("Tracers"),
                EncryptedString.of("Draws lines to nearby entities"),
                -1,
                CategoryManager.ESP
        );
        addSettings(players, mobs, hostilesOnly, items, maxRange);
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

    // ── Phase 1: project world positions → screen ─────────────────────────

    @Override
    public void onGameRender(GameRenderEvent event) {
        pending.clear();
        if (mc == null || mc.level == null || mc.player == null) return;

        Camera cam    = mc.gameRenderer.getMainCamera();
        Vec3   camPos = cam.position();
        int    winW   = mc.getWindow().getGuiScaledWidth();
        int    winH   = mc.getWindow().getGuiScaledHeight();

        originX = winW / 2;
        originY = winH;      // tracers originate from the bottom-centre of the screen

        Matrix4f viewRot  = event.matrices.last().pose();
        Matrix4f projMat  = event.projMatrix;
        double   rangeSq  = maxRange.getValue() * maxRange.getValue();

        Color accent = GuiTheme.accent();
        int   col    = GuiTheme.rgba(accent.getRed(), accent.getGreen(), accent.getBlue(), 200);

        for (Entity entity : mc.level.entitiesForRendering()) {
            if (mc.player.distanceToSqr(entity) > rangeSq) continue;

            boolean draw = false;
            if (players.getValue() && entity instanceof Player p && p != mc.player) {
                draw = true;
            } else if (mobs.getValue() && entity instanceof Mob mob) {
                if (!hostilesOnly.getValue() || mob instanceof Monster) draw = true;
            } else if (items.getValue() && entity instanceof ItemEntity) {
                draw = true;
            }
            if (!draw) continue;

            Vec3 lerpedPos = entity.getPosition(event.delta);
            Vec3 centre    = lerpedPos.add(0, entity.getBbHeight() / 2.0, 0);

            int[] screen = RenderUtils.projectToScreen(centre, viewRot, projMat, camPos, winW, winH);
            if (screen == null) continue;

            pending.add(new TracerData(screen[0], screen[1], col));
        }
    }

    // ── Phase 2: draw collected tracers on the HUD ────────────────────────

    @Override
    public void onRenderHud(HudEvent event) {
        if (pending.isEmpty()) return;
        GuiGraphicsExtractor ctx = event.context;
        for (TracerData t : pending) {
            RenderUtils.drawLine2D(ctx, originX, originY, t.sx, t.sy, t.color);
        }
    }
}
