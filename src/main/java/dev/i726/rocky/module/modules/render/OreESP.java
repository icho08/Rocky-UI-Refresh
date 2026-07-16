package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.event.events.HudListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.utils.RenderUtils;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * OreESP using 2D screen-space projection.
 *
 * Scanning is done on a background thread (existing pattern, retained).
 * Rendering is split into GameRenderListener (project) → HudListener (draw).
 * Works in MC 26.1.2 where RenderType.lines was removed.
 */
public final class OreESP extends Module implements GameRenderListener, HudListener {

    private static final class OreEntry {
        final BlockPos pos;
        final Color    color;
        OreEntry(BlockPos pos, Color color) { this.pos = pos; this.color = color; }
    }

    private static final class OreScreenData {
        final int minX, minY, maxX, maxY, outlineColor, fillColor;
        final boolean doFill, doTracer;
        final int cx, cy;
        OreScreenData(int x1, int y1, int x2, int y2, int out, int fill,
                      boolean doFill, boolean doTracer, int cx, int cy) {
            minX = x1; minY = y1; maxX = x2; maxY = y2;
            outlineColor = out; fillColor = fill;
            this.doFill = doFill; this.doTracer = doTracer;
            this.cx = cx; this.cy = cy;
        }
    }

    private final CopyOnWriteArrayList<OreEntry>       cachedOres = new CopyOnWriteArrayList<>();
    private final List<OreScreenData>                  pendingDraw = new ArrayList<>();

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?>       scanTask;

    private int screenOriginX, screenOriginY;

    private final NumberSetting range = new NumberSetting(
            EncryptedString.of("Range"), 5, 30, 15, 1);

    private final BooleanSetting diamond       = new BooleanSetting(EncryptedString.of("Diamond"),       true);
    private final BooleanSetting iron          = new BooleanSetting(EncryptedString.of("Iron"),          true);
    private final BooleanSetting gold          = new BooleanSetting(EncryptedString.of("Gold"),          true);
    private final BooleanSetting emerald       = new BooleanSetting(EncryptedString.of("Emerald"),       true);
    private final BooleanSetting redstone      = new BooleanSetting(EncryptedString.of("Redstone"),      false);
    private final BooleanSetting lapis         = new BooleanSetting(EncryptedString.of("Lapis"),         false);
    private final BooleanSetting coal          = new BooleanSetting(EncryptedString.of("Coal"),          false);
    private final BooleanSetting copper        = new BooleanSetting(EncryptedString.of("Copper"),        false);
    private final BooleanSetting ancientDebris = new BooleanSetting(EncryptedString.of("Ancient Debris"), true);
    private final BooleanSetting fill          = new BooleanSetting(EncryptedString.of("Fill"),          true);
    private final BooleanSetting showTracers   = new BooleanSetting(EncryptedString.of("Tracers"),       false);

    public OreESP() {
        super(EncryptedString.of("OreESP"),
                EncryptedString.of("Highlights ore blocks through walls"),
                -1, CategoryManager.ESP);
        addSettings(range, diamond, iron, gold, emerald, redstone, lapis, coal, copper,
                ancientDebris, fill, showTracers);
    }

