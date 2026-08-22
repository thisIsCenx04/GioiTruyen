package com.storyplatform.unit.tts;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyplatform.tts.application.ChapterTextSplitter;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cắt chương thành đoạn và mẩu.
 *
 * <p>Điều kiện sống còn: chữ đưa đi dựng tiếng phải bằng đúng chữ của chương.
 * Một chữ bị đổi là một chỗ người nghe nghe sai truyện mà không hề biết - họ
 * đang nghe chứ không đang đọc, nên không có cách nào tự phát hiện.
 *
 * <p>Các bài kiểm ở đây soi gương với {@code fe/apps/web/src/lib/speech.test.ts}.
 * Hai bản phải cho cùng kết quả, vì trình duyệt tô sáng theo chỉ số đoạn do máy
 * chủ đánh - lệch một đoạn là tô sáng sai dòng suốt cả chương.
 */
class ChapterTextSplitterTest {

    @Test
    @DisplayName("giữ nguyên từng chữ, chỉ bỏ thẻ")
    void keepsEveryWord() {
        assertThat(ChapterTextSplitter.paragraphs("<p>Diệp Phàm <strong>bước</strong> vào căn phòng.</p>"))
                .containsExactly("Diệp Phàm bước vào căn phòng.");
    }

    @Test
    @DisplayName("giữ đúng thứ tự các đoạn")
    void keepsParagraphOrder() {
        assertThat(ChapterTextSplitter.paragraphs("<p>Một.</p><p>Hai.</p><p>Ba.</p>"))
                .containsExactly("Một.", "Hai.", "Ba.");
    }

    /** Thẻ bọc ngoài chứa các đoạn con sẽ lặp lại y nguyên chữ của con nó. */
    @Test
    @DisplayName("không đọc lặp khi đoạn nằm trong thẻ bọc")
    void doesNotRepeatNestedBlocks() {
        assertThat(ChapterTextSplitter.paragraphs("<div><p>Một.</p><p>Hai.</p></div>"))
                .containsExactly("Một.", "Hai.");
    }

    @Test
    @DisplayName("bỏ script và style, không đọc mã nguồn thành lời")
    void dropsScripts() {
        assertThat(ChapterTextSplitter.paragraphs(
                "<p>Chào.</p><script>alert(1)</script><style>p{color:red}</style>"))
                .containsExactly("Chào.");
    }

    /* <br> chỉ tách đoạn, không được biến thành một dấu chấm mà tác giả không viết. */
    @Test
    @DisplayName("coi <br> là ranh giới đoạn, không chèn dấu chấm")
    void treatsBreakAsAParagraphBoundary() {
        assertThat(ChapterTextSplitter.paragraphs("<p>Dòng một<br>Dòng hai</p>"))
                .containsExactly("Dòng một", "Dòng hai");
    }

    @Test
    @DisplayName("không chèn dấu câu vào cuối đoạn chưa có dấu")
    void addsNoPunctuation() {
        assertThat(ChapterTextSplitter.paragraphs("<p>Trời tối</p><p>Hắn đi</p>"))
                .containsExactly("Trời tối", "Hắn đi");
    }

    /* Xuống dòng trong mã HTML chỉ là khoảng trắng cho dễ đọc, không phải ý đồ
       của tác giả - gộp chung hai thứ là tách đôi một đoạn liền mạch. */
    @Test
    @DisplayName("xuống dòng trong mã HTML không tách đoạn")
    void ignoresSourceNewlines() {
        assertThat(ChapterTextSplitter.paragraphs("<p>Hắn   \n\n  đi\tvề</p>"))
                .containsExactly("Hắn đi về");
    }

    @Test
    @DisplayName("bỏ đoạn rỗng dùng để giãn dòng")
    void dropsEmptyParagraphs() {
        assertThat(ChapterTextSplitter.paragraphs("<p>Một.</p><p></p><p>  </p><p>Hai.</p>"))
                .containsExactly("Một.", "Hai.");
    }

    @Test
    @DisplayName("HTML rỗng ra danh sách rỗng")
    void handlesEmptyInput() {
        assertThat(ChapterTextSplitter.paragraphs("")).isEmpty();
        assertThat(ChapterTextSplitter.paragraphs("<p></p>")).isEmpty();
        assertThat(ChapterTextSplitter.paragraphs(null)).isEmpty();
    }

