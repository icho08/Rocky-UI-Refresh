package dev.i726.rocky.module.modules.render;

import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.block.entity.*;
import net.minecraft.util.math.Box;
import net.minecraft.world.chunk.WorldChunk;

import java.awt.Color;

public final class StorageEsp extends Module implements GameRenderListener {

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

    public StorageEsp() {
        super(
                EncryptedString.of("StorageESP"),
                EncryptedString.of("Highlights storage containers through walls"),
                -1,
                CategoryManager.ESP
        );
        addSettings(chests, barrels, shulkers, furnaces, hoppers, fill, fillOpacity, outlineOpacity, tracers);
    }

    @Override
    public void onEnable() {
        eventManager.add(GameRenderListener.class, this);
        super.onEnable();
    }

    @Override
    public void onDisable() {
        eventManager.remove(GameRenderListener.class, this);
        super.onDisable();
    }

    @Override
    public void onGameRender(GameRenderEvent event) {
        if (mc == null || mc.world == null || mc.player == null) return;

        Color accent = GuiTheme.accent();
        int fA = (int) fillOpacity.getValue();
        int oA = (int) outlineOpacity.getValue();
        Color outlineColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), oA);
        Color fillColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), fA);

        int playerCX = mc.player.getBlockX() >> 4;
        int playerCZ = mc.player.getBlockZ() >> 4;
        int chunkRadius = 6;

        for (int cx = playerCX - chunkRadius; cx <= playerCX + chunkRadius; cx++) {
            for (int cz = playerCZ - chunkRadius; cz <= playerCZ + chunkRadius; cz++) {
                WorldChunk chunk = mc.world.getChunkManager().getWorldChunk(cx, cz);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!shouldRender(be)) continue;

                    double x = be.getPos().getX();
                    double y = be.getPos().getY();
                    double z = be.getPos().getZ();
                    Box box = new Box(x, y, z, x + 1, y + 1, z + 1);

                    RenderUtils.drawOutlinedBox(event.matrices, box, outlineColor);

                    if (fill.getValue()) {
                        RenderUtils.renderFilledBox(event.matrices,
                                box.minX, box.minY, box.minZ,
                                box.maxX, box.maxY, box.maxZ,
                                fillColor);
                    }

                    if (tracers.getValue()) {
                        RenderUtils.drawTracer(event.matrices, box.getCenter(), outlineColor);
                    }
                }
            }
        }
    }

    private boolean shouldRender(BlockEntity be) {
        if (chests.getValue() && (be instanceof ChestBlockEntity || be instanceof TrappedChestBlockEntity)) return true;
        if (barrels.getValue() && be instanceof BarrelBlockEntity) return true;
        if (shulkers.getValue() && be instanceof ShulkerBoxBlockEntity) return true;
        if (furnaces.getValue() && (be instanceof FurnaceBlockEntity
                || be instanceof BlastFurnaceBlockEntity
                || be instanceof SmokerBlockEntity)) return true;
        if (hoppers.getValue() && (be instanceof HopperBlockEntity
                || be instanceof DispenserBlockEntity
                || be instanceof DropperBlockEntity)) return true;
        return false;
    }
}
