package dev.i726.rocky.utils;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import java.awt.*;

import static dev.i726.rocky.Rocky.mc;


public final class RenderUtils {
        public static boolean rendering3D = true;

        public static Vec3 getCameraPos() {
                return mc.gameRenderer.getMainCamera().position();
        }

        public static double deltaTime() {
                return mc.getFps() > 0 ? (1.0000 / mc.getFps()) : 1;
        }

        public static float fast(float end, float start, float multiple) {
                return (1 - Mth.clamp((float) (deltaTime() * multiple), 0, 1)) * end + Mth.clamp((float) (deltaTime() * multiple), 0, 1) * start;
        }

        public static Vec3 getPlayerLookVec(Player player) {
                float f = 0.017453292F;
                float pi = 3.1415927F;
                float f1 = Mth.cos(-player.getYRot() * f - pi);
                float f2 = Mth.sin(-player.getYRot() * f - pi);
                float f3 = -Mth.cos(-player.getXRot() * f);
                float f4 = Mth.sin(-player.getXRot() * f);
                return (new Vec3((f2 * f3), f4, (f1 * f3))).normalize();
        }

        public static void unscaledProjection() {}

        public static void scaledProjection() {}

        public static void drawRoundedRect(GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, float radius, int color) {
                renderRoundedQuad(context, new Color(color, true), x1, y1, x2, y2, radius, 15);
        }

        public static void renderGradientRoundedQuad(GuiGraphicsExtractor context, Color c1, Color c2, double x, double y, double x2, double y2, double radius, double samples) {
                // Bodies
                double r = Math.min(radius, Math.min((x2 - x) / 2, (y2 - y) / 2));
                
                // Main body with gradient
                context.fillGradient((int)x, (int)(y + r), (int)x2, (int)(y2 - r), c1.getRGB(), c2.getRGB());
                
                // Top and bottom bars
                context.fill((int)(x + r), (int)y, (int)(x2 - r), (int)(y + r), c1.getRGB());
                context.fill((int)(x + r), (int)(y2 - r), (int)(x2 - r), (int)y2, c2.getRGB());

                // Corners (simplified for UI stability)
                if (r > 0) {
                        renderRoundedCorner(context, c1, x + r, y + r, r, 180, samples);
                        renderRoundedCorner(context, c1, x2 - r, y + r, r, 270, samples);
                        renderRoundedCorner(context, c2, x2 - r, y2 - r, r, 0, samples);
                        renderRoundedCorner(context, c2, x + r, y2 - r, r, 90, samples);
                }
        }

        private static void renderRoundedCorner(GuiGraphicsExtractor context, Color c, double cx, double cy, double r, double startAngle, double samples) {
                int color = c.getRGB();
                double step = 90.0 / samples;
                for (double i = 0; i < 90; i += step) {
                        double r1 = Math.toRadians(startAngle + i);
                        double r2 = Math.toRadians(startAngle + Math.min(i + step, 90.0));
                        int x1 = (int)(cx + Math.sin(r1) * r);
                        int y1 = (int)(cy + Math.cos(r1) * r);
                        int x2 = (int)(cx + Math.sin(r2) * r);
                        int y2 = (int)(cy + Math.cos(r2) * r);
                        context.fill(Math.min(x1, x2), Math.min(y1, y2),
                                     Math.max(x1, x2) + 1, Math.max(y1, y2) + 1, color);
                }
        }

