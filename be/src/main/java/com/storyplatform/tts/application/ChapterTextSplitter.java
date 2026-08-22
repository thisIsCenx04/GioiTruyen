package com.storyplatform.tts.application;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

/**
 * Cắt nội dung chương thành đoạn văn và những mẩu vừa cho bộ tổng hợp tiếng.
 *
 * <p>Nguyên tắc của cả lớp này: chữ đưa đi đọc phải bằng đúng chữ hiển thị trên
 * trang, không thừa không thiếu một ký tự. Ở đây không có bước nào "hiểu" nội
 * dung, không dịch, không viết lại, không sửa chính tả, không thêm dấu câu.
 * Việc duy nhất được làm là <em>cắt</em> - và ghép lại phải ra đúng chuỗi ban
 * đầu.
 *
 * <p>Phải cắt vì hai lẽ. Một mẩu ngắn cho bộ tổng hợp trả kết quả sau vài giây
 * thay vì vài phút, nên người nghe bấm phát là có tiếng gần như ngay. Và mẩu là
 * đơn vị để tô sáng: mỗi mẩu biết nó thuộc đoạn văn nào.
 *
 * <p>Bản dịch sát từng dòng của {@code fe/apps/web/src/lib/speech.ts}. Hai bản
 * phải cho ra cùng một kết quả trên cùng một chương, vì phía trình duyệt tô
 * sáng theo chỉ số đoạn mà phía máy chủ đánh số.
 */
public final class ChapterTextSplitter {

    /** Ngưỡng ký tự cho một mẩu. Khoảng tám giây đọc ở tốc độ thường. */
    public static final int CHUNK_LIMIT = 220;

    /** Các thẻ mở đầu một đoạn mới khi nghe. */
    private static final String BLOCK_TAGS = "p, div, li, h1, h2, h3, h4, h5, h6, blockquote, pre, td";

    /**
     * Dấu đánh chỗ có {@code <br>}, chọn một ký tự không bao giờ có trong văn bản.
     *
     * <p>Cần một dấu riêng vì xuống dòng thật trong mã HTML chỉ là khoảng trắng
     * cho dễ đọc, không phải ý đồ của tác giả - gộp chung hai thứ thì một đoạn
     * viết xuống dòng trong file HTML sẽ bị tách làm đôi khi nghe.
     */
    private static final String BREAK = "\u0000";

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    /**
     * Câu, kèm dấu đóng ngoặc kép đi sau dấu chấm.
     *
     * <p>Không gom dấu ngoặc lại thì mỗi câu thoại kết thúc bằng dấu hỏi sẽ đẩy
     * dấu ngoặc sang đầu mẩu sau, và người nghe được một quãng nghỉ đặt sai chỗ.
     */
    private static final Pattern SENTENCE =
            Pattern.compile("[^.!?…]+[.!?…]+[\"”'’)\\]]*|[^.!?…]+$");

    private ChapterTextSplitter() {
    }

    /** Một mẩu để đọc, kèm số thứ tự đoạn văn nó thuộc về. */
    public record SpeechChunk(int paragraph, String text) {}

    /** Chương đã sẵn sàng: đoạn để hiển thị, mẩu để đọc. */
    public record ChapterSpeech(List<String> paragraphs, List<SpeechChunk> chunks) {}

