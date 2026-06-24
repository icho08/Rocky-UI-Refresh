package dev.i726.rocky.utils;

import dev.i726.rocky.module.modules.client.ClickGUI;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import static dev.i726.rocky.Rocky.mc;

public final class TextRenderer {

	public static void text(CharSequence string, GuiGraphicsExtractor context, int x, int y, int color) {
                context.text(mc.font, string.toString(), x, y, color, true);
        }

        public static int getWidth(CharSequence string) {
                return mc.font.width(string.toString());
        }

        public static void drawCenteredString(CharSequence string, GuiGraphicsExtractor context, int x, int y, int color) {
                int textWidth = mc.font.width(string.toString());
                context.text(mc.font, string.toString(), x - textWidth / 2, y, color, true);
        }

        public static void drawLargeString(CharSequence string, GuiGraphicsExtractor context, int x, int y, int color) {
                org.joml.Matrix3x2fStack matrices = context.pose();
                matrices.pushMatrix();
                matrices.scale(2f, 2f);
                context.text(mc.font, string.toString(), x / 2, y / 2, color, true);
                matrices.popMatrix();
        }
}