        public static void renderRoundedQuad(GuiGraphicsExtractor context, Color c, double x, double y, double x2, double y2, double topLeft, double topRight, double bottomRight, double bottomLeft, double samples) {
                int color = c.getRGB();
                double rTL = Math.min(topLeft, Math.min((x2 - x) / 2, (y2 - y) / 2));
                double rTR = Math.min(topRight, Math.min((x2 - x) / 2, (y2 - y) / 2));
                double rBR = Math.min(bottomRight, Math.min((x2 - x) / 2, (y2 - y) / 2));
                double rBL = Math.min(bottomLeft, Math.min((x2 - x) / 2, (y2 - y) / 2));

                double maxR = Math.max(Math.max(rTL, rTR), Math.max(rBR, rBL));
                
                // Fill body
                context.fill((int)x, (int)(y + maxR), (int)x2, (int)(y2 - maxR), color);
                context.fill((int)(x + maxR), (int)y, (int)(x2 - maxR), (int)(y + maxR), color);
                context.fill((int)(x + maxR), (int)(y2 - maxR), (int)(x2 - maxR), (int)y2, color);

                // Corners
                if (rTL > 0) renderRoundedCorner(context, c, x + rTL, y + rTL, rTL, 180, samples);
                if (rTR > 0) renderRoundedCorner(context, c, x2 - rTR, y + rTR, rTR, 270, samples);
                if (rBR > 0) renderRoundedCorner(context, c, x2 - rBR, y2 - rBR, rBR, 0, samples);
                if (rBL > 0) renderRoundedCorner(context, c, x + rBL, y2 - rBL, rBL, 90, samples);
        }

        public static void renderRoundedQuad(GuiGraphicsExtractor context, Color c, double x, double y, double x1, double y1, double rad, double samples) {
                renderRoundedQuad(context, c, x, y, x1, y1, rad, rad, rad, rad, samples);
        }

        public static void renderRoundedOutline(GuiGraphicsExtractor context, Color c, double x, double y, double x2, double y2, double topLeft, double topRight, double bottomRight, double bottomLeft, double width, double samples) {
                int color = c.getRGB();
                // Straight sides
                context.fill((int)x, (int)(y + topLeft), (int)(x + width), (int)(y2 - bottomLeft), color);
                context.fill((int)(x2 - width), (int)(y + topRight), (int)x2, (int)(y2 - bottomRight), color);
                context.fill((int)(x + topLeft), (int)y, (int)(x2 - topRight), (int)(y + width), color);
                context.fill((int)(x + bottomLeft), (int)(y2 - width), (int)(x2 - bottomRight), (int)y2, color);
                // Corner arcs approximated with fills
                double step = 90.0 / samples;
                double[][] corners = {
                        {x2 - bottomRight, y2 - bottomRight, bottomRight, 0},
                        {x + bottomLeft,   y2 - bottomLeft,  bottomLeft,  90},
                        {x + topLeft,      y + topLeft,      topLeft,     180},
                        {x2 - topRight,    y + topRight,     topRight,    270}
                };
                for (double[] corner : corners) {
                        double cx = corner[0], cy = corner[1], rad = corner[2], base = corner[3];
                        for (double a = base; a < base + 90; a += step) {
                                double r1 = Math.toRadians(a);
                                double r2 = Math.toRadians(Math.min(a + step, base + 90));
                                int ox1 = (int)(cx + Math.sin(r1) * rad), oy1 = (int)(cy + Math.cos(r1) * rad);
                                int ox2 = (int)(cx + Math.sin(r2) * rad), oy2 = (int)(cy + Math.cos(r2) * rad);
                                context.fill(Math.min(ox1, ox2), Math.min(oy1, oy2),
                                             Math.max(ox1, ox2) + (int)width + 1, Math.max(oy1, oy2) + (int)width + 1, color);
                        }
                }
        }

        public static void renderCircle(org.joml.Matrix3x2fStack matrices, Color c, double originX, double originY, double rad, int segments) {
                // No-op: full 3D/VBO circle rendering removed in MC 26.1.2
        }

        public static void renderLine(PoseStack matrices, Color color, Vec3 start, Vec3 end) {
                // No-op: RenderType.debugLineStrip removed in MC 26.1.2 rendering overhaul
        }

        public static void renderFilledBox(PoseStack matrices, double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
                // No-op: RenderType.debugFilledBox removed in MC 26.1.2 rendering overhaul
        }

        public static void drawOutlinedBox(PoseStack matrices, net.minecraft.world.phys.AABB box, Color color) {
                // No-op: RenderType.lines and DebugRenderer.renderFilledBox removed in MC 26.1.2
        }

