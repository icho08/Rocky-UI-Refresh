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
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

public final class StorageEsp extends Module implements GameRenderListener, HudListener {

    private static final class StorageEntry {
        final BlockPos pos;
        StorageEntry(BlockPos pos) { this.pos = pos; }
    }

    private static final class ScreenData {
        final int minX, minY, maxX, maxY, outlineColor, fillColor;
        final boolean doFill, doTracer;
        final int cx, cy;
        ScreenData(int x1, int y1, int x2, int y2, int out, int fill,
                   boolean doFill, boolean doTracer, int cx, int cy) {
            minX = x1; minY = y1; maxX = x2; maxY = y2;
            outlineColor = out; fillColor = fill;
            this.doFill = doFill; this.doTracer = doTracer;
            this.cx = cx; this.cy = cy;
        }
    }

    private final CopyOnWriteArrayList<StorageEntry> cached = new CopyOnWriteArrayList<>();
    private final List<ScreenData> pending = new ArrayList<>();
    private int screenOriginX, screenOriginY;

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scanTask;

    private final BooleanSetting chests = new BooleanSetting(
            EncryptedString.of("Chests"), true
    ).setDescription(EncryptedString.of("Highlight chest and trapped chest blocks"));

    private final BooleanSetting barrels = new BooleanSetting(
            EncryptedString.of("Barrels"), true
    ).setDescription(EncryptedString.of("Highlight barrel blocks"));

    private final BooleanSetting shulkers = new BooleanSetting(
            EncryptedString.of("Shulkers"), true
    ).setDescription(EncryptedString.of("Highlight shulker box blocks"));

    private final BooleanSetting furnaces = new BooleanSetting(
            EncryptedString.of("Furnaces"), false
    ).setDescription(EncryptedString.of("Highlight furnace, blast furnace and smoker"));

    private final BooleanSetting hoppers = new BooleanSetting(
            EncryptedString.of("Hoppers"), false
    ).setDescription(EncryptedString.of("Highlight hopper and dropper blocks"));

    private final BooleanSetting fill = new BooleanSetting(
            EncryptedString.of("Fill"), true
    ).setDescription(EncryptedString.of("Fill the bounding box"));

    private final NumberSetting fillOpacity = new NumberSetting(
            EncryptedString.of("Fill Opacity"), 0, 255, 40, 5
    ).setDescription(EncryptedString.of("Transparency of the box fill (0 = invisible, 255 = solid)"));

    private final NumberSetting outlineOpacity = new NumberSetting(
            EncryptedString.of("Outline Opacity"), 0, 255, 220, 5
    ).setDescription(EncryptedString.of("Transparency of the box outline (0 = invisible, 255 = solid)"));

    private final BooleanSetting tracers = new BooleanSetting(
            EncryptedString.of("Tracers"), false
    ).setDescription(EncryptedString.of("Draw tracer lines to storage blocks"));

    private final NumberSetting maxRange = new NumberSetting(
            EncryptedString.of("Range"), 10, 128, 64, 8
    ).setDescription(EncryptedString.of("Maximum distance to show storage ESP"));

    public StorageEsp() {
        super(
                EncryptedString.of("StorageESP"),
                EncryptedString.of("Highlights storage containers through walls"),
                -1,
                CategoryManager.ESP
        );
        addSettings(chests, barrels, shulkers, furnaces, hoppers, fill, fillOpacity, outlineOpacity, tracers, maxRange);
    }

