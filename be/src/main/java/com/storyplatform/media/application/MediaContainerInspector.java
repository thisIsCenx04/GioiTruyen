package com.storyplatform.media.application;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.zip.CRC32;

final class MediaContainerInspector {

    private static final byte[] PNG_SIGNATURE = {
            (byte) 0x89, 0x50, 0x4e, 0x47,
            0x0d, 0x0a, 0x1a, 0x0a
    };

    private MediaContainerInspector() {
    }

    static Dimensions inspect(String format, byte[] content) {
        return switch (format) {
            case "jpg", "jpeg" -> jpeg(content);
            case "png" -> png(content);
            case "webp" -> webp(content);
            default -> throw invalid();
        };
    }

    private static Dimensions jpeg(byte[] content) {
        if (content.length < 4
                || unsigned(content[content.length - 2]) != 0xff
                || unsigned(content[content.length - 1]) != 0xd9) {
            throw invalid();
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(
                new ByteArrayInputStream(content)
        )) {
            if (input == null) {
                throw invalid();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw invalid();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                if (!"JPEG".equalsIgnoreCase(reader.getFormatName())) {
                    throw invalid();
                }
                return dimensions(
                        reader.getWidth(0),
                        reader.getHeight(0)
                );
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof MediaPolicyException policy) {
                throw policy;
            }
            throw invalid();
        }
    }

    private static Dimensions png(byte[] content) {
        if (!startsWith(content, PNG_SIGNATURE)) {
            throw invalid();
        }
        int offset = PNG_SIGNATURE.length;
        Dimensions result = null;
        boolean imageDataSeen = false;
        while (offset < content.length) {
            if (content.length - offset < 12) {
                throw invalid();
            }
            long dataLength = unsignedBigEndianInt(content, offset);
            if (dataLength > Integer.MAX_VALUE) {
                throw invalid();
            }
            int length = (int) dataLength;
            int typeOffset = offset + 4;
            int dataOffset = typeOffset + 4;
            long end = (long) dataOffset + length + 4;
            if (end > content.length) {
                throw invalid();
            }
            String type = ascii(content, typeOffset);
            verifyCrc(content, typeOffset, length);
            if (result == null) {
                if (!"IHDR".equals(type) || length != 13) {
                    throw invalid();
                }
                result = dimensions(
                        positiveBigEndianInt(content, dataOffset),
                        positiveBigEndianInt(content, dataOffset + 4)
                );
            } else if ("IHDR".equals(type)) {
                throw invalid();
            }
            if ("IDAT".equals(type)) {
                imageDataSeen = true;
            }
            offset = (int) end;
            if ("IEND".equals(type)) {
                if (length != 0
                        || offset != content.length
                        || result == null
                        || !imageDataSeen) {
                    throw invalid();
                }
                return result;
            }
        }
        throw invalid();
    }

    private static Dimensions webp(byte[] content) {
        if (content.length < 20
                || !"RIFF".equals(ascii(content, 0))
                || !"WEBP".equals(ascii(content, 8))
                || unsignedLittleEndianInt(content, 4)
                != content.length - 8L) {
            throw invalid();
        }
        int offset = 12;
        Dimensions result = null;
        while (offset < content.length) {
            if (content.length - offset < 8) {
                throw invalid();
            }
            String type = ascii(content, offset);
            long dataLength = unsignedLittleEndianInt(content, offset + 4);
            if (dataLength > Integer.MAX_VALUE) {
                throw invalid();
            }
            int length = (int) dataLength;
            int dataOffset = offset + 8;
            long paddedEnd = (long) dataOffset
                    + length
                    + (length & 1);
            if (paddedEnd > content.length) {
                throw invalid();
            }
            if (result == null) {
                result = switch (type) {
                    case "VP8 " -> vp8(content, dataOffset, length);
                    case "VP8L" -> vp8Lossless(
                            content,
                            dataOffset,
                            length
                    );
                    default -> null;
                };
            }
            offset = (int) paddedEnd;
        }
        if (offset != content.length || result == null) {
            throw invalid();
        }
        return result;
    }

