package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.GameRenderListener;
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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

public final class OreESP extends Module implements GameRenderListener {

    private final NumberSetting range = new NumberSetting(
            EncryptedString.of("Range"), 5, 30, 15, 1);

    private final BooleanSetting diamond      = new BooleanSetting(EncryptedString.of("Diamond"), true);
    private final BooleanSetting iron         = new BooleanSetting(EncryptedString.of("Iron"), true);
    private final BooleanSetting gold         = new BooleanSetting(EncryptedString.of("Gold"), true);
    private final BooleanSetting emerald      = new BooleanSetting(EncryptedString.of("Emerald"), true);
    private final BooleanSetting redstone     = new BooleanSetting(EncryptedString.of("Redstone"), false);
    private final BooleanSetting lapis        = new BooleanSetting(EncryptedString.of("Lapis"), false);
    private final BooleanSetting coal         = new BooleanSetting(EncryptedString.of("Coal"), false);
    private final BooleanSetting copper       = new BooleanSetting(EncryptedString.of("Copper"), false);
    private final BooleanSetting ancientDebris = new BooleanSetting(EncryptedString.of("Ancient Debris"), true);
    private final BooleanSetting fill          = new BooleanSetting(EncryptedString.of("Fill"), true);
    private final BooleanSetting showTracers   = new BooleanSetting(EncryptedString.of("Tracers"), false);

    /** Thread-safe list shared between scan thread and render thread. */
    private final CopyOnWriteArrayList<OreEntry> cachedOres = new CopyOnWriteArrayList<>();

    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> scanTask;

    public OreESP() {
        super(EncryptedString.of("OreESP"),
                EncryptedString.of("Highlights ore blocks through walls"),
                -1, CategoryManager.ESP);
        addSettings(range, diamond, iron, gold, emerald, redstone, lapis, coal, copper, ancientDebris, fill, showTracers);
    }

    @Override
    public void onEnable() {
        cachedOres.clear();
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "OreESP-Scanner");
            t.setDaemon(true);
            return t;
        });
        // Scan every 500 ms on a background thread — no render-thread stall
        scanTask = scheduler.scheduleAtFixedRate(this::scanOres, 0, 500, TimeUnit.MILLISECONDS);
        eventManager.add(GameRenderListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(GameRenderListener.class, this);
        if (scanTask != null)  { scanTask.cancel(false); scanTask = null; }
        if (scheduler != null) { scheduler.shutdownNow(); scheduler = null; }
        cachedOres.clear();
        super.onDisable();
    }

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
                             y <= Math.min(worldTop,    center.getY() + r); y++) {
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

    @Override
    public void onGameRender(GameRenderEvent event) {
        if (mc == null || mc.level == null || mc.player == null) return;

        for (OreEntry entry : cachedOres) {
            AABB box = new AABB(
                    entry.pos.getX(),     entry.pos.getY(),     entry.pos.getZ(),
                    entry.pos.getX() + 1, entry.pos.getY() + 1, entry.pos.getZ() + 1);

            Color outline = new Color(entry.color.getRed(), entry.color.getGreen(), entry.color.getBlue(), 220);

            RenderUtils.drawOutlinedBox(event.matrices, box, outline);

            if (fill.getValue()) {
                Color fillCol = new Color(entry.color.getRed(), entry.color.getGreen(), entry.color.getBlue(), 35);
                RenderUtils.renderFilledBox(event.matrices,
                        box.minX, box.minY, box.minZ,
                        box.maxX, box.maxY, box.maxZ, fillCol);
            }

            if (showTracers.getValue()) {
                RenderUtils.drawTracer(event.matrices, box.getCenter(), outline);
            }
        }
    }

    /** Returns the distinct display colour for each ore type, or null if not a target ore. */
    private Color oreColor(Block block) {
        if (diamond.getValue()) {
            if (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE)
                return new Color(0x1EAEE3);
        }
        if (iron.getValue()) {
            if (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE)
                return new Color(0xD4A07A);
        }
        if (gold.getValue()) {
            if (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE)
                return new Color(0xFFD700);
        }
        if (emerald.getValue()) {
            if (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE)
                return new Color(0x17DE63);
        }
        if (redstone.getValue()) {
            if (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE)
                return new Color(0xE82525);
        }
        if (lapis.getValue()) {
            if (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE)
                return new Color(0x1B44C8);
        }
        if (coal.getValue()) {
            if (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE)
                return new Color(0x444444);
        }
        if (copper.getValue()) {
            if (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE)
                return new Color(0xE07A35);
        }
        if (ancientDebris.getValue()) {
            if (block == Blocks.ANCIENT_DEBRIS)
                return new Color(0xAB47BC);
        }
        return null;
    }

    private record OreEntry(BlockPos pos, Color color) {}
}
