package dev.i726.rocky.font;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.*;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.util.Identifier;
import static dev.i726.rocky.Rocky.mc;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import javax.imageio.ImageIO;

public class GlyphPage {
	private int imgSize;
	private int maxFontHeight = -1;
	private BufferedImage bufferedImage;
	private Glyph[] glyphCharacterMap = new Glyph[256];
	private NativeImageBackedTexture texture;
	private Identifier id;

	public GlyphPage(Font font, boolean antiAliasing, boolean fractionalMetrics) {
		this.imgSize = 1024;
		this.id = Identifier.of("rocky", "font_page_" + font.getName().toLowerCase().replace(" ", "_") + "_" + font.getStyle() + "_" + font.getSize());
		this.bufferedImage = new BufferedImage(imgSize, imgSize, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = (Graphics2D) bufferedImage.getGraphics();
		g.setFont(font);
		g.setColor(new Color(255, 255, 255, 0));
		g.fillRect(0, 0, imgSize, imgSize);
		g.setColor(Color.WHITE);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fractionalMetrics ? RenderingHints.VALUE_FRACTIONALMETRICS_ON : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antiAliasing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, antiAliasing ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

		FontMetrics fontMetrics = g.getFontMetrics();
		int currentCharHeight = 0;
		int posX = 0;
		int posY = 1;

		for (int i = 0; i < 256; i++) {
			char ch = (char) i;
			BufferedImage characterImage = getFontCharImage(ch, font, antiAliasing, fractionalMetrics);
			Glyph glyphCharacter = new Glyph();

			glyphCharacter.width = characterImage.getWidth();
			glyphCharacter.height = characterImage.getHeight();

			if (posX + glyphCharacter.width >= imgSize) {
				posX = 0;
				posY += currentCharHeight;
				currentCharHeight = 0;
			}

			glyphCharacter.x = posX;
			glyphCharacter.y = posY;

			if (glyphCharacter.height > maxFontHeight) {
				maxFontHeight = glyphCharacter.height;
			}

			if (glyphCharacter.height > currentCharHeight) {
				currentCharHeight = glyphCharacter.height;
			}

			g.drawImage(characterImage, posX, posY, null);
			posX += glyphCharacter.width;
			glyphCharacterMap[i] = glyphCharacter;
		}
	}

	public void setupTexture() {
		if (texture == null) {
			try {
				ByteArrayOutputStream baos = new ByteArrayOutputStream();
				ImageIO.write(bufferedImage, "png", baos);
				byte[] bytes = baos.toByteArray();
				ByteBuffer data = ByteBuffer.allocateDirect(bytes.length).put(bytes);
				data.flip();
				texture = new NativeImageBackedTexture(() -> "glyph_page", NativeImage.read(data));
				mc.getTextureManager().registerTexture(id, texture);
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		if (texture != null) {
			RenderSystem.setShaderTexture(0, texture.getGlTextureView());
		}
	}

	public void bindTexture() {
		// This needs to be implemented differently in 1.21.2+ if we don't have the texture ID
	}

	public void unbindTexture() {
	}

	public float drawChar(net.minecraft.client.gui.DrawContext context, char ch, float x, float y, float r, float g, float b, float a) {
		if (ch >= 256)
			return 0;
		Glyph glyphCharacter = glyphCharacterMap[ch];
		if (glyphCharacter == null)
			return 0;

		float width = (float) glyphCharacter.width;
		float height = (float) glyphCharacter.height;

		float pagePosX = (float) glyphCharacter.x / (float) imgSize;
		float pagePosY = (float) glyphCharacter.y / (float) imgSize;
		float pageWidth = (float) glyphCharacter.width / (float) imgSize;
		float pageHeight = (float) glyphCharacter.height / (float) imgSize;

		VertexConsumerProvider.Immediate immediate = mc.getBufferBuilders().getEntityVertexConsumers();
		VertexConsumer buffer = immediate.getBuffer(RenderLayer.getText(id));
		
		org.joml.Matrix3x2f matrix = context.getMatrices();
		
		buffer.vertex(matrix, x, y).color(r, g, b, a).texture(pagePosX, pagePosY).light(15728880);
		buffer.vertex(matrix, x, y + height).color(r, g, b, a).texture(pagePosX, pagePosY + pageHeight).light(15728880);
		buffer.vertex(matrix, x + width, y + height).color(r, g, b, a).texture(pagePosX + pageWidth, pagePosY + pageHeight).light(15728880);
		buffer.vertex(matrix, x + width, y).color(r, g, b, a).texture(pagePosX + pageWidth, pagePosY).light(15728880);

		return width;
	}

	public float getWidth(char ch) {
		if (ch >= 256 || glyphCharacterMap[ch] == null)
			return 0;
		return glyphCharacterMap[ch].width;
	}

	public int getMaxFontHeight() {
		return maxFontHeight;
	}

	private BufferedImage getFontCharImage(char ch, Font font, boolean antiAliasing, boolean fractionalMetrics) {
		BufferedImage temp = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = (Graphics2D) temp.getGraphics();
		g.setFont(font);
		g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fractionalMetrics ? RenderingHints.VALUE_FRACTIONALMETRICS_ON : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antiAliasing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
		g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, antiAliasing ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);

		FontMetrics fontMetrics = g.getFontMetrics();
		int charWidth = fontMetrics.charWidth(ch) + 8;
		if (charWidth <= 8)
			charWidth = 7;

		int charHeight = fontMetrics.getHeight() + 3;
		if (charHeight <= 3)
			charHeight = font.getSize();

		BufferedImage characterImage = new BufferedImage(charWidth, charHeight, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g2 = (Graphics2D) characterImage.getGraphics();
		g2.setFont(font);
		g2.setColor(Color.WHITE);
		g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, fractionalMetrics ? RenderingHints.VALUE_FRACTIONALMETRICS_ON : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
		g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, antiAliasing ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);
		g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, antiAliasing ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
		g2.drawString(String.valueOf(ch), 3, fontMetrics.getAscent() + 2);

		return characterImage;
	}

	static class Glyph {
		int x;
		int y;
		int width;
		int height;
	}
}