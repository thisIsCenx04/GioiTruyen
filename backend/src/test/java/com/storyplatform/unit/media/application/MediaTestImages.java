package com.storyplatform.unit.media.application;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

final class MediaTestImages {

    private MediaTestImages() {
    }

    static byte[] jpeg(int width, int height) {
        return raster("jpg", width, height);
    }

    static byte[] png(int width, int height) {
        return raster("png", width, height);
    }

    static byte[] webpLossless(int width, int height) {
        if (width < 1 || width > 16_384
                || height < 1 || height > 16_384) {
            throw new IllegalArgumentException("invalid WebP dimensions");
        }
        int bits = width - 1 | (height - 1) << 14;
        ByteBuffer buffer = ByteBuffer.allocate(26)
                .order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(new byte[]{0x52, 0x49, 0x46, 0x46});
        buffer.putInt(18);
        buffer.put(new byte[]{0x57, 0x45, 0x42, 0x50});
        buffer.put(new byte[]{0x56, 0x50, 0x38, 0x4c});
        buffer.putInt(5);
        buffer.put((byte) 0x2f);
        buffer.putInt(bits);
        buffer.put((byte) 0);
        return buffer.array();
    }

    private static byte[] raster(
            String format,
            int width,
            int height
    ) {
        BufferedImage image = new BufferedImage(
                width,
                height,
                BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, format, output)) {
                throw new AssertionError("Missing " + format + " writer");
            }
            return output.toByteArray();
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }
}