    @Override
    public void onEnable() {
        cachedOres.clear();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "OreESP-Scanner");
            t.setDaemon(true);
            return t;
        });
        scanTask = scheduler.scheduleAtFixedRate(this::scanOres, 0, 500, TimeUnit.MILLISECONDS);
        eventManager.add(GameRenderListener.class, this);
        eventManager.add(HudListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(GameRenderListener.class, this);
        eventManager.remove(HudListener.class, this);
        if (scanTask  != null) { scanTask.cancel(false);  scanTask = null; }
        if (scheduler != null) { scheduler.shutdownNow(); scheduler = null; }
        cachedOres.clear();
        pendingDraw.clear();
        super.onDisable();
    }

    // ── Background ore scanner ─────────────────────────────────────────────

    private void scanOres() {
        try {
            if (mc == null || mc.level == null || mc.player == null) return;

            BlockPos center     = mc.player.blockPosition();
            int      r          = range.getValueInt();
            int      worldBottom = mc.level.getMinY();
            int      worldTop    = worldBottom + mc.level.getHeight() - 1;

            List<OreEntry> found = new ArrayList<>();

            for (int x = center.getX() - r; x <= center.getX() + r; x++) {
                for (int z = center.getZ() - r; z <= center.getZ() + r; z++) {
                    for (int y = Math.max(worldBottom, center.getY() - r);
                             y <= Math.min(worldTop, center.getY() + r); y++) {
                        BlockPos pos   = new BlockPos(x, y, z);
                        Block    block = mc.level.getBlockState(pos).getBlock();
                        Color    color = oreColor(block);
                        if (color != null) found.add(new OreEntry(pos, color));
                    }
                }
            }

            cachedOres.clear();
            cachedOres.addAll(found);
        } catch (Exception ignored) {}
    }

    // ── Phase 1: project ore AABB corners → 2D bounding rect ──────────────

    @Override
    public void onGameRender(GameRenderEvent event) {
        pendingDraw.clear();
        if (mc == null || mc.level == null || mc.player == null || cachedOres.isEmpty()) return;

        Camera cam    = mc.gameRenderer.getMainCamera();
        Vec3   camPos = cam.position();
        int    winW   = mc.getWindow().getGuiScaledWidth();
        int    winH   = mc.getWindow().getGuiScaledHeight();

        screenOriginX = winW / 2;
        screenOriginY = winH;

        Matrix4f viewRot  = event.matrices.last().pose();
        Matrix4f projMat  = event.projMatrix;

        for (OreEntry entry : cachedOres) {
            AABB box = new AABB(
                    entry.pos.getX(),     entry.pos.getY(),     entry.pos.getZ(),
                    entry.pos.getX() + 1, entry.pos.getY() + 1, entry.pos.getZ() + 1
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

            int outC  = GuiTheme.rgba(entry.color.getRed(), entry.color.getGreen(), entry.color.getBlue(), 220);
            int fillC = GuiTheme.rgba(entry.color.getRed(), entry.color.getGreen(), entry.color.getBlue(), 35);

            int[] centre = RenderUtils.projectToScreen(box.getCenter(), viewRot, projMat, camPos, winW, winH);
            int cx = centre != null ? centre[0] : (minSX + maxSX) / 2;
            int cy = centre != null ? centre[1] : (minSY + maxSY) / 2;

            pendingDraw.add(new OreScreenData(minSX, minSY, maxSX, maxSY, outC, fillC,
                    fill.getValue(), showTracers.getValue(), cx, cy));
        }
    }

    // ── Phase 2: draw on the HUD ───────────────────────────────────────────

    @Override
    public void onRenderHud(HudEvent event) {
        if (pendingDraw.isEmpty()) return;
        GuiGraphicsExtractor ctx = event.context;

        for (OreScreenData d : pendingDraw) {
            RenderUtils.drawRect2D(ctx, d.minX, d.minY, d.maxX, d.maxY, d.outlineColor);
            if (d.doFill) ctx.fill(d.minX + 1, d.minY + 1, d.maxX, d.maxY, d.fillColor);
            if (d.doTracer) {
                RenderUtils.drawLine2D(ctx, screenOriginX, screenOriginY, d.cx, d.cy, d.outlineColor);
            }
        }
    }

    // ── Ore colour table ───────────────────────────────────────────────────

    private Color oreColor(Block block) {
        if (diamond.getValue() && (block == Blocks.DIAMOND_ORE       || block == Blocks.DEEPSLATE_DIAMOND_ORE))  return new Color(0x1EAEE3);
        if (iron.getValue()    && (block == Blocks.IRON_ORE          || block == Blocks.DEEPSLATE_IRON_ORE))     return new Color(0xD4A07A);
        if (gold.getValue()    && (block == Blocks.GOLD_ORE          || block == Blocks.DEEPSLATE_GOLD_ORE
                                                                     || block == Blocks.NETHER_GOLD_ORE))         return new Color(0xFFD700);
        if (emerald.getValue() && (block == Blocks.EMERALD_ORE       || block == Blocks.DEEPSLATE_EMERALD_ORE))  return new Color(0x17DE63);
        if (redstone.getValue()&& (block == Blocks.REDSTONE_ORE      || block == Blocks.DEEPSLATE_REDSTONE_ORE)) return new Color(0xE82525);
        if (lapis.getValue()   && (block == Blocks.LAPIS_ORE         || block == Blocks.DEEPSLATE_LAPIS_ORE))    return new Color(0x1B44C8);
        if (coal.getValue()    && (block == Blocks.COAL_ORE          || block == Blocks.DEEPSLATE_COAL_ORE))     return new Color(0x444444);
        if (copper.getValue()  && (block == Blocks.COPPER_ORE        || block == Blocks.DEEPSLATE_COPPER_ORE))   return new Color(0xE07A35);
        if (ancientDebris.getValue() && block == Blocks.ANCIENT_DEBRIS)                                           return new Color(0xAB47BC);
        return null;
    }
}
