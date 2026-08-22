package com.storyplatform.admin.application;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Card-sized copies of story covers.
 *
 * <p>Covers arrive at print resolution - 2200x3300 and several megabytes is
 * normal - and every shelf then renders them about 190px wide. The browser
 * still downloads the whole file, which is why a page of cards used to fill in
 * one picture at a time. A cover is stored once and read on nearly every page,
 * so the resize belongs here rather than in the request path.
 *
 * <p>The thumbnail lives beside the original under a {@code thumb/} directory
 * with the same base name, so its URL is derivable and needs no database
 * column. When one cannot be produced - a WEBP, which stock ImageIO cannot
 * read, or a corrupt file - none is written and the page falls back to the
 * original: a missing thumbnail must degrade to a slow cover, never a broken
 * one.
 */
@Service
public class CoverThumbnails {

    /**
     * Covers the largest place the derived file is used - the 272px frame on a
     * story page - at better than 2x, so it is sharp there and well past sharp
     * on the ~190px cards. Sized generously on purpose: an earlier pass at this
     * looked soft next to the original, and a file this small is still two
     * orders of magnitude cheaper than the print-resolution upload.
     */
    private static final int TARGET_WIDTH = 600;
    private static final float JPEG_QUALITY = 0.82f;
    /** Directory name holding the derived files, relative to the original's. */
    public static final String THUMB_DIR = "thumb";
    /** Formats ImageIO reads out of the box. GIF is skipped: it may be animated. */
    private static final Set<String> RESIZABLE = Set.of("png", "jpg", "jpeg");

    private static final Logger log = LoggerFactory.getLogger(CoverThumbnails.class);

    /**
     * Writes the card-sized copy of a stored image, if one can be made.
     *
     * <p>Never throws: an upload that succeeded must not be failed after the
     * fact because its thumbnail could not be produced.
     */
    public void generate(Path original) {
        Path thumb = thumbPathOf(original);
        if (thumb == null) {
            return;
        }
        try {
            BufferedImage source = ImageIO.read(original.toFile());
            if (source == null || source.getWidth() <= 0) {
                return;
            }
            // A cover already at or below card size is written out unchanged in
            // size. Skipping it entirely left no file at the derived URL, so the
            // page asked for a thumbnail that was never going to exist and took
            // a 404 on every load before falling back to the original.
            int width = Math.min(TARGET_WIDTH, source.getWidth());
            int height = Math.max(1, Math.round(source.getHeight() * (width / (float) source.getWidth())));
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D graphics = scaled.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING,
                    RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                    RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(source, 0, 0, width, height, null);
            graphics.dispose();

            Files.createDirectories(thumb.getParent());
            writeJpeg(scaled, thumb);
        } catch (IOException | RuntimeException exception) {
            log.warn("Could not build a thumbnail for {}: {}", original.getFileName(), exception.toString());
        }
    }

    /**
     * Builds any thumbnail that is missing under a directory of originals.
     *
     * <p>Covers uploaded before thumbnails existed would otherwise never get
     * one, and those are precisely the heavy files already in circulation.
     *
     * @return how many were written
     */
    public int backfill(Path directory) {
        if (!Files.isDirectory(directory)) {
            return 0;
        }
        int written = 0;
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file : files.filter(Files::isRegularFile).toList()) {
                Path thumb = thumbPathOf(file);
                if (thumb == null || Files.exists(thumb)) {
                    continue;
                }
                generate(file);
                if (Files.exists(thumb)) {
                    written++;
                }
            }
        } catch (IOException exception) {
            log.warn("Could not scan {} for missing thumbnails: {}", directory, exception.toString());
        }
        return written;
    }

    /** Where the thumbnail of this file belongs, or null if it cannot have one. */
    private static Path thumbPathOf(Path original) {
        String name = original.getFileName().toString();
        int dot = name.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        String extension = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        if (!RESIZABLE.contains(extension)) {
            return null;
        }
        // Always .jpg: the client derives the URL by this same rule, so the
        // extension has to be fixed rather than inherited from the source.
        return original.getParent().resolve(THUMB_DIR).resolve(name.substring(0, dot) + ".jpg");
    }

    private static void writeJpeg(BufferedImage image, Path target) throws IOException {
        var writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            ImageIO.write(image, "jpg", target.toFile());
            return;
        }
        var writer = writers.next();
        var params = writer.getDefaultWriteParam();
        params.setCompressionMode(javax.imageio.ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(JPEG_QUALITY);
        try (var output = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(output);
            writer.write(null, new javax.imageio.IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
    }
}
