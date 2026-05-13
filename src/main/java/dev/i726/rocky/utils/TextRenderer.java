package dev.i726.rocky.utils;

import dev.i726.rocky.font.Fonts;
import dev.i726.rocky.module.modules.client.ClickGUI;
import net.minecraft.client.gui.DrawContext;

import static dev.i726.rocky.Rocky.mc;

public final class TextRenderer {

	public static void drawString(CharSequence string, DrawContext context, int x, int y, int color) {
		context.drawText(mc.textRenderer, string.toString(), x, y, color, true);
	}

	public static int getWidth(CharSequence string) {
		return mc.textRenderer.getWidth(string.toString());
	}

	public static void drawCenteredString(CharSequence string, DrawContext context, int x, int y, int color) {
		int textWidth = mc.textRenderer.getWidth(string.toString());
		context.drawText(mc.textRenderer, string.toString(), x - textWidth / 2, y, color, true);
	}

	public static void drawLargeString(CharSequence string, DrawContext context, int x, int y, int color) {
		org.joml.Matrix3x2fStack matrices = context.getMatrices();
		matrices.pushMatrix();
		matrices.scale(2f, 2f);
		context.drawText(mc.textRenderer, string.toString(), x / 2, y / 2, color, true);
		matrices.popMatrix();
	}
}
