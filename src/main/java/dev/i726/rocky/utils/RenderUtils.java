package dev.i726.rocky.utils;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.render.*;
import net.minecraft.client.render.debug.DebugRenderer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix3x2f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

import java.awt.*;

import static dev.i726.rocky.Rocky.mc;


public final class RenderUtils {
        public static boolean rendering3D = true;

        public static Vec3d getCameraPos() {
                return mc.gameRenderer.getCamera().getPos();
        }

        public static double deltaTime() {
                return mc.getCurrentFps() > 0 ? (1.0000 / mc.getCurrentFps()) : 1;
        }

        public static float fast(float end, float start, float multiple) {
                return (1 - MathHelper.clamp((float) (deltaTime() * multiple), 0, 1)) * end + MathHelper.clamp((float) (deltaTime() * multiple), 0, 1) * start;
        }

        public static Vec3d getPlayerLookVec(PlayerEntity player) {
                float f = 0.017453292F;
                float pi = 3.1415927F;
                float f1 = MathHelper.cos(-player.getYaw() * f - pi);
                float f2 = MathHelper.sin(-player.getYaw() * f - pi);
                float f3 = -MathHelper.cos(-player.getPitch() * f);
                float f4 = MathHelper.sin(-player.getPitch() * f);
                return (new Vec3d((f2 * f3), f4, (f1 * f3))).normalize();
        }

        public static void unscaledProjection() {}

        public static void scaledProjection() {}

        public static void drawRoundedRect(DrawContext context, float x1, float y1, float x2, float y2, float radius, int color) {
                renderRoundedQuad(context, new Color(color, true), x1, y1, x2, y2, radius, 15);
        }

        public static void renderGradientRoundedQuad(DrawContext context, Color c1, Color c2, double x, double y, double x2, double y2, double radius, double samples) {
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

        private static void renderRoundedCorner(DrawContext context, Color c, double cx, double cy, double r, double startAngle, double samples) {
                org.joml.Matrix3x2f matrix = context.getMatrices();
                VertexConsumer bufferBuilder = mc.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayer.getDebugTriangleFan());
                float f = c.getAlpha() / 255f, g = c.getRed() / 255f, h = c.getGreen() / 255f, k = c.getBlue() / 255f;
                
                bufferBuilder.vertex(matrix, (float)cx, (float)cy).color(g, h, k, f);
                for (double i = 0; i <= 90; i += (90 / samples)) {
                        double radians = Math.toRadians(startAngle + i);
                        bufferBuilder.vertex(matrix, (float)(cx + Math.sin(radians) * r), (float)(cy + Math.cos(radians) * r)).color(g, h, k, f);
                }
        }

        public static void renderRoundedQuad(DrawContext context, Color c, double x, double y, double x2, double y2, double topLeft, double topRight, double bottomRight, double bottomLeft, double samples) {
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

        public static void renderRoundedQuad(DrawContext context, Color c, double x, double y, double x1, double y1, double rad, double samples) {
                renderRoundedQuad(context, c, x, y, x1, y1, rad, rad, rad, rad, samples);
        }

        public static void renderRoundedOutline(DrawContext context, Color c, double x, double y, double x2, double y2, double topLeft, double topRight, double bottomRight, double bottomLeft, double width, double samples) {
                int color = c.getRGB();
                org.joml.Matrix3x2f matrix = context.getMatrices();
                float f = c.getAlpha() / 255f, g = c.getRed() / 255f, h = c.getGreen() / 255f, k = c.getBlue() / 255f;
                
                VertexConsumer bufferBuilder = mc.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayer.getDebugQuads());
                
                // Map corners: BR (0), BL (90), TL (180), TR (270)
                double[][] map = new double[][]{
                                {x2 - bottomRight, y2 - bottomRight, bottomRight}, 
                                {x + bottomLeft, y2 - bottomLeft, bottomLeft},
                                {x + topLeft, y + topLeft, topLeft}, 
                                {x2 - topRight, y + topRight, topRight}
                };
                
                for (int i = 0; i < 4; i++) {
                        double[] current = map[i];
                        double rad = current[2];
                        for (double r = i * 90d; r < (90 + i * 90d); r += (90 / samples)) {
                                double rad_next = Math.min(90 + i * 90d, r + (90 / samples));
                                
                                float s1 = (float) Math.sin(Math.toRadians(r));
                                float c1 = (float) Math.cos(Math.toRadians(r));
                                float s2 = (float) Math.sin(Math.toRadians(rad_next));
                                float c2 = (float) Math.cos(Math.toRadians(rad_next));
                                
                                bufferBuilder.vertex(matrix, (float)current[0] + s1 * (float)rad, (float)current[1] + c1 * (float)rad).color(g, h, k, f);
                                bufferBuilder.vertex(matrix, (float)current[0] + s1 * (float)(rad + width), (float)current[1] + c1 * (float)(rad + width)).color(g, h, k, f);
                                bufferBuilder.vertex(matrix, (float)current[0] + s2 * (float)(rad + width), (float)current[1] + c2 * (float)(rad + width)).color(g, h, k, f);
                                bufferBuilder.vertex(matrix, (float)current[0] + s2 * (float)rad, (float)current[1] + c2 * (float)rad).color(g, h, k, f);
                        }
                }
                
                // Fill sides
                context.fill((int)x, (int)(y + topLeft), (int)(x + width), (int)(y2 - bottomLeft), color);
                context.fill((int)(x2 - width), (int)(y + topRight), (int)x2, (int)(y2 - bottomRight), color);
                context.fill((int)(x + topLeft), (int)y, (int)(x2 - topRight), (int)(y + width), color);
                context.fill((int)(x + bottomLeft), (int)(y2 - width), (int)(x2 - bottomRight), (int)y2, color);
        }

