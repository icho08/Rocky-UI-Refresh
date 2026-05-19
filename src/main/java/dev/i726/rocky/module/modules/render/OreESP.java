package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public final class OreESP extends Module implements GameRenderListener {

    private final NumberSetting range = new NumberSetting(
            EncryptedString.of("Range"), 5, 30, 15, 1
    ).setDescription(EncryptedString.of("Block search radius around player"));

    private final BooleanSetting diamond = new BooleanSetting(
            EncryptedString.of("Diamond"), true
    ).setDescription(EncryptedString.of("Highlight diamond ore"));

    private final BooleanSetting iron = new BooleanSetting(
            EncryptedString.of("Iron"), true
    ).setDescription(EncryptedString.of("Highlight iron ore"));

    private final BooleanSetting gold = new BooleanSetting(
            EncryptedString.of("Gold"), true
    ).setDescription(EncryptedString.of("Highlight gold ore"));

    private final BooleanSetting emerald = new BooleanSetting(
            EncryptedString.of("Emerald"), true
    ).setDescription(EncryptedString.of("Highlight emerald ore"));

    private final BooleanSetting redstone = new BooleanSetting(
            EncryptedString.of("Redstone"), false
    ).setDescription(EncryptedString.of("Highlight redstone ore"));

    private final BooleanSetting lapis = new BooleanSetting(
            EncryptedString.of("Lapis"), false
    ).setDescription(EncryptedString.of("Highlight lapis ore"));

    private final BooleanSetting coal = new BooleanSetting(
            EncryptedString.of("Coal"), false
    ).setDescription(EncryptedString.of("Highlight coal ore"));

    private final BooleanSetting copper = new BooleanSetting(
            EncryptedString.of("Copper"), false
    ).setDescription(EncryptedString.of("Highlight copper ore"));

    private final BooleanSetting ancientDebris = new BooleanSetting(
            EncryptedString.of("Ancient Debris"), true
    ).setDescription(EncryptedString.of("Highlight ancient debris"));

    private final BooleanSetting fill = new BooleanSetting(
            EncryptedString.of("Fill"), true
    ).setDescription(EncryptedString.of("Fill the bounding box"));

    private final BooleanSetting showTracers = new BooleanSetting(
            EncryptedString.of("Tracers"), false
    ).setDescription(EncryptedString.of("Draw tracer lines to ores"));

    private final List<BlockPos> cachedOres = new ArrayList<>();
    private long lastScanTime = 0L;
    private static final long SCAN_INTERVAL_MS = 500L;

    public OreESP() {
        super(
                EncryptedString.of("OreESP"),
                EncryptedString.of("Highlights ore blocks through walls"),
                -1,
                CategoryManager.ESP
        );
        addSettings(range, diamond, iron, gold, emerald, redstone, lapis, coal, copper, ancientDebris, fill, showTracers);
    }

    @Override
    public void onEnable() {
        cachedOres.clear();
        lastScanTime = 0L;
        eventManager.add(GameRenderListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(GameRenderListener.class, this);
        cachedOres.clear();
        super.onDisable();
    }

    @Override
    public void onGameRender(GameRenderEvent event) {
        if (mc == null || mc.world == null || mc.player == null) return;

        long now = System.currentTimeMillis();
        if (now - lastScanTime >= SCAN_INTERVAL_MS) {
            rescan();
            lastScanTime = now;
        }

        Color accent = GuiTheme.accent();
        Color outlineColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 220);
        Color fillColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), 40);

        for (BlockPos pos : cachedOres) {
            Box box = new Box(pos.getX(), pos.getY(), pos.getZ(),
                    pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1);

            RenderUtils.drawOutlinedBox(event.matrices, box, outlineColor);

            if (fill.getValue()) {
                RenderUtils.renderFilledBox(event.matrices,
                        box.minX, box.minY, box.minZ,
                        box.maxX, box.maxY, box.maxZ,
                        fillColor);
            }

            if (showTracers.getValue()) {
                RenderUtils.drawTracer(event.matrices, box.getCenter(), outlineColor);
            }
        }
    }

    private void rescan() {
        cachedOres.clear();
        if (mc.world == null || mc.player == null) return;

        BlockPos center = mc.player.getBlockPos();
        int r = range.getValueInt();
        int worldBottom = mc.world.getBottomY();
        int worldTop = worldBottom + mc.world.getHeight() - 1;

        for (int x = center.getX() - r; x <= center.getX() + r; x++) {
            for (int y = Math.max(worldBottom, center.getY() - r); y <= Math.min(worldTop, center.getY() + r); y++) {
                for (int z = center.getZ() - r; z <= center.getZ() + r; z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    Block block = mc.world.getBlockState(pos).getBlock();
                    if (isTargetOre(block)) {
                        cachedOres.add(pos);
                    }
                }
            }
        }
    }

    private boolean isTargetOre(Block block) {
        if (diamond.getValue() && (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE)) return true;
        if (iron.getValue() && (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE)) return true;
        if (gold.getValue() && (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE)) return true;
        if (emerald.getValue() && (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE)) return true;
        if (redstone.getValue() && (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE)) return true;
        if (lapis.getValue() && (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE)) return true;
        if (coal.getValue() && (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE)) return true;
        if (copper.getValue() && (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE)) return true;
        if (ancientDebris.getValue() && block == Blocks.ANCIENT_DEBRIS) return true;
        return false;
    }
}
