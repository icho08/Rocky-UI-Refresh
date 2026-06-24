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
import dev.i726.rocky.utils.TextRenderer;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

/**
 * StorageESP using 2D screen-space projection.
 *
 * Two-phase rendering (GameRenderListener → collect, HudListener → draw).
 * The "Show Contents" label is also rendered in 2D at the top of the projected box.
 * Works in MC 26.1.2 where RenderType.lines and Font.drawInBatch were removed.
 */
public final class StorageEsp extends Module implements GameRenderListener, HudListener {

    private static final class StorageData {
        final int minX, minY, maxX, maxY, outlineColor, fillColor;
        final boolean doFill, doTracer;
        final int labelX, labelY, labelColor;
        final String label;
        StorageData(int x1, int y1, int x2, int y2, int out, int fill,
                    boolean doFill, boolean doTracer,
                    int lx, int ly, int lc, String label) {
            minX = x1; minY = y1; maxX = x2; maxY = y2;
            outlineColor = out; fillColor = fill;
            this.doFill = doFill; this.doTracer = doTracer;
            labelX = lx; labelY = ly; labelColor = lc; this.label = label;
        }
    }

    private final List<StorageData> pending = new ArrayList<>();
    private int screenOriginX, screenOriginY;

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

    private final BooleanSetting showContents = new BooleanSetting(
            EncryptedString.of("Show Contents"), true
    ).setDescription(EncryptedString.of("Show item count above the container — green = has items, red = empty"));

    public StorageEsp() {
        super(
                EncryptedString.of("StorageESP"),
                EncryptedString.of("Highlights storage containers through walls"),
                -1,
                CategoryManager.ESP
        );
        addSettings(chests, barrels, shulkers, furnaces, hoppers, fill, fillOpacity, outlineOpacity, tracers, showContents);
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

    // ── Phase 1: project block AABB corners → 2D bounding rect ───────────

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

        Matrix4f viewRot     = event.matrices.last().pose();
        double   fovYRad     = Math.toRadians(mc.options.fov().get());
        double   tanHalfFovY = Math.tan(fovYRad / 2.0);
        double   aspect      = (double) winW / winH;

        Color accent  = GuiTheme.accent();
        int   fA      = (int) fillOpacity.getValue();
        int   oA      = (int) outlineOpacity.getValue();
        int   fillCol = GuiTheme.rgba(accent.getRed(), accent.getGreen(), accent.getBlue(), fA);
        int   outCol  = GuiTheme.rgba(accent.getRed(), accent.getGreen(), accent.getBlue(), oA);

        int playerCX = mc.player.getBlockX() >> 4;
        int playerCZ = mc.player.getBlockZ() >> 4;
        int chunkRadius = 6;

        for (int cx = playerCX - chunkRadius; cx <= playerCX + chunkRadius; cx++) {
            for (int cz = playerCZ - chunkRadius; cz <= playerCZ + chunkRadius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!shouldRender(be)) continue;

                    double bx = be.getBlockPos().getX();
                    double by = be.getBlockPos().getY();
                    double bz = be.getBlockPos().getZ();
                    AABB   box = new AABB(bx, by, bz, bx + 1, by + 1, bz + 1);

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

                    // Content label
                    String label    = "";
                    int    labelCol = 0xFFFFFFFF;
                    int    labelX   = (minSX + maxSX) / 2;
                    int    labelY   = minSY - 10;

                    if (showContents.getValue() && be instanceof Container inv) {
                        int filled = 0;
                        int total  = inv.getContainerSize();
                        for (int i = 0; i < total; i++) {
                            ItemStack s = inv.getItem(i);
                            if (!s.isEmpty()) filled++;
                        }
                        label = filled + "/" + total;
                        if (filled == 0 || filled == total) {
                            labelCol = 0xFFFF4444;
                        } else {
                            float ratio = (float) filled / total;
                            int r = (int)((1f - ratio) * 220);
                            int g = (int)(ratio * 220);
                            labelCol = new Color(r, g, 40, 230).getRGB();
                        }
                        int lw = TextRenderer.getWidth(label);
                        labelX = (minSX + maxSX) / 2 - lw / 2;
                    }

                    pending.add(new StorageData(minSX, minSY, maxSX, maxSY, outCol, fillCol,
                            fill.getValue(), tracers.getValue(),
                            labelX, labelY, labelCol, label));
                }
            }
        }
    }

    // ── Phase 2: draw on the HUD ──────────────────────────────────────────

    @Override
    public void onRenderHud(HudEvent event) {
        if (pending.isEmpty()) return;
        GuiGraphicsExtractor ctx = event.context;

        for (StorageData d : pending) {
            RenderUtils.drawRect2D(ctx, d.minX, d.minY, d.maxX, d.maxY, d.outlineColor);
            if (d.doFill) ctx.fill(d.minX + 1, d.minY + 1, d.maxX, d.maxY, d.fillColor);
            if (d.doTracer) {
                int midX = (d.minX + d.maxX) / 2;
                int midY = (d.minY + d.maxY) / 2;
                RenderUtils.drawLine2D(ctx, screenOriginX, screenOriginY, midX, midY, d.outlineColor);
            }
            if (!d.label.isEmpty() && d.labelY > 0) {
                TextRenderer.text(d.label, ctx, d.labelX, d.labelY, d.labelColor);
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
