package com.example.genprofileimage.profile;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

@Component
@ConditionalOnProperty(prefix = "app.comfyui", name = "mock", havingValue = "true", matchIfMissing = true)
public class MockComfyUiClient implements ComfyUiClient {

    private static final int DEFAULT_WIDTH = 768;
    private static final int DEFAULT_HEIGHT = 1024;

    @Override
    public byte[] generateSuitProfile(Path inputImagePath) throws IOException {
        BufferedImage source = ImageIO.read(inputImagePath.toFile());
        int width = source == null ? DEFAULT_WIDTH : Math.max(source.getWidth(), 512);
        int height = source == null ? DEFAULT_HEIGHT : Math.max(source.getHeight(), 512);

        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            drawStudioBackground(g, width, height);
            drawSuitSilhouette(g, width, height);
            drawMockBadge(g, width, height);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private void drawStudioBackground(Graphics2D g, int width, int height) {
        g.setPaint(new GradientPaint(0, 0, new Color(238, 242, 247), width, height, new Color(203, 213, 225)));
        g.fillRect(0, 0, width, height);

        g.setColor(new Color(255, 255, 255, 140));
        g.fillOval(width / 6, height / 12, width * 2 / 3, width * 2 / 3);
    }

    private void drawSuitSilhouette(Graphics2D g, int width, int height) {
        int centerX = width / 2;
        int headRadius = Math.min(width, height) / 9;
        int headY = height / 4;

        g.setColor(new Color(229, 194, 167));
        g.fillOval(centerX - headRadius, headY - headRadius, headRadius * 2, headRadius * 2);

        Path2D jacket = new Path2D.Double();
        jacket.moveTo(centerX - width * 0.33, height * 0.93);
        jacket.lineTo(centerX - width * 0.25, height * 0.48);
        jacket.quadTo(centerX, height * 0.38, centerX + width * 0.25, height * 0.48);
        jacket.lineTo(centerX + width * 0.33, height * 0.93);
        jacket.closePath();
        g.setColor(new Color(15, 23, 42));
        g.fill(jacket);

        Path2D shirt = new Path2D.Double();
        shirt.moveTo(centerX - width * 0.09, height * 0.47);
        shirt.lineTo(centerX, height * 0.79);
        shirt.lineTo(centerX + width * 0.09, height * 0.47);
        shirt.closePath();
        g.setColor(Color.WHITE);
        g.fill(shirt);

        Path2D tie = new Path2D.Double();
        tie.moveTo(centerX, height * 0.51);
        tie.lineTo(centerX - width * 0.035, height * 0.66);
        tie.lineTo(centerX, height * 0.82);
        tie.lineTo(centerX + width * 0.035, height * 0.66);
        tie.closePath();
        g.setColor(new Color(37, 99, 235));
        g.fill(tie);

        g.setStroke(new BasicStroke(Math.max(3, width / 160f)));
        g.setColor(new Color(148, 163, 184));
        g.drawLine(centerX - width / 9, (int) (height * 0.49), centerX, (int) (height * 0.79));
        g.drawLine(centerX + width / 9, (int) (height * 0.49), centerX, (int) (height * 0.79));
    }

    private void drawMockBadge(Graphics2D g, int width, int height) {
        String line1 = "MOCK RESULT";
        String line2 = "ComfyUI-ready suit profile";
        int padding = Math.max(18, width / 36);
        int badgeHeight = Math.max(82, height / 12);

        g.setColor(new Color(15, 23, 42, 210));
        g.fillRoundRect(padding, height - badgeHeight - padding, width - padding * 2, badgeHeight, 28, 28);

        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, Math.max(24, width / 28)));
        FontMetrics titleMetrics = g.getFontMetrics();
        int titleX = (width - titleMetrics.stringWidth(line1)) / 2;
        int titleY = height - badgeHeight - padding + badgeHeight / 2 - 4;
        g.drawString(line1, titleX, titleY);

        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, Math.max(16, width / 42)));
        FontMetrics subtitleMetrics = g.getFontMetrics();
        int subtitleX = (width - subtitleMetrics.stringWidth(line2)) / 2;
        g.setColor(new Color(203, 213, 225));
        g.drawString(line2, subtitleX, titleY + subtitleMetrics.getHeight() + 2);
    }
}
