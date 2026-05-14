package dev.i726.rocky.module.modules.render;
import dev.i726.rocky.gui.GuiTheme;

import dev.i726.rocky.event.events.GameRenderListener;
import dev.i726.rocky.module.CategoryManager;
import dev.i726.rocky.module.Module;
import dev.i726.rocky.module.setting.BooleanSetting;
import dev.i726.rocky.module.setting.NumberSetting;
import dev.i726.rocky.utils.EncryptedString;
import dev.i726.rocky.utils.RenderUtils;
import net.minecraft.client.render.Camera;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public final class PlayerESP extends Module implements GameRenderListener {
        private final NumberSetting opacity = new NumberSetting(EncryptedString.of("Opacity"), 0, 255, 40, 1);
        private final NumberSetting range = new NumberSetting(EncryptedString.of("Range"), 10, 500, 100, 10);
        private final BooleanSetting tracers = new BooleanSetting(EncryptedString.of("Tracers"), false)
                        .setDescription(EncryptedString.of("Draws a line from your player to the other"));
        private final NumberSetting tracerOpacity = new NumberSetting(EncryptedString.of("Tracer Opacity"), 50, 255, 180, 5)
                        .setDescription(EncryptedString.of("Opacity of tracer lines"));

        public PlayerESP() {
                super(EncryptedString.of("Player ESP"),
                EncryptedString.of("Highlights players"),
                                -1,
                                CategoryManager.ESP);
                addSettings(opacity, range, tracers, tracerOpacity);
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
                if (mc.player == null || mc.world == null) return;

                Camera cam = mc.gameRenderer.getCamera();
                if (cam == null) return;

                MatrixStack matrices = event.matrices;
                float tickDelta = mc.getRenderTickCounter().getTickProgress(true);

                GL11.glDisable(GL11.GL_DEPTH_TEST);
                matrices.push();

                for (PlayerEntity player : mc.world.getPlayers()) {
                        if (player == mc.player || player.isDead() || player.isRemoved()) continue;
                        if (mc.player.distanceTo(player) > range.getValue()) continue;

                        // Lerped position for smooth interpolation between ticks
                        Vec3d pos = player.getLerpedPos(tickDelta);
                        Vec3d rawPos = new Vec3d(player.getX(), player.getY(), player.getZ());
                        Vec3d lerpDelta = pos.subtract(rawPos);

                        // Build the lerped bounding box by offsetting the server-tick box
                        Box lerpedBox = player.getBoundingBox().offset(lerpDelta);

                        // Subtle fill — low default opacity so players remain visible through it
                        RenderUtils.renderFilledBox(matrices,
                                lerpedBox.minX, lerpedBox.minY, lerpedBox.minZ,
                                lerpedBox.maxX, lerpedBox.maxY, lerpedBox.maxZ,
                                getColor(opacity.getValueInt()));

                        // Solid outline on the lerped box
                        RenderUtils.drawOutlinedBox(matrices, lerpedBox,
                                getColor(Math.min(255, opacity.getValueInt() + 130)));

                        if (tracers.getValue()) {
                                Vec3d target = pos.add(0, player.getHeight() / 2.0, 0);
                                RenderUtils.renderLine(matrices, getColor(tracerOpacity.getValueInt()), cam.getPos(), target);
                        }
                }

                GL11.glEnable(GL11.GL_DEPTH_TEST);
                matrices.pop();
        }

        private Color getColor(int alpha) {
                Color a = GuiTheme.accent();
                return new Color(a.getRed(), a.getGreen(), a.getBlue(), alpha);
        }
}
