package dev.i726.rocky.utils;

import net.minecraft.client.Camera;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;
import org.joml.Vector4f;

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

        /**
         * Projects a world-space position to GUI screen coordinates using the real GPU
         * projection matrix captured from renderLevel — this handles sprint-FOV, bow-zoom,
         * cinematic camera, and any other in-game FOV modifiers that mc.options.fov() misses.
         *
         * @param worldPos   world-space position to project
         * @param viewRot    view-rotation matrix from event.matrices.last().pose()
         * @param projMatrix real projection matrix from event.projMatrix
         * @param camPos     camera position from mc.gameRenderer.getMainCamera().position()
         * @param winW       GUI-scaled window width
         * @param winH       GUI-scaled window height
         * @return int[2] {screenX, screenY} in GUI-scaled pixels, or null if behind the camera.
         */
        public static int[] projectToScreen(Vec3 worldPos, Matrix4f viewRot, Matrix4f projMatrix,
                                            Vec3 camPos, int winW, int winH) {
                float rx = (float)(worldPos.x - camPos.x);
                float ry = (float)(worldPos.y - camPos.y);
                float rz = (float)(worldPos.z - camPos.z);

                // Camera-relative → eye space
                Vector4f eye = viewRot.transform(new Vector4f(rx, ry, rz, 1f));

                // Eye space → clip space using the real projection matrix
                Vector4f clip = new Matrix4f(projMatrix).transform(eye);
                if (clip.w <= 0.001f) return null;

                float ndcX = clip.x / clip.w;
                float ndcY = clip.y / clip.w;

                int sx = (int)((ndcX + 1f) * 0.5f * winW);
                int sy = (int)((1f - ndcY) * 0.5f * winH);
                return new int[]{ sx, sy };
        }

        /**
         * Draws a 1-pixel wide line using a 2D matrix rotation — one fill call total.
         * Replaces the old per-pixel Bresenham loop which cost O(length) fill calls.
         */
        public static void drawLine2D(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
                float dx  = x2 - x1;
                float dy  = y2 - y1;
                float len = (float) Math.sqrt(dx * dx + dy * dy);
                if (len < 1f) return;
                org.joml.Matrix3x2fStack pose = ctx.pose();
                pose.pushMatrix();
                pose.translate(x1, y1);
                pose.rotate((float) Math.atan2(dy, dx));
                ctx.fill(0, -1, (int) len, 1, color);
                pose.popMatrix();
        }

        /**
         * Draws a hollow rectangle outline — one native call instead of four Bresenham loops.
         */
        public static void drawRect2D(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
                ctx.outline(x1, y1, x2, y2, color);
        }

        // ── Legacy 3D helpers (no-ops – RenderType.lines/debugLineStrip removed in 26.1.2) ──

        public static void renderLine(PoseStack matrices, Color color, Vec3 start, Vec3 end) {
                // Not supported in MC 26.1.2 (RenderType.debugLineStrip removed).
                // Tracers/ESP use 2D screen-projection via GuiGraphicsExtractor instead.
        }

        public static void renderFilledBox(PoseStack matrices, double x1, double y1, double z1,
                                           double x2, double y2, double z2, Color color) {
                // Not supported in MC 26.1.2 (RenderType.debugFilledBox removed).
        }

        public static void drawOutlinedBox(PoseStack matrices, net.minecraft.world.phys.AABB box, Color color) {
                // Not supported in MC 26.1.2 (RenderType.lines removed).
        }

        public static void renderBoxWithTracers(PoseStack matrices, net.minecraft.world.entity.Entity entity, Color color) {
                // 3D box rendering not available in MC 26.1.2 — use 2D approach.
        }

        public static void drawTracer(PoseStack matrices, Vec3 end, Color color) {
                // 3D line rendering not available in MC 26.1.2 — use 2D approach.
        }

        public static void renderCircle(org.joml.Matrix3x2fStack matrices, Color c, double originX, double originY, double rad, int segments) {
                // No-op: full 3D/VBO circle rendering removed in MC 26.1.2
        }

        // ── 2D GUI helpers ────────────────────────────────────────────────────────

        public static void setScissorRegion(GuiGraphicsExtractor context, int x, int y, int width, int height) {
                context.enableScissor(x, y, x + width, y + height);
        }

        public static void disableScissor(GuiGraphicsExtractor context) {
                context.disableScissor();
        }

        public static void drawRoundedRect(GuiGraphicsExtractor context, float x1, float y1, float x2, float y2, float radius, int color) {
                renderRoundedQuad(context, new Color(color, true), x1, y1, x2, y2, radius, 15);
        }

        public static void renderGradientRoundedQuad(GuiGraphicsExtractor context, Color c1, Color c2, double x, double y, double x2, double y2, double radius, double samples) {
                double r = Math.min(radius, Math.min((x2 - x) / 2, (y2 - y) / 2));
                context.fillGradient((int)x, (int)(y + r), (int)x2, (int)(y2 - r), c1.getRGB(), c2.getRGB());
                context.fill((int)(x + r), (int)y, (int)(x2 - r), (int)(y + r), c1.getRGB());
                context.fill((int)(x + r), (int)(y2 - r), (int)(x2 - r), (int)y2, c2.getRGB());
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
                context.fill((int)x, (int)(y + maxR), (int)x2, (int)(y2 - maxR), color);
                context.fill((int)(x + maxR), (int)y, (int)(x2 - maxR), (int)(y + maxR), color);
                context.fill((int)(x + maxR), (int)(y2 - maxR), (int)(x2 - maxR), (int)y2, color);

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
                context.fill((int)x, (int)(y + topLeft), (int)(x + width), (int)(y2 - bottomLeft), color);
                context.fill((int)(x2 - width), (int)(y + topRight), (int)x2, (int)(y2 - bottomRight), color);
                context.fill((int)(x + topLeft), (int)y, (int)(x2 - topRight), (int)(y + width), color);
                context.fill((int)(x + bottomLeft), (int)(y2 - width), (int)(x2 - bottomRight), (int)y2, color);
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

        public static void renderNeonQuad(GuiGraphicsExtractor context, Color base, Color glow, double x, double y, double x2, double y2, double radius) {
                renderRoundedOutline(context, new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 30), x - 1, y - 1, x2 + 1, y2 + 1, radius + 1, radius + 1, radius + 1, radius + 1, 1.5, 15);
                renderRoundedQuad(context, base, x, y, x2, y2, radius, 15);
                renderRoundedOutline(context, new Color(255, 255, 255, 15), x, y, x2, y2, radius, radius, radius, radius, 0.5, 15);
        }

        public static void renderPremiumShadow(GuiGraphicsExtractor context, double x, double y, double x2, double y2, double radius) {
                renderRoundedQuad(context, new Color(0, 0, 0, 40), x + 2, y + 2, x2 + 4, y2 + 4, radius + 2, 10);
        }

        public static void renderAccentLine(GuiGraphicsExtractor context, Color start, Color end, double x, double y, double x2, double y2) {
                context.fillGradient((int)x, (int)y, (int)x2, (int)y2, start.getRGB(), end.getRGB());
                context.fillGradient((int)x - 1, (int)y, (int)x, (int)y2, new Color(start.getRed(), start.getGreen(), start.getBlue(), 40).getRGB(), new Color(end.getRed(), end.getGreen(), end.getBlue(), 40).getRGB());
                context.fillGradient((int)x2, (int)y, (int)x2 + 1, (int)y2, new Color(start.getRed(), start.getGreen(), start.getBlue(), 40).getRGB(), new Color(end.getRed(), end.getGreen(), end.getBlue(), 40).getRGB());
        }

        public static void renderSwitch(GuiGraphicsExtractor context, boolean enabled, float animation, double x, double y, double width, double height, Color accent) {
                Color trackColor = enabled ? new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int)(100 + 100 * animation)) : new Color(50, 50, 55, 200);
                renderRoundedQuad(context, trackColor, x, y, x + width, y + height, height / 2, 15);
                double thumbSize = height - 4;
                double thumbX = x + 2 + (width - thumbSize - 4) * animation;
                Color thumbColor = enabled ? Color.WHITE : new Color(180, 185, 195);
                renderRoundedQuad(context, thumbColor, thumbX, y + 2, thumbX + thumbSize, y + height - 2, thumbSize / 2, 15);
                if (enabled && animation > 0.5) {
                        renderRoundedOutline(context, new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), (int)(50 * (animation - 0.5) * 2)), thumbX - 1, y + 1, thumbX + thumbSize + 1, y + height - 1, thumbSize / 2, thumbSize / 2, thumbSize / 2, thumbSize / 2, 1, 15);
                }
        }
}