        public static void renderCircle(org.joml.Matrix3x2fStack matrices, Color c, double originX, double originY, double rad, int segments) {
                int segments1 = MathHelper.clamp(segments, 4, 360);
                int color = c.getRGB();

                org.joml.Matrix3x2f matrix = matrices;
                float f = (float) (color >> 24 & 255) / 255.0F;
                float g = (float) (color >> 16 & 255) / 255.0F;
                float h = (float) (color >> 8 & 255) / 255.0F;
                float k = (float) (color & 255) / 255.0F;

                VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
                VertexConsumer bufferBuilder = immediate.getBuffer(RenderLayer.getDebugTriangleFan());
                for (int i = 0; i < 360; i += Math.min(360 / segments1, 360 - i)) {
                        double radians = Math.toRadians(i);
                        double sin = Math.sin(radians) * rad;
                        double cos = Math.cos(radians) * rad;
                        bufferBuilder.vertex(matrix, (float) (originX + sin), (float) (originY + cos)).color(g, h, k, f);
                }
                immediate.draw();
        }

        public static void renderLine(MatrixStack matrices, Color color, Vec3d start, Vec3d end) {
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                VertexConsumer buffer = mc.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayer.getLines());
                Vec3d cam = getCameraPos();

                float r = color.getRed() / 255f;
                float g = color.getGreen() / 255f;
                float b = color.getBlue() / 255f;
                float a = color.getAlpha() / 255f;

                float sx = (float)(start.x - cam.x);
                float sy = (float)(start.y - cam.y);
                float sz = (float)(start.z - cam.z);
                float ex = (float)(end.x - cam.x);
                float ey = (float)(end.y - cam.y);
                float ez = (float)(end.z - cam.z);

                // Compute line direction, then cross with world-up to get a proper
                // screen-space normal. Fall back to world-right if the line is vertical.
                float dx = ex - sx, dy = ey - sy, dz = ez - sz;
                float len = (float) Math.sqrt(dx*dx + dy*dy + dz*dz);
                if (len < 1e-6f) return;
                dx /= len; dy /= len; dz /= len;

                // cross(dir, up=(0,1,0)) = (dz, 0, -dx)
                float nx = dz, ny = 0f, nz = -dx;
                float nLen = (float) Math.sqrt(nx*nx + nz*nz);
                if (nLen < 1e-6f) { nx = 1f; ny = 0f; nz = 0f; } // line is vertical, use right
                else { nx /= nLen; nz /= nLen; }

