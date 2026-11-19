package com.opsflow.demo;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;

import javax.imageio.ImageIO;

/**
 * The pictures of the demo items: a simple drawing for each kind, made in the code so the repository holds
 * no image and the command needs no file. No text is drawn, so no font is needed in the container.
 */
final class DemoImages {

	/** What a picture shows. */
	enum Kind {
		PAPER, PENS, TAPE, BOX, TONER, LABELS
	}

	private static final int SIZE = 320;

	private DemoImages() {
	}

	static byte[] png(Kind kind, Color color) {
		BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = image.createGraphics();
		try {
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setColor(new Color(245, 246, 248));
			g.fillRect(0, 0, SIZE, SIZE);
			g.setColor(color);
			g.setStroke(new BasicStroke(10, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
			switch (kind) {
				case PAPER -> {
					for (int i = 2; i >= 0; i--) {
						g.setColor(i == 0 ? Color.WHITE : new Color(225, 228, 233));
						g.fillRect(90 + i * 14, 60 + i * 14, 130, 180);
					}
					g.setColor(color);
					g.drawRect(90, 60, 130, 180);
					for (int y = 100; y <= 200; y += 25) {
						g.drawLine(110, y, 200, y);
					}
				}
				case PENS -> {
					for (int i = 0; i < 3; i++) {
						int x = 100 + i * 50;
						g.fillRoundRect(x, 60, 26, 170, 12, 12);
						g.fillPolygon(new int[] { x, x + 26, x + 13 }, new int[] { 230, 230, 265 }, 3);
					}
				}
				case TAPE -> {
					g.fillOval(70, 70, 180, 180);
					g.setColor(new Color(245, 246, 248));
					g.fillOval(125, 125, 70, 70);
				}
				case BOX -> {
					g.fillPolygon(new int[] { 60, 160, 260, 160 }, new int[] { 110, 60, 110, 160 }, 4);
					g.setColor(color.darker());
					g.fillPolygon(new int[] { 60, 160, 160, 60 }, new int[] { 110, 160, 270, 220 }, 4);
					g.setColor(color.brighter());
					g.fillPolygon(new int[] { 160, 260, 260, 160 }, new int[] { 160, 110, 220, 270 }, 4);
				}
				case TONER -> {
					g.fillRoundRect(60, 110, 200, 100, 30, 30);
					g.setColor(Color.WHITE);
					g.fillRect(90, 140, 140, 14);
					g.fillRect(90, 166, 90, 14);
				}
				case LABELS -> {
					g.fillOval(80, 80, 160, 160);
					g.setColor(Color.WHITE);
					g.fillOval(130, 130, 60, 60);
					g.fillRect(160, 220, 110, 40);
					g.setColor(color);
					g.drawRect(160, 220, 110, 40);
				}
			}
		}
		finally {
			g.dispose();
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try {
			ImageIO.write(image, "png", out);
		}
		catch (IOException failed) {
			throw new UncheckedIOException(failed);
		}
		return out.toByteArray();
	}

}