    /**
     * Các đoạn văn của chương, đúng thứ tự, đúng nguyên văn.
     *
     * @param contentHtml HTML của chương lấy từ cơ sở dữ liệu.
     */
    public static List<String> paragraphs(String contentHtml) {
        if (contentHtml == null || contentHtml.isBlank()) return List.of();

        Document parsed = Jsoup.parse(contentHtml);
        parsed.select("script, style, noscript").remove();
        // <br> là ranh giới đoạn, không phải chữ. Đổi thành dấu đánh riêng để
        // tách được, chứ không đổi thành dấu chấm - dấu chấm là chữ tác giả
        // không viết.
        parsed.select("br").forEach(node -> node.replaceWith(new org.jsoup.nodes.TextNode(BREAK)));

        Elements blocks = parsed.body().select(BLOCK_TAGS);
        List<String> source = new ArrayList<>();
        for (Element block : blocks) {
            // Thẻ bọc ngoài chứa các khối khác sẽ lặp lại y nguyên chữ của con
            // nó, làm người nghe nghe mỗi đoạn hai lần.
            //
            // Phải so `other != block`: select() của jsoup tính cả chính phần
            // tử đang gọi, khác với querySelectorAll của DOM. Thiếu vế này thì
            // MỌI đoạn đều tự thấy mình "chứa một khối" và bị bỏ hết.
            boolean wrapsOtherBlocks = block.select(BLOCK_TAGS).stream()
                    .anyMatch(other -> other != block);
            if (wrapsOtherBlocks) continue;
            source.add(block.wholeText());
        }
        if (source.isEmpty()) {
            // Chương chỉ có chữ trần: lúc này xuống dòng là cấu trúc duy nhất
            // còn lại, nên nó mới được coi là ranh giới đoạn.
            source.add(parsed.body().wholeText().replace("\n", BREAK));
        }

        List<String> out = new ArrayList<>();
        for (String text : source) {
            for (String line : text.split(BREAK, -1)) {
                // Mọi khoảng trắng liền nhau - kể cả xuống dòng trong mã HTML -
                // gộp thành một dấu cách. Đây là chuẩn hoá khoảng trắng, không
                // đụng tới chữ nào.
                String cleaned = WHITESPACE.matcher(line).replaceAll(" ").trim();
                if (!cleaned.isEmpty()) out.add(cleaned);
            }
        }
        return out;
    }

    /** Cắt một mẩu quá dài tại dấu phẩy, rồi tại khoảng trắng nếu vẫn còn dài. */
    private static List<String> splitLongPiece(String piece) {
        List<String> parts = new ArrayList<>();
        String rest = piece;
        while (rest.length() > CHUNK_LIMIT) {
            String window = rest.substring(0, CHUNK_LIMIT);
            // Ưu tiên dấu phẩy gần cuối cửa sổ; không có thì lấy khoảng trắng
            // cuối cùng.
            int comma = Math.max(window.lastIndexOf(", "), window.lastIndexOf("; "));
            int space = window.lastIndexOf(' ');
            // Cắt quá sớm thì mẩu còn lại vẫn dài mà mẩu này lại vụn; chỉ nhận
            // điểm cắt nằm sau nửa cửa sổ. Không có điểm nào hợp lệ thì cắt cứng
            // ở giới hạn - một "từ" dài hơn 220 ký tự không phải tiếng Việt, mà
            // là dữ liệu hỏng.
            int half = CHUNK_LIMIT / 2;
            int at = comma > half ? comma + 1 : space > half ? space : CHUNK_LIMIT;
            parts.add(rest.substring(0, at).trim());
            rest = rest.substring(at).trim();
        }
        if (!rest.isEmpty()) parts.add(rest);
        parts.removeIf(String::isEmpty);
        return parts;
    }

    /** Cắt một đoạn văn thành các mẩu vừa cho bộ đọc, theo đúng thứ tự. */
    public static List<String> paragraphChunks(String paragraph) {
        String text = paragraph == null ? "" : paragraph.trim();
        if (text.isEmpty()) return List.of();
        if (text.length() <= CHUNK_LIMIT) return List.of(text);

        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        Matcher matcher = SENTENCE.matcher(text);
        while (matcher.find()) {
            String sentence = matcher.group().trim();
            if (sentence.isEmpty()) continue;

            if (sentence.length() > CHUNK_LIMIT) {
                if (current.length() > 0) {
                    chunks.add(current.toString());
                    current.setLength(0);
                }
                chunks.addAll(splitLongPiece(sentence));
                continue;
            }

            if (current.length() > 0 && current.length() + 1 + sentence.length() > CHUNK_LIMIT) {
                chunks.add(current.toString());
                current.setLength(0);
                current.append(sentence);
            } else {
                if (current.length() > 0) current.append(' ');
                current.append(sentence);
            }
        }
        if (current.length() > 0) chunks.add(current.toString());
        return chunks;
    }

    /**
     * Chương đã sẵn sàng để nghe.
     *
     * <p>Mẩu nào cũng chỉ về đúng đoạn chứa nó, nên chỗ tô sáng trên màn hình
     * luôn là chỗ đang phát ra tiếng.
     */
    public static ChapterSpeech split(String contentHtml) {
        List<String> paragraphs = paragraphs(contentHtml);
        List<SpeechChunk> chunks = new ArrayList<>();
        for (int index = 0; index < paragraphs.size(); index++) {
            for (String text : paragraphChunks(paragraphs.get(index))) {
                chunks.add(new SpeechChunk(index, text));
            }
        }
        return new ChapterSpeech(List.copyOf(paragraphs), List.copyOf(chunks));
    }
}