    @Override
    public void onEnable() {
        cached.clear();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "StorageESP-Scanner");
            t.setDaemon(true);
            return t;
        });
        scanTask = scheduler.scheduleAtFixedRate(this::scan, 0, 1000, TimeUnit.MILLISECONDS);
        eventManager.add(GameRenderListener.class, this);
        eventManager.add(HudListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(GameRenderListener.class, this);
        eventManager.remove(HudListener.class, this);
        if (scanTask != null) { scanTask.cancel(false); scanTask = null; }
        if (scheduler != null) { scheduler.shutdownNow(); scheduler = null; }
        cached.clear();
        pending.clear();
        super.onDisable();
    }

    private void scan() {
        try {
            if (mc == null || mc.level == null || mc.player == null) return;

            BlockPos center = mc.player.blockPosition();
            int r = maxRange.getValueInt();
            int chunkR = (r >> 4) + 1;
            int cx = center.getX() >> 4;
            int cz = center.getZ() >> 4;

            List<StorageEntry> found = new ArrayList<>();

            for (int dx = -chunkR; dx <= chunkR; dx++) {
                for (int dz = -chunkR; dz <= chunkR; dz++) {
                    LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cx + dx, cz + dz);
                    if (chunk == null) continue;

                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        if (!shouldRender(be)) continue;
                        if (center.distToCenterSqr(be.getBlockPos().getX() + 0.5,
                                                      be.getBlockPos().getY() + 0.5,
                                                      be.getBlockPos().getZ() + 0.5) > r * r) continue;
                        found.add(new StorageEntry(be.getBlockPos()));
                    }
                }
            }

            cached.clear();
            cached.addAll(found);
        } catch (Exception ignored) {}
    }

    @Override
    public void onGameRender(GameRenderEvent event) {
        pending.clear();
        if (mc == null || mc.level == null || mc.player == null || cached.isEmpty()) return;

        Camera cam    = mc.gameRenderer.getMainCamera();
        Vec3   camPos = cam.position();
        int    winW   = mc.getWindow().getGuiScaledWidth();
        int    winH   = mc.getWindow().getGuiScaledHeight();

        screenOriginX = winW / 2;
        screenOriginY = winH;

        Matrix4f viewRot     = event.matrices.last().pose();
        double   fovYRad     = Math.toRadians(mc.options.fov().get());
        double   tanHalfFovY = Math.tan(fovYRad / 2.0);
        double   aspect      = (double) winW / winH;

        Color accent  = GuiTheme.accent();
        int   fA      = (int) fillOpacity.getValue();
        int   oA      = (int) outlineOpacity.getValue();
        int   fillCol = GuiTheme.rgba(accent.getRed(), accent.getGreen(), accent.getBlue(), fA);
        int   outCol  = GuiTheme.rgba(accent.getRed(), accent.getGreen(), accent.getBlue(), oA);

        for (StorageEntry entry : cached) {
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
                int[] sc = RenderUtils.projectToScreen(corner, viewRot, camPos, winW, winH, tanHalfFovY, aspect);
                if (sc == null) continue;
                any = true;
                if (sc[0] < minSX) minSX = sc[0];
                if (sc[1] < minSY) minSY = sc[1];
                if (sc[0] > maxSX) maxSX = sc[0];
                if (sc[1] > maxSY) maxSY = sc[1];
            }

            if (!any) continue;

            int[] centre = RenderUtils.projectToScreen(box.getCenter(), viewRot, camPos, winW, winH, tanHalfFovY, aspect);
            int cx = centre != null ? centre[0] : (minSX + maxSX) / 2;
            int cy = centre != null ? centre[1] : (minSY + maxSY) / 2;

            pending.add(new ScreenData(minSX, minSY, maxSX, maxSY, outCol, fillCol,
                    fill.getValue(), tracers.getValue(), cx, cy));
        }
    }

    @Override
    public void onRenderHud(HudEvent event) {
        if (pending.isEmpty()) return;
        GuiGraphicsExtractor ctx = event.context;

        for (ScreenData d : pending) {
            RenderUtils.drawRect2D(ctx, d.minX, d.minY, d.maxX, d.maxY, d.outlineColor);
            if (d.doFill) ctx.fill(d.minX + 1, d.minY + 1, d.maxX, d.maxY, d.fillColor);
            if (d.doTracer) {
                RenderUtils.drawLine2D(ctx, screenOriginX, screenOriginY, d.cx, d.cy, d.outlineColor);
            }
        }
    }

    private boolean shouldRender(BlockEntity be) {
        if (chests.getValue()   && (be instanceof ChestBlockEntity || be instanceof TrappedChestBlockEntity)) return true;
        if (barrels.getValue()  && be instanceof BarrelBlockEntity)                                           return true;
        if (shulkers.getValue() && be instanceof ShulkerBoxBlockEntity)                                       return true;
        if (furnaces.getValue() && (be instanceof FurnaceBlockEntity
                                 || be instanceof BlastFurnaceBlockEntity
                                 || be instanceof SmokerBlockEntity))                                         return true;
        if (hoppers.getValue()  && (be instanceof HopperBlockEntity
                                 || be instanceof DispenserBlockEntity
                                 || be instanceof DropperBlockEntity))                                        return true;
        return false;
    }
}
