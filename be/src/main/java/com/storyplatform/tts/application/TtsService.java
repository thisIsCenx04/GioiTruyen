package com.storyplatform.tts.application;

import com.storyplatform.shared.api.ApiException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Sinh tiếng đọc ngay trên máy chủ, không mượn giọng của máy người nghe.
 *
 * <p>Bản đầu dùng bộ đọc sẵn có của trình duyệt. Nó miễn phí và không tốn gì
 * của máy chủ, nhưng phụ thuộc vào thứ nằm ngoài tầm với: máy nào chưa cài
 * giọng tiếng Việt thì không nghe được, và phần lớn máy Windows chưa cài. Ở đây
 * giọng do máy chủ tạo, nên mọi người nghe cùng một giọng, kể cả trên iPhone
 * hay một máy Windows trắng tinh.
 *
 * <p>Máy chủ chỉ có một nhân CPU, nên ba điều dưới đây không phải tối ưu hoá
 * sớm mà là điều kiện để trang không sập:
 *
 * <ul>
 *   <li>Cắt chương thành mẩu ngắn - người nghe có tiếng sau vài giây thay vì
 *       chờ tổng hợp cả chương.</li>
 *   <li>Đệm ra đĩa - lần nghe thứ hai của bất kỳ ai không tốn thêm CPU nào.</li>
 *   <li>Giới hạn số việc chạy cùng lúc - tổng hợp tiếng ăn trọn một nhân, thả
 *       cửa thì trang web đứng hình vì đói CPU.</li>
 * </ul>
 */
@Service
public class TtsService {

    private static final Logger log = LoggerFactory.getLogger(TtsService.class);

    /** Giọng đọc, theo tên người nghe thấy chứ không theo tên tệp mô hình. */
    public enum Voice {
        FEMALE("vais1000", null),
        MALE("vivos", 50);

        private final String model;
        private final Integer speaker;

        Voice(String model, Integer speaker) {
            this.model = model;
            this.speaker = speaker;
        }

        public static Voice of(String value) {
            return "male".equalsIgnoreCase(value) ? MALE : FEMALE;
        }
    }

    private final Path piper;
    private final Path voiceDir;
    private final Path cacheDir;
    private final long cacheLimitBytes;
    private final int timeoutSeconds;

    /**
     * Số việc tổng hợp được chạy cùng lúc.
     *
     * <p>Một nhân CPU thì hai việc song song không nhanh hơn hai việc nối tiếp,
     * chỉ làm cả hai cùng chậm và cướp CPU của phần còn lại của trang. Xếp hàng
     * là câu trả lời đúng, không phải chạy song song.
     */
    private final Semaphore slots;

    public TtsService(
            @Value("${app.tts.piper-binary:/opt/tts/piper/piper}") String piperBinary,
            @Value("${app.tts.voice-dir:/opt/tts/voices}") String voiceDir,
            @Value("${app.tts.cache-dir:/var/lib/gioitruyen-tts}") String cacheDir,
            @Value("${app.tts.cache-limit-mb:4096}") long cacheLimitMb,
            @Value("${app.tts.concurrency:2}") int concurrency,
            @Value("${app.tts.timeout-seconds:60}") int timeoutSeconds
    ) {
        this.piper = Path.of(piperBinary);
        this.voiceDir = Path.of(voiceDir);
        this.cacheDir = Path.of(cacheDir);
        this.cacheLimitBytes = cacheLimitMb * 1024L * 1024L;
        this.slots = new Semaphore(Math.max(1, concurrency));
        this.timeoutSeconds = timeoutSeconds;
    }

    /** Máy chủ này có dựng được tiếng không, để màn hình biết mà nói trước. */
    public boolean available() {
        return Files.isExecutable(piper) && Files.isDirectory(voiceDir);
    }

