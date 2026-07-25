package com.storyplatform.media.application;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.zip.CRC32;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaContainerInspectorTest {

    @Test
    void readsDimensionsFromSupportedContainers() {
        assertThat(MediaContainerInspector.inspect(
                "jpg",
                raster("jpg", 2, 3)
        )).isEqualTo(new MediaContainerInspector.Dimensions(2, 3));
        assertThat(MediaContainerInspector.inspect(
                "jpeg",
                raster("jpg", 3, 2)
        )).isEqualTo(new MediaContainerInspector.Dimensions(3, 2));
        assertThat(MediaContainerInspector.inspect(
                "png",
                raster("png", 4, 5)
        )).isEqualTo(new MediaContainerInspector.Dimensions(4, 5));
        assertThat(MediaContainerInspector.inspect(
                "webp",
                webpLossless(6, 7)
        )).isEqualTo(new MediaContainerInspector.Dimensions(6, 7));
        assertThat(MediaContainerInspector.inspect(
                "webp",
                webpLossy(8, 9)
        )).isEqualTo(new MediaContainerInspector.Dimensions(8, 9));
    }

    @Test
    void rejectsUnsupportedTruncatedAndTrailingContainers() {
        byte[] jpeg = raster("jpg", 1, 1);
        byte[] png = raster("png", 1, 1);
        byte[] webp = webpLossless(1, 1);

        assertInvalid("gif", png);
        assertInvalid("jpg", new byte[]{
                (byte) 0xff, (byte) 0xd8, (byte) 0xff, (byte) 0xd9
        });
        assertInvalid("jpeg", Arrays.copyOf(jpeg, jpeg.length - 1));
        assertInvalid("png", Arrays.copyOf(png, 8));
        assertInvalid("png", Arrays.copyOf(png, png.length - 1));
        assertInvalid("webp", Arrays.copyOf(webp, 19));

        assertInvalid("jpg", append(jpeg));
        assertInvalid("png", append(png));
        assertInvalid("webp", append(webp));
    }

    @Test
    void rejectsCorruptPngChunkLengthTypeDimensionsAndCrc() {
        byte[] png = raster("png", 2, 2);

        byte[] hugeLength = png.clone();
        Arrays.fill(hugeLength, 8, 12, (byte) 0xff);
        assertInvalid("png", hugeLength);

        byte[] wrongFirstType = png.clone();
        System.arraycopy(
                new byte[]{0x4a, 0x55, 0x4e, 0x4b},
                0,
                wrongFirstType,
                12,
                4
        );
        updatePngChunkCrc(wrongFirstType, 8);
        assertInvalid("png", wrongFirstType);

        byte[] zeroWidth = png.clone();
        Arrays.fill(zeroWidth, 16, 20, (byte) 0);
        updatePngChunkCrc(zeroWidth, 8);
        assertInvalid("png", zeroWidth);

        byte[] badCrc = png.clone();
        badCrc[29] ^= 1;
        assertInvalid("png", badCrc);

        byte[] duplicateHeader = new byte[png.length + 25];
        System.arraycopy(png, 0, duplicateHeader, 0, 33);
        System.arraycopy(png, 8, duplicateHeader, 33, 25);
        System.arraycopy(
                png,
                33,
                duplicateHeader,
                58,
                png.length - 33
        );
        assertInvalid("png", duplicateHeader);

        byte[] missingImageData = new byte[45];
        System.arraycopy(png, 0, missingImageData, 0, 33);
        System.arraycopy(
                png,
                png.length - 12,
                missingImageData,
                33,
                12
        );
        assertInvalid("png", missingImageData);
    }

    @Test
    void rejectsCorruptWebpEnvelopeChunksAndDimensions() {
        byte[] lossless = webpLossless(2, 2);

        byte[] wrongMarker = lossless.clone();
        wrongMarker[0] = 0;
        assertInvalid("webp", wrongMarker);

        byte[] wrongEnvelopeLength = lossless.clone();
        wrongEnvelopeLength[4] = 0;
        assertInvalid("webp", wrongEnvelopeLength);

        byte[] hugeChunk = lossless.clone();
        Arrays.fill(hugeChunk, 16, 20, (byte) 0xff);
        assertInvalid("webp", hugeChunk);

        byte[] badLosslessSignature = lossless.clone();
        badLosslessSignature[20] = 0;
        assertInvalid("webp", badLosslessSignature);

        byte[] unknownChunk = lossless.clone();
        System.arraycopy(
                new byte[]{0x4a, 0x55, 0x4e, 0x4b},
                0,
                unknownChunk,
                12,
                4
        );
        assertInvalid("webp", unknownChunk);

        assertInvalid("webp", webpLossy(0, 1));
    }

    private static void assertInvalid(String format, byte[] content) {
        assertThatThrownBy(() ->
                MediaContainerInspector.inspect(format, content))
                .isInstanceOf(MediaPolicyException.class)
                .extracting("code")
                .isEqualTo("MEDIA_CONTAINER_INVALID");
    }

    private static byte[] raster(
            String format,
            int width,
            int height
    ) {
        try {
            BufferedImage image = new BufferedImage(
                    width,
                    height,
                    BufferedImage.TYPE_INT_RGB
            );
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, output)) {
                throw new AssertionError("Missing image writer");
            }
            return output.toByteArray();
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private static byte[] webpLossless(int width, int height) {
        int bits = width - 1 | (height - 1) << 14;
        return ByteBuffer.allocate(26)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(new byte[]{0x52, 0x49, 0x46, 0x46})
                .putInt(18)
                .put(new byte[]{0x57, 0x45, 0x42, 0x50})
                .put(new byte[]{0x56, 0x50, 0x38, 0x4c})
                .putInt(5)
                .put((byte) 0x2f)
                .putInt(bits)
                .put((byte) 0)
                .array();
    }

    private static byte[] webpLossy(int width, int height) {
        return ByteBuffer.allocate(30)
                .order(ByteOrder.LITTLE_ENDIAN)
                .put(new byte[]{0x52, 0x49, 0x46, 0x46})
                .putInt(22)
                .put(new byte[]{0x57, 0x45, 0x42, 0x50})
                .put(new byte[]{0x56, 0x50, 0x38, 0x20})
                .putInt(10)
                .put(new byte[]{0, 0, 0, (byte) 0x9d, 0x01, 0x2a})
                .putShort((short) width)
                .putShort((short) height)
                .array();
    }

    private static byte[] append(byte[] content) {
        byte[] result = Arrays.copyOf(content, content.length + 1);
        result[result.length - 1] = '<';
        return result;
    }

    private static void updatePngChunkCrc(byte[] content, int chunkOffset) {
        int length = ByteBuffer.wrap(
                content,
                chunkOffset,
                4
        ).getInt();
        int typeOffset = chunkOffset + 4;
        CRC32 crc = new CRC32();
        crc.update(content, typeOffset, 4 + length);
        ByteBuffer.wrap(
                content,
                typeOffset + 4 + length,
                4
        ).putInt((int) crc.getValue());
    }
}