        public static void renderBoxWithTracers(PoseStack matrices, net.minecraft.world.entity.Entity entity, Color color) {
                net.minecraft.world.phys.AABB box = entity.getBoundingBox();
                drawOutlinedBox(matrices, box, color);
                renderFilledBox(matrices, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, new Color(color.getRed(), color.getGreen(), color.getBlue(), 50));
                drawTracer(matrices, entity.getBoundingBox().getCenter(), color);
        }

        public static void drawTracer(PoseStack matrices, Vec3 end, Color color) {
                // Do NOT start from the exact camera position: in perspective projection
                // a vertex at the eye origin has w_clip = 0 (perspective divide by zero),
                // which the GPU treats as a degenerate point and may discard the primitive.
                // Shift 0.1 blocks forward along the look direction so z_view < 0.
                net.minecraft.client.Camera cam = mc.gameRenderer.getMainCamera();
                Vec3 forward = Vec3.directionFromRotation(cam.xRot(), cam.yRot());
                Vec3 origin = cam.position().add(forward.scale(0.1));
                renderLine(matrices, color, origin, end);
        }

        public static void setScissorRegion(GuiGraphicsExtractor context, int x, int y, int width, int height) {
                context.enableScissor(x, y, x + width, y + height);
        }

        public static void disableScissor(GuiGraphicsExtractor context) {
                context.disableScissor();
        }

        public static void renderNeonQuad(GuiGraphicsExtractor context, Color base, Color glow, double x, double y, double x2, double y2, double radius) {
                // Subtle Glow (Single layer)
                renderRoundedOutline(context, new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 30), x - 1, y - 1, x2 + 1, y2 + 1, radius + 1, radius + 1, radius + 1, radius + 1, 1.5, 15);
                
                // Base Quad
                renderRoundedQuad(context, base, x, y, x2, y2, radius, 15);
                
                // Subtle Border
                renderRoundedOutline(context, new Color(255, 255, 255, 15), x, y, x2, y2, radius, radius, radius, radius, 0.5, 15);
        }

        public static void renderPremiumShadow(GuiGraphicsExtractor context, double x, double y, double x2, double y2, double radius) {
                // Single soft shadow layer
                renderRoundedQuad(context, new Color(0, 0, 0, 40), x + 2, y + 2, x2 + 4, y2 + 4, radius + 2, 10);
        }

        public static void renderAccentLine(GuiGraphicsExtractor context, Color start, Color end, double x, double y, double x2, double y2) {
                context.fillGradient((int)x, (int)y, (int)x2, (int)y2, start.getRGB(), end.getRGB());
                // Glow for the line
                context.fillGradient((int)x - 1, (int)y, (int)x, (int)y2, new Color(start.getRed(), start.getGreen(), start.getBlue(), 40).getRGB(), new Color(end.getRed(), end.getGreen(), end.getBlue(), 40).getRGB());
                context.fillGradient((int)x2, (int)y, (int)x2 + 1, (int)y2, new Color(start.getRed(), start.getGreen(), start.getBlue(), 40).getRGB(), new Color(end.getRed(), end.getGreen(), end.getBlue(), 40).getRGB());
        }

        public static void renderSwitch(GuiGraphicsExtractor context, boolean enabled, float animation, double x, double y, double width, double height, Color accent) {
                // Track
                Color trackColor = enabled ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int)(100 + 100 * animation)) : new Color(50, 50, 55, 200);
                renderRoundedQuad(context, trackColor, x, y, x + width, y + height, height / 2, 15);
                
                // Thumb
                double thumbSize = height - 4;
                double thumbX = x + 2 + (width - thumbSize - 4) * animation;
                Color thumbColor = enabled ? Color.WHITE : new Color(180, 185, 195);
                
                renderRoundedQuad(context, thumbColor, thumbX, y + 2, thumbX + thumbSize, y + height - 2, thumbSize / 2, 15);
                
                if (enabled && animation > 0.5) {
                        // Subtle glow for enabled thumb
                        renderRoundedOutline(context, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int)(50 * (animation - 0.5) * 2)), thumbX - 1, y + 1, thumbX + thumbSize + 1, y + height - 1, thumbSize / 2, thumbSize / 2, thumbSize / 2, thumbSize / 2, 1, 15);
                }
        }
}