package dev.i726.rocky.font;

import com.mojang.blaze3d.systems.RenderSystem;
import static dev.i726.rocky.Rocky.mc;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class GlyphPageFontRenderer {
	private GlyphPage regularPage;
	private GlyphPage boldPage;
	private GlyphPage italicPage;
	private GlyphPage boldItalicPage;
	private int[] colorCode = new int[32];

	public GlyphPageFontRenderer(GlyphPage regularPage, GlyphPage boldPage, GlyphPage italicPage, GlyphPage boldItalicPage) {
		this.regularPage = regularPage;
		this.boldPage = boldPage;
		this.italicPage = italicPage;
		this.boldItalicPage = boldItalicPage;

		for (int i = 0; i < 32; ++i) {
			int j = (i >> 3 & 1) * 85;
			int k = (i >> 2 & 1) * 170 + j;
			int l = (i >> 1 & 1) * 170 + j;
			int m = (i & 1) * 170 + j;

			if (i == 6) {
				k += 85;
			}

			if (i >= 16) {
				k /= 4;
				l /= 4;
				m /= 4;
			}

			this.colorCode[i] = (k & 255) << 16 | (l & 255) << 8 | m & 255;
		}
	}

	public static GlyphPageFontRenderer createFromID(String resourcePath, int size, boolean bold, boolean antiAliasing, boolean fractionalMetrics) {
		try {
			Font font = Font.createFont(Font.TRUETYPE_FONT, GlyphPageFontRenderer.class.getResourceAsStream(resourcePath));
			return create(font.deriveFont((float) size), antiAliasing, fractionalMetrics);
		} catch (Exception e) {
			return create(new Font("SansSerif", Font.PLAIN, size), antiAliasing, fractionalMetrics);
		}
	}

	public static GlyphPageFontRenderer create(Font font, boolean antiAliasing, boolean fractionalMetrics) {
		GlyphPage regularPage = new GlyphPage(font.deriveFont(Font.PLAIN), antiAliasing, fractionalMetrics);
		GlyphPage boldPage = new GlyphPage(font.deriveFont(Font.BOLD), antiAliasing, fractionalMetrics);
		GlyphPage italicPage = new GlyphPage(font.deriveFont(Font.ITALIC), antiAliasing, fractionalMetrics);
		GlyphPage boldItalicPage = new GlyphPage(font.deriveFont(Font.BOLD | Font.ITALIC), antiAliasing, fractionalMetrics);

		return new GlyphPageFontRenderer(regularPage, boldPage, italicPage, boldItalicPage);
	}

	public void drawString(DrawContext context, CharSequence text, float x, float y, int color) {
		renderString(context, text.toString(), x, y, color, false);
	}

	public void drawStringWithShadow(DrawContext context, CharSequence text, float x, float y, int color) {
		renderString(context, text.toString(), x + 1.0F, y + 1.0F, color, true);
		renderString(context, text.toString(), x, y, color, false);
	}

	private void renderString(DrawContext context, String text, float x, float y, int color, boolean shadow) {
		if (text == null) {
			return;
		}

		if (shadow) {
			color = (color & 16579836) >> 2 | color & -16777216;
		}

		float red = (float) (color >> 16 & 255) / 255.0F;
		float green = (float) (color >> 8 & 255) / 255.0F;
		float blue = (float) (color & 255) / 255.0F;
		float alpha = (float) (color >> 24 & 255) / 255.0F;

		if (alpha == 0) alpha = 1;

		GlyphPage currentPage = regularPage;
		currentPage.setupTexture();

		float currentX = x;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			currentX += currentPage.drawChar(context, c, currentX, y, red, green, blue, alpha);
		}
		mc.getBufferBuilders().getEntityVertexConsumers().draw();
	}

	public int getStringWidth(CharSequence text) {
		if (text == null) {
			return 0;
		}

		int width = 0;
		for (int i = 0; i < text.length(); ++i) {
			char c = text.charAt(i);
			width += regularPage.getWidth(c);
		}

		return width;
	}

	public int getFontHeight() {
		return regularPage.getMaxFontHeight();
	}
}