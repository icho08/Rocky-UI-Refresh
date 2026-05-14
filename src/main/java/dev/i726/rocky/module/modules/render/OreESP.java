package dev.i726.rocky.module.modules.render;

import org.lwjgl.opengl.GL11;
import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.event.events.TickListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;

public final class OreESP extends Module implements GameRenderListener, TickListener {

    private final NumberSetting range     = new NumberSetting(EncryptedString.of("Range"), 4, 16, 8, 1)
            .setDescription(EncryptedString.of("Block radius to scan for ores"));
    private final NumberSetting fillAlpha = new NumberSetting(EncryptedString.of("Fill Alpha"), 0, 200, 40, 5)
            .setDescription(EncryptedString.of("Opacity of the filled box (0 = outline only)"));

    private final BooleanSetting diamonds      = new BooleanSetting(EncryptedString.of("Diamonds"), true);
    private final BooleanSetting gold          = new BooleanSetting(EncryptedString.of("Gold"), true);
    private final BooleanSetting iron          = new BooleanSetting(EncryptedString.of("Iron"), true);
    private final BooleanSetting emeralds      = new BooleanSetting(EncryptedString.of("Emeralds"), false);
    private final BooleanSetting ancientDebris = new BooleanSetting(EncryptedString.of("Ancient Debris"), true);
    private final BooleanSetting redstone      = new BooleanSetting(EncryptedString.of("Redstone"), false);
    private final BooleanSetting lapis         = new BooleanSetting(EncryptedString.of("Lapis"), false);
    private final BooleanSetting coal          = new BooleanSetting(EncryptedString.of("Coal"), false);
    private final BooleanSetting copper        = new BooleanSetting(EncryptedString.of("Copper"), false);

    private record OreEntry(BlockPos pos, Color color) {}

    private final List<OreEntry> cache       = new ArrayList<>();
    private final List<OreEntry> renderCache = new ArrayList<>();
    private int tickCount = 0;
    private static final int UPDATE_TICKS = 40;

    public OreESP() {
        super(EncryptedString.of("Ore ESP"),
                EncryptedString.of("Highlights ore blocks through walls"),
                -1, CategoryManager.ESP);
        addSettings(range, fillAlpha, diamonds, gold, iron, emeralds, ancientDebris, redstone, lapis, coal, copper);
    }

    @Override
    public void onEnable() {
        eventManager.add(GameRenderListener.class, this);
        eventManager.add(TickListener.class, this);
        tickCount = UPDATE_TICKS;
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(GameRenderListener.class, this);
        eventManager.remove(TickListener.class, this);
        cache.clear();
        renderCache.clear();
        super.onDisable();
    }

    @Override
    public void onTick() {
        if (mc.player == null || mc.world == null) return;
        if (++tickCount < UPDATE_TICKS) return;
        tickCount = 0;

        cache.clear();
        BlockPos center = mc.player.getBlockPos();
        int r = range.getValueInt();

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    BlockPos pos = center.add(x, y, z);
                    Color color = oreColor(mc.world.getBlockState(pos).getBlock());
                    if (color != null) cache.add(new OreEntry(pos.toImmutable(), color));
                }
            }
        }

        synchronized (renderCache) {
            renderCache.clear();
            renderCache.addAll(cache);
        }
    }

    @Override
    public void onGameRender(GameRenderEvent event) {
        if (mc.player == null || mc.world == null) return;

        MatrixStack matrices = event.matrices;
        int alpha = fillAlpha.getValueInt();

        GL11.glDisable(GL11.GL_DEPTH_TEST);

        synchronized (renderCache) {
            for (OreEntry e : renderCache) {
                BlockPos p = e.pos();
                Color c = e.color();

                if (alpha > 0) {
                    RenderUtils.renderFilledBox(matrices,
                            p.getX(), p.getY(), p.getZ(),
                            p.getX() + 1, p.getY() + 1, p.getZ() + 1,
                            new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha));
                }

                RenderUtils.drawOutlinedBox(matrices, new Box(p), c);
            }
        }

        mc.getBufferBuilders().getEntityVertexConsumers().draw();
        GL11.glEnable(GL11.GL_DEPTH_TEST);
    }

    private Color oreColor(Block block) {
        if (diamonds.getValue() && (block == Blocks.DIAMOND_ORE || block == Blocks.DEEPSLATE_DIAMOND_ORE))
            return new Color(0x4ECDC4);
        if (gold.getValue() && (block == Blocks.GOLD_ORE || block == Blocks.DEEPSLATE_GOLD_ORE || block == Blocks.NETHER_GOLD_ORE))
            return new Color(0xFFD700);
        if (iron.getValue() && (block == Blocks.IRON_ORE || block == Blocks.DEEPSLATE_IRON_ORE))
            return new Color(0xC8956A);
        if (emeralds.getValue() && (block == Blocks.EMERALD_ORE || block == Blocks.DEEPSLATE_EMERALD_ORE))
            return new Color(0x00C853);
        if (ancientDebris.getValue() && block == Blocks.ANCIENT_DEBRIS)
            return new Color(0xB22222);
        if (redstone.getValue() && (block == Blocks.REDSTONE_ORE || block == Blocks.DEEPSLATE_REDSTONE_ORE))
            return new Color(0xFF1744);
        if (lapis.getValue() && (block == Blocks.LAPIS_ORE || block == Blocks.DEEPSLATE_LAPIS_ORE))
            return new Color(0x1565C0);
        if (coal.getValue() && (block == Blocks.COAL_ORE || block == Blocks.DEEPSLATE_COAL_ORE))
            return new Color(0x616161);
        if (copper.getValue() && (block == Blocks.COPPER_ORE || block == Blocks.DEEPSLATE_COPPER_ORE))
            return new Color(0xE87722);
        return null;
    }
}