                buffer.vertex(matrix, sx, sy, sz).color(r, g, b, a).normal(matrices.peek(), nx, ny, nz);
                buffer.vertex(matrix, ex, ey, ez).color(r, g, b, a).normal(matrices.peek(), nx, ny, nz);
        }

        public static void renderFilledBox(MatrixStack matrices, double x1, double y1, double z1, double x2, double y2, double z2, Color color) {
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                VertexConsumer buffer = mc.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayer.getDebugFilledBox());
                Vec3d cam = getCameraPos();
                
                float r = color.getRed() / 255f;
                float g = color.getGreen() / 255f;
                float b = color.getBlue() / 255f;
                float a = color.getAlpha() / 255f;
                
                float minX = (float) (x1 - cam.x);
                float minY = (float) (y1 - cam.y);
                float minZ = (float) (z1 - cam.z);
                float maxX = (float) (x2 - cam.x);
                float maxY = (float) (y2 - cam.y);
                float maxZ = (float) (z2 - cam.z);

                // Box sides
                buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a);
                buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a);
                buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a);
                buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a);
                
                buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a);
                buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a);
                buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a);
                buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a);
                
                buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a);
                buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a);
                buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a);
                buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a);
                
                buffer.vertex(matrix, maxX, minY, minZ).color(r, g, b, a);
                buffer.vertex(matrix, maxX, maxY, minZ).color(r, g, b, a);
                buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a);
                buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a);
                
                buffer.vertex(matrix, maxX, minY, maxZ).color(r, g, b, a);
                buffer.vertex(matrix, maxX, maxY, maxZ).color(r, g, b, a);
                buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a);
                buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a);
                
                buffer.vertex(matrix, minX, minY, maxZ).color(r, g, b, a);
                buffer.vertex(matrix, minX, maxY, maxZ).color(r, g, b, a);
                buffer.vertex(matrix, minX, maxY, minZ).color(r, g, b, a);
                buffer.vertex(matrix, minX, minY, minZ).color(r, g, b, a);
        }

        public static void drawOutlinedBox(MatrixStack matrices, net.minecraft.util.math.Box box, Color color) {
                Matrix4f matrix = matrices.peek().getPositionMatrix();
                VertexConsumer buffer = mc.getBufferBuilders().getEntityVertexConsumers().getBuffer(RenderLayer.getLines());
                Vec3d cam = getCameraPos();
                
                float r = color.getRed() / 255f;
                float g = color.getGreen() / 255f;
                float b = color.getBlue() / 255f;
                float a = color.getAlpha() / 255f;
                
                DebugRenderer.drawBox(matrices, mc.getBufferBuilders().getEntityVertexConsumers(), box.minX - cam.x, box.minY - cam.y, box.minZ - cam.z, box.maxX - cam.x, box.maxY - cam.y, box.maxZ - cam.z, r, g, b, a);
        }

        public static void renderBoxWithTracers(MatrixStack matrices, net.minecraft.entity.Entity entity, Color color) {
                net.minecraft.util.math.Box box = entity.getBoundingBox();
                drawOutlinedBox(matrices, box, color);
                renderFilledBox(matrices, box.minX, box.minY, box.minZ, box.maxX, box.maxY, box.maxZ, new Color(color.getRed(), color.getGreen(), color.getBlue(), 50));
                drawTracer(matrices, entity.getBoundingBox().getCenter(), color);
        }

        public static void drawTracer(MatrixStack matrices, Vec3d end, Color color) {
                Vec3d start = new Vec3d(0, 0, 1)
                                .rotateX(-(float) Math.toRadians(mc.player.getPitch()))
                                .rotateY(-(float) Math.toRadians(mc.player.getYaw()))
                                .add(getCameraPos());
                renderLine(matrices, color, start, end);
        }

        public static void setScissorRegion(DrawContext context, int x, int y, int width, int height) {
                context.enableScissor(x, y, x + width, y + height);
        }

        public static void disableScissor(DrawContext context) {
                context.disableScissor();
        }

        public static void renderNeonQuad(DrawContext context, Color base, Color glow, double x, double y, double x2, double y2, double radius) {
                // Subtle Glow (Single layer)
                renderRoundedOutline(context, new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 30), x - 1, y - 1, x2 + 1, y2 + 1, radius + 1, radius + 1, radius + 1, radius + 1, 1.5, 15);
                
                // Base Quad
                renderRoundedQuad(context, base, x, y, x2, y2, radius, 15);
                
                // Subtle Border
                renderRoundedOutline(context, new Color(255, 255, 255, 15), x, y, x2, y2, radius, radius, radius, radius, 0.5, 15);
        }

        public static void renderPremiumShadow(DrawContext context, double x, double y, double x2, double y2, double radius) {
                // Single soft shadow layer
                renderRoundedQuad(context, new Color(0, 0, 0, 40), x + 2, y + 2, x2 + 4, y2 + 4, radius + 2, 10);
        }

        public static void renderAccentLine(DrawContext context, Color start, Color end, double x, double y, double x2, double y2) {
                context.fillGradient((int)x, (int)y, (int)x2, (int)y2, start.getRGB(), end.getRGB());
                // Glow for the line
                context.fillGradient((int)x - 1, (int)y, (int)x, (int)y2, new Color(start.getRed(), start.getGreen(), start.getBlue(), 40).getRGB(), new Color(end.getRed(), end.getGreen(), end.getBlue(), 40).getRGB());
                context.fillGradient((int)x2, (int)y, (int)x2 + 1, (int)y2, new Color(start.getRed(), start.getGreen(), start.getBlue(), 40).getRGB(), new Color(end.getRed(), end.getGreen(), end.getBlue(), 40).getRGB());
        }

        public static void renderSwitch(DrawContext context, boolean enabled, float animation, double x, double y, double width, double height, Color accent) {
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