    private static Dimensions vp8(
            byte[] content,
            int offset,
            int length
    ) {
        if (length < 10
                || unsigned(content[offset + 3]) != 0x9d
                || unsigned(content[offset + 4]) != 0x01
                || unsigned(content[offset + 5]) != 0x2a) {
            throw invalid();
        }
        return dimensions(
                littleEndianShort(content, offset + 6) & 0x3fff,
                littleEndianShort(content, offset + 8) & 0x3fff
        );
    }

    private static Dimensions vp8Lossless(
            byte[] content,
            int offset,
            int length
    ) {
        if (length < 5 || unsigned(content[offset]) != 0x2f) {
            throw invalid();
        }
        int bits = unsigned(content[offset + 1])
                | unsigned(content[offset + 2]) << 8
                | unsigned(content[offset + 3]) << 16
                | unsigned(content[offset + 4]) << 24;
        return dimensions(
                (bits & 0x3fff) + 1,
                ((bits >>> 14) & 0x3fff) + 1
        );
    }

    private static void verifyCrc(
            byte[] content,
            int typeOffset,
            int dataLength
    ) {
        CRC32 crc = new CRC32();
        crc.update(content, typeOffset, 4 + dataLength);
        long expected = unsignedBigEndianInt(
                content,
                typeOffset + 4 + dataLength
        );
        if (crc.getValue() != expected) {
            throw invalid();
        }
    }

    private static Dimensions dimensions(int width, int height) {
        if (width < 1 || height < 1) {
            throw invalid();
        }
        return new Dimensions(width, height);
    }

    private static int positiveBigEndianInt(byte[] value, int offset) {
        long parsed = unsignedBigEndianInt(value, offset);
        if (parsed < 1 || parsed > Integer.MAX_VALUE) {
            throw invalid();
        }
        return (int) parsed;
    }

    private static long unsignedBigEndianInt(byte[] value, int offset) {
        requireAvailable(value, offset, 4);
        return (long) unsigned(value[offset]) << 24
                | (long) unsigned(value[offset + 1]) << 16
                | (long) unsigned(value[offset + 2]) << 8
                | unsigned(value[offset + 3]);
    }

    private static long unsignedLittleEndianInt(
            byte[] value,
            int offset
    ) {
        requireAvailable(value, offset, 4);
        return unsigned(value[offset])
                | (long) unsigned(value[offset + 1]) << 8
                | (long) unsigned(value[offset + 2]) << 16
                | (long) unsigned(value[offset + 3]) << 24;
    }

    private static int littleEndianShort(byte[] value, int offset) {
        requireAvailable(value, offset, 2);
        return unsigned(value[offset])
                | unsigned(value[offset + 1]) << 8;
    }

    private static String ascii(byte[] value, int offset) {
        requireAvailable(value, offset, 4);
        return new String(
                value,
                offset,
                4,
                StandardCharsets.US_ASCII
        );
    }

    private static boolean startsWith(byte[] content, byte[] expected) {
        if (content.length < expected.length) {
            return false;
        }
        for (int index = 0; index < expected.length; index++) {
            if (content[index] != expected[index]) {
                return false;
            }
        }
        return true;
    }

    private static void requireAvailable(
            byte[] value,
            int offset,
            int length
    ) {
        if (offset < 0
                || length < 0
                || offset > value.length - length) {
            throw invalid();
        }
    }

    private static int unsigned(byte value) {
        return Byte.toUnsignedInt(value);
    }

    private static MediaPolicyException invalid() {
        return new MediaPolicyException(
                "MEDIA_CONTAINER_INVALID",
                "Image container structure is invalid or has trailing data."
        );
    }

    record Dimensions(int width, int height) {
    }
}
