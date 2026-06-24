package dev.i726.rocky.module.modules.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.gui.GuiTheme;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.world.level.block.entity.*;
import net.minecraft.client.Camera;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlastFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.DispenserBlockEntity;
import net.minecraft.world.level.block.entity.DropperBlockEntity;
import net.minecraft.world.level.block.entity.FurnaceBlockEntity;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.entity.SmokerBlockEntity;
import net.minecraft.world.level.block.entity.TrappedChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
        if (mc == null || mc.level == null || mc.player == null) return;

        Color accent = GuiTheme.accent();
        int fA = (int) fillOpacity.getValue();
        int oA = (int) outlineOpacity.getValue();
        Color outlineColor = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), oA);
        Color fillColor    = new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), fA);

        Camera cam    = mc.gameRenderer.getMainCamera();
        Vec3  camPos = cam.position();
        float  td     = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);

        MultiBufferSource.BufferSource immediate =
                mc.renderBuffers().crumblingBufferSource();

        int playerCX = mc.player.getBlockX() >> 4;
        int playerCZ = mc.player.getBlockZ() >> 4;
        int chunkRadius = 6;

        for (int cx = playerCX - chunkRadius; cx <= playerCX + chunkRadius; cx++) {
            for (int cz = playerCZ - chunkRadius; cz <= playerCZ + chunkRadius; cz++) {
                LevelChunk chunk = mc.level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;

                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    if (!shouldRender(be)) continue;

                    double x = be.getBlockPos().getX();
                    double y = be.getBlockPos().getY();
                    double z = be.getBlockPos().getZ();
                    AABB box = new AABB(x, y, z, x + 1, y + 1, z + 1);

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

                    if (showContents.getValue() && be instanceof Container inv) {
                        renderContentLabel(event.matrices, immediate, cam, camPos,
                                x + 0.5, y + 1.15, z + 0.5, inv);
                    }
                }
            }
        }

        immediate.endBatch();
    }

    private void renderContentLabel(PoseStack matrices,
                                    MultiBufferSource.BufferSource immediate,
                                    Camera cam, Vec3 camPos,
                                    double wx, double wy, double wz,
                                    Container inv) {
        int filled = 0;
        int total  = inv.getContainerSize();
        for (int i = 0; i < total; i++) {
            ItemStack s = inv.getItem(i);
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

        matrices.pushPose();
        matrices.translate(dx, dy, dz);
        matrices.mulPose(Axis.YP.rotationDegrees(-cam.yRot()));
        matrices.mulPose(Axis.XP.rotationDegrees(cam.xRot()));
        matrices.scale(-0.02f, -0.02f, 0.02f);

        float w = mc.font.width(label) / 2f;
        mc.font.drawInBatch(
                label, -w, 0, textColor, false,
                matrices.last().pose(),
                immediate,
                Font.DisplayMode.SEE_THROUGH,
                0x40000000,
                15728880
        );
        matrices.popPose();
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