    /**
     * Tiếng đọc của một mẩu, lấy từ đệm nếu đã có.
     *
     * @param chapterId chương, chỉ dùng để đặt tên tệp đệm.
     * @param version   bản của chương; đổi nội dung là đổi version, nên tệp đệm
     *                  cũ tự hết hiệu lực thay vì phát ra chữ đã bị sửa.
     */
    public byte[] audio(String chapterId, int version, int index, String text, Voice voice) {
        if (!available()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "tts.unavailable",
                    "TTS unavailable", "Máy chủ đọc truyện đang bảo trì. Thử lại sau ít phút.");
        }

        Path cached = cachePath(chapterId, version, index, text, voice);
        try {
            if (Files.exists(cached)) {
                // Chạm vào tệp để nó khỏi bị dọn: dọn theo lần chạm gần nhất,
                // nên chương đang có người nghe luôn nằm lại trong đệm.
                touch(cached);
                return Files.readAllBytes(cached);
            }
        } catch (IOException reading) {
            log.warn("Không đọc được tệp đệm {}: {}", cached, reading.toString());
        }

        byte[] audio = synthesize(text, voice, cached);
        pruneCache();
        return audio;
    }

    /* ── Tổng hợp ────────────────────────────────────────────────────── */

    private byte[] synthesize(String text, Voice voice, Path target) {
        boolean acquired = false;
        try {
            // Chờ có chỗ, nhưng không chờ mãi: hàng đợi dài nghĩa là máy đang
            // quá tải, và trả lỗi ngay tử tế hơn là để người dùng nhìn màn hình
            // trắng cho tới khi trình duyệt bỏ cuộc.
            acquired = slots.tryAcquire(timeoutSeconds, TimeUnit.SECONDS);
            if (!acquired) {
                throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "tts.busy",
                        "TTS busy", "Máy chủ đang bận dựng tiếng. Thử lại sau vài giây.");
            }

            Path wav = Files.createTempFile("tts-", ".wav");
            Path opus = Files.createTempFile("tts-", ".opus");
            try {
                runPiper(text, voice, wav);
                runFfmpeg(wav, opus);
                byte[] audio = Files.readAllBytes(opus);

                Files.createDirectories(target.getParent());
                // Ghi ra tệp tạm rồi đổi tên: hai yêu cầu cùng một mẩu sẽ không
                // đọc phải một tệp mới ghi được một nửa.
                Path staging = Files.createTempFile(target.getParent(), "part-", ".opus");
                Files.write(staging, audio);
                Files.move(staging, target, StandardCopyOption.REPLACE_EXISTING);
                return audio;
            } finally {
                Files.deleteIfExists(wav);
                Files.deleteIfExists(opus);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "tts.interrupted",
                    "TTS interrupted", "Dựng tiếng bị gián đoạn. Thử lại giúp.");
        } catch (IOException failure) {
            log.error("Tổng hợp tiếng thất bại: {}", failure.toString());
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "tts.failed",
                    "TTS failed", "Không dựng được tiếng cho đoạn này.");
        } finally {
            if (acquired) slots.release();
        }
    }

    private void runPiper(String text, Voice voice, Path wav) throws IOException, InterruptedException {
        List<String> command = new ArrayList<>(List.of(
                piper.toString(),
                "--model", voiceDir.resolve(voice.model + ".onnx").toString(),
                "--output_file", wav.toString()));
        if (voice.speaker != null) {
            command.add("--speaker");
            command.add(String.valueOf(voice.speaker));
        }

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        try (var stdin = process.getOutputStream()) {
            // Piper đọc từng dòng; xuống dòng trong mẩu sẽ thành hai lần đọc
            // rời rạc, nên gộp lại thành một dòng duy nhất.
            stdin.write(text.replace('\n', ' ').getBytes(StandardCharsets.UTF_8));
            stdin.write('\n');
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("piper quá hạn " + timeoutSeconds + "s");
        }
        if (process.exitValue() != 0) {
            throw new IOException("piper lỗi " + process.exitValue() + ": " + output);
        }
    }

    /**
     * Nén sang Opus.
     *
     * <p>Một mẩu mười hai giây ở dạng WAV nặng khoảng 500 KB; cùng mẩu đó ở
     * Opus 24 kbps chỉ còn khoảng 35 KB. Với người nghe bằng 3G thì đó là khác
     * biệt giữa nghe được và không.
     */
    private void runFfmpeg(Path wav, Path opus) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(
                "ffmpeg", "-y", "-loglevel", "error",
                "-i", wav.toString(),
                "-c:a", "libopus", "-b:a", "24k", "-application", "voip",
                opus.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            throw new IOException("ffmpeg quá hạn");
        }
        if (process.exitValue() != 0) {
            throw new IOException("ffmpeg lỗi " + process.exitValue() + ": " + output);
        }
    }

    /* ── Đệm ─────────────────────────────────────────────────────────── */

    /**
     * Đường dẫn tệp đệm.
     *
     * <p>Băm cả nội dung mẩu vào tên: nếu chương được sửa mà số version không
     * đổi, tên tệp vẫn đổi theo chữ, nên không đời nào phát ra đoạn văn cũ.
     */
    private Path cachePath(String chapterId, int version, int index, String text, Voice voice) {
        String digest = sha256(text);
        String bucket = digest.substring(0, 2);
        return cacheDir.resolve(bucket)
                .resolve("%s-v%d-%d-%s-%s.opus".formatted(
                        chapterId, version, index, voice.name().toLowerCase(), digest.substring(0, 12)));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static void touch(Path path) {
        try {
            Files.setLastModifiedTime(path, java.nio.file.attribute.FileTime.from(java.time.Instant.now()));
        } catch (IOException ignored) {
            // Không chạm được thì tệp chỉ bị dọn sớm hơn, không phải lỗi.
        }
    }

    /**
     * Dọn đệm khi vượt trần, bỏ tệp lâu chưa dùng nhất trước.
     *
     * <p>Một bộ truyện hai nghìn chương ở dạng tiếng chiếm vài GB. Không có
     * bước này thì đĩa đầy dần cho tới ngày máy chủ không ghi nổi log.
     */
    private void pruneCache() {
        try {
            if (!Files.isDirectory(cacheDir)) return;
            List<Path> files;
            try (var walk = Files.walk(cacheDir)) {
                files = walk.filter(Files::isRegularFile).toList();
            }
            long total = 0;
            for (Path file : files) total += Files.size(file);
            if (total <= cacheLimitBytes) return;

            List<Path> oldestFirst = new ArrayList<>(files);
            oldestFirst.sort(Comparator.comparing(file -> {
                try {
                    return Files.getLastModifiedTime(file);
                } catch (IOException unreadable) {
                    return java.nio.file.attribute.FileTime.fromMillis(0);
                }
            }));

            long removed = 0;
            for (Path file : oldestFirst) {
                if (total - removed <= cacheLimitBytes * 9 / 10) break;
                long size = Files.size(file);
                if (Files.deleteIfExists(file)) removed += size;
            }
            log.info("Dọn đệm tiếng: bỏ {} MB", removed / (1024 * 1024));
        } catch (IOException failure) {
            log.warn("Không dọn được đệm tiếng: {}", failure.toString());
        }
    }

    /** Thông tin cho màn hình quản trị: đệm đang chiếm bao nhiêu. */
    public Map<String, Object> cacheStats() {
        try (var walk = Files.walk(cacheDir)) {
            List<Path> files = walk.filter(Files::isRegularFile).toList();
            long total = 0;
            for (Path file : files) total += Files.size(file);
            return Map.of("available", available(), "files", files.size(),
                    "megabytes", total / (1024 * 1024), "limitMegabytes", cacheLimitBytes / (1024 * 1024));
        } catch (IOException failure) {
            return Map.of("available", available(), "files", 0, "megabytes", 0);
        }
    }
}
