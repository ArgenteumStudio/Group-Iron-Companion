package com.bowlsofmayo;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import javax.imageio.ImageIO;

final class TrackerImages
{
    private static final int ICON_SIZE = 32;

    static BufferedImage readLogo(BridgeClient bridge) throws IOException
    {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bridge.getBytes("/logo", 5 * 1024 * 1024)));
        if (image == null)
        {
            throw new IOException("Unsupported logo image format");
        }
        return iconImage(image);
    }

    static BufferedImage readMapIcon(BridgeClient bridge) throws IOException
    {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bridge.getBytes("/map-icon", 32768)));
        if (image == null || image.getWidth() > 32 || image.getHeight() > 32)
        {
            throw new IOException("Map icon must be an image no larger than 32 x 32");
        }
        return image;
    }

    static BufferedImage iconImage(BufferedImage source)
    {
        int minX = source.getWidth(), minY = source.getHeight(), maxX = -1, maxY = -1;
        for (int y = 0; y < source.getHeight(); y++)
        {
            for (int x = 0; x < source.getWidth(); x++)
            {
                if ((source.getRGB(x, y) >>> 24) != 0)
                {
                    minX = Math.min(minX, x);
                    minY = Math.min(minY, y);
                    maxX = Math.max(maxX, x);
                    maxY = Math.max(maxY, y);
                }
            }
        }
        if (maxX < 0)
        {
            return marker();
        }
        int width = maxX - minX + 1;
        int height = maxY - minY + 1;
        BufferedImage current = source.getSubimage(minX, minY, width, height);
        while (Math.max(current.getWidth(), current.getHeight()) > ICON_SIZE * 2)
        {
            int w = Math.max(1, current.getWidth() / 2);
            int h = Math.max(1, current.getHeight() / 2);
            current = draw(current, w, h, w, h, 0, 0);
        }
        double scale = (double) ICON_SIZE / Math.max(current.getWidth(), current.getHeight());
        int w = Math.max(1, (int) Math.round(current.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(current.getHeight() * scale));
        return draw(current, ICON_SIZE, ICON_SIZE, w, h, (ICON_SIZE - w) / 2, (ICON_SIZE - h) / 2);
    }

    private static BufferedImage draw(BufferedImage source, int canvasW, int canvasH, int w, int h, int x, int y)
    {
        BufferedImage result = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = result.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, x, y, w, h, null);
        graphics.dispose();
        return result;
    }

    static BufferedImage marker()
    {
        BufferedImage image = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.CYAN);
        graphics.fillOval(1, 1, 14, 14);
        graphics.setColor(Color.BLACK);
        graphics.drawOval(1, 1, 14, 14);
        graphics.dispose();
        return image;
    }

}