    @Test
    @DisplayName("đoạn ngắn giữ nguyên một mẩu")
    void keepsShortParagraphsWhole() {
        assertThat(ChapterTextSplitter.paragraphChunks("Hắn đi. Nàng ở lại."))
                .containsExactly("Hắn đi. Nàng ở lại.");
    }

    /* Mẩu dài làm bộ tổng hợp chạy lâu, người nghe chờ trắng màn hình. */
    @Test
    @DisplayName("không mẩu nào vượt ngưỡng, kể cả với đoạn rất dài")
    void neverExceedsTheChunkLimit() {
        String long_ = "Hắn bước qua cánh cửa gỗ mục và nhìn thấy bóng người quen. ".repeat(120);
        List<String> chunks = ChapterTextSplitter.paragraphChunks(long_);

        assertThat(chunks).hasSizeGreaterThan(20);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.length()).isLessThanOrEqualTo(ChapterTextSplitter.CHUNK_LIMIT));
    }

    @Test
    @DisplayName("cắt được cả một câu dài không có dấu chấm nào")
    void splitsARunOnSentence() {
        List<String> chunks = ChapterTextSplitter.paragraphChunks(
                "tiếng nói vọng lại từ phía sau lưng ".repeat(40));

        assertThat(chunks).hasSizeGreaterThan(1);
        assertThat(chunks).allSatisfy(chunk ->
                assertThat(chunk.length()).isLessThanOrEqualTo(ChapterTextSplitter.CHUNK_LIMIT));
    }

    @Test
    @DisplayName("mỗi mẩu chỉ đúng về đoạn chứa nó")
    void pointsEachChunkAtItsParagraph() {
        var speech = ChapterTextSplitter.split(
                "<p>Diệp Phàm bước vào căn phòng.</p><p>Bên trong tối om.</p>");

        assertThat(speech.paragraphs()).hasSize(2);
        assertThat(speech.chunks()).extracting(ChapterTextSplitter.SpeechChunk::paragraph)
                .containsExactly(0, 1);
        assertThat(speech.chunks()).allSatisfy(chunk ->
                assertThat(speech.paragraphs().get(chunk.paragraph())).contains(chunk.text()));
    }

    /**
     * Bài kiểm quan trọng nhất của cả tệp.
     *
     * <p>Ghép mọi mẩu lại phải ra đúng toàn văn chương. Một phép so sánh bắt
     * được mọi kiểu hỏng cùng lúc: dịch, viết lại, thêm dấu câu, nuốt chữ, đảo
     * thứ tự.
     */
    @Test
    @DisplayName("ghép mọi mẩu lại ra đúng toàn văn chương, không thêm không bớt")
    void losesNothingAndAddsNothing() {
        String chapter = "<p>Diệp Phàm bước vào căn phòng.</p>"
                + "<p>“Ai đó?” hắn hỏi, giọng khàn đặc.</p>"
                + "<p>" + "Không có ai trả lời, chỉ có tiếng gió lùa qua khe cửa sổ vỡ. ".repeat(20) + "</p>"
                + "<p>Hắn đếm: 1, 2, 3.</p>";
        var speech = ChapterTextSplitter.split(chapter);

        String spoken = java.util.stream.IntStream.range(0, speech.paragraphs().size())
                .mapToObj(index -> speech.chunks().stream()
                        .filter(chunk -> chunk.paragraph() == index)
                        .map(ChapterTextSplitter.SpeechChunk::text)
                        .collect(Collectors.joining(" ")))
                .collect(Collectors.joining("\n"));

        assertThat(spoken).isEqualTo(String.join("\n", speech.paragraphs()));
    }

    @Test
    @DisplayName("giữ nguyên tên riêng, số và dấu ngoặc kép")
    void keepsNamesNumbersAndQuotes() {
        var speech = ChapterTextSplitter.split("<p>Diệp Phàm nói: “Ta có 3 viên đan dược.”</p>");
        assertThat(speech.chunks().get(0).text()).isEqualTo("Diệp Phàm nói: “Ta có 3 viên đan dược.”");
    }
}
