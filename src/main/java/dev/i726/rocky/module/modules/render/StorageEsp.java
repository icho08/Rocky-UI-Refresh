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
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.inventory.Inventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
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
        Color fillColor    = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), fA);

        Camera cam    = mc.gameRenderer.getCamera();
        Vec3d  camPos = cam.getPos();
        float  td     = mc.getRenderTickCounter().getTickProgress(true);

        VertexConsumerProvider.Immediate immediate =
                mc.getBufferBuilders().getEffectVertexConsumers();

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

                    if (showContents.getValue() && be instanceof Inventory inv) {
                        renderContentLabel(event.matrices, immediate, cam, camPos,
                                x + 0.5, y + 1.15, z + 0.5, inv);
                    }
                }
            }
        }

        immediate.draw();
    }

    private void renderContentLabel(MatrixStack matrices,
                                    VertexConsumerProvider.Immediate immediate,
                                    Camera cam, Vec3d camPos,
                                    double wx, double wy, double wz,
                                    Inventory inv) {
        int filled = 0;
        int total  = inv.size();
        for (int i = 0; i < total; i++) {
            ItemStack s = inv.getStack(i);
            if (!s.isEmpty()) filled++;
        }

        // Choose color: green gradient from empty→full, red when completely empty
        int textColor;
        if (filled == 0) {
            textColor = 0xFF4444; // red — empty
        } else if (filled == total) {
            textColor = 0xFF4444; // red — full (no more room)
        } else {
            float ratio = (float) filled / total;
            int r = (int)((1f - ratio) * 220);
            int g = (int)(ratio * 220);
            textColor = new Color(r, g, 40, 255).getRGB();
        }

        String label = filled + "/" + total;

        double dx = wx - camPos.x;
        double dy = wy - camPos.y;
        double dz = wz - camPos.z;

        matrices.push();
        matrices.translate(dx, dy, dz);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-cam.getYaw()));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(cam.getPitch()));
        matrices.scale(-0.02f, -0.02f, 0.02f);

        float w = mc.textRenderer.getWidth(label) / 2f;
        mc.textRenderer.draw(
                label, -w, 0, textColor, false,
                matrices.peek().getPositionMatrix(),
                immediate,
                TextRenderer.TextLayerType.SEE_THROUGH,
                0x40000000,
                15728880
        );
        matrices.pop();
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
