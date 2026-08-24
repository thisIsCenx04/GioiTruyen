package com.storyplatform.unit;

import static org.assertj.core.api.Assertions.assertThat;

import com.storyplatform.shared.api.ClientIp;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

/**
 * Địa chỉ dùng làm khoá hạn mức.
 *
 * <p>Mọi chốt chặn đếm theo IP — giới hạn tần suất, khoá đăng nhập sai, chống
 * đếm trùng lượt xem và lượt bấm quảng cáo — đều đứng trên con số này. Đọc sai
 * một lần là hỏng tất cả cùng lúc, nên nó có bài kiểm riêng.
 */
class ClientIpTest {

    /** Đúng hình dạng nginx tạo ra: `$proxy_add_x_forwarded_for` nối IP thật vào cuối. */
    private static MockHttpServletRequest viaNginx(String forwardedChain, String realIp) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("127.0.0.1");
        if (forwardedChain != null) request.addHeader("X-Forwarded-For", forwardedChain);
        if (realIp != null) request.addHeader("X-Real-IP", realIp);
        return request;
    }

    @Test
    @DisplayName("qua nginx thì lấy X-Real-IP, vì nginx ghi đè hẳn tiêu đề đó")
    void prefersRealIpBehindProxy() {
        assertThat(ClientIp.of(viaNginx("1.2.3.4, 203.0.113.9", "203.0.113.9")))
                .isEqualTo("203.0.113.9");
    }

    /**
     * Đây là lỗi cũ. Bản trước lấy `split(",")[0]` — tức phần do chính người
     * gọi viết ra — nên đổi một dòng tiêu đề là được cấp ô đếm mới.
     */
    @Test
    @DisplayName("không lấy phần đầu chuỗi chuyển tiếp, vì đó là phần client tự viết")
    void ignoresTheClientWrittenHop() {
        assertThat(ClientIp.of(viaNginx("9.9.9.9, 203.0.113.9", null)))
                .isEqualTo("203.0.113.9")
                .isNotEqualTo("9.9.9.9");
    }

    @Test
    @DisplayName("chuỗi nhiều chặng thì lấy chặng cuối, chặng nginx nối vào")
    void takesTheLastHop() {
        assertThat(ClientIp.of(viaNginx("1.1.1.1, 2.2.2.2, 203.0.113.9", null)))
                .isEqualTo("203.0.113.9");
    }

    /**
     * Gọi thẳng vào cổng 8080 từ ngoài: người gọi không phải nginx, nên mọi
     * tiêu đề họ gửi kèm đều là lời của người lạ và bị bỏ hết.
     */
    @Test
    @DisplayName("gọi trực tiếp thì bỏ qua mọi tiêu đề, chỉ còn địa chỉ TCP thật")
    void ignoresHeadersFromUntrustedPeers() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("198.51.100.7");
        request.addHeader("X-Forwarded-For", "1.2.3.4");
        request.addHeader("X-Real-IP", "5.6.7.8");
        assertThat(ClientIp.of(request)).isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("qua nginx mà không có tiêu đề nào thì còn lại địa chỉ ngang hàng")
    void fallsBackToPeer() {
        assertThat(ClientIp.of(viaNginx(null, null))).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("chuỗi chuyển tiếp toàn khoảng trắng không làm rơi ra chuỗi rỗng")
    void skipsBlankHops() {
        assertThat(ClientIp.of(viaNginx(" , , ", null))).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("loopback IPv6 cũng là proxy nhà mình")
    void trustsIpv6Loopback() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr("::1");
        request.addHeader("X-Real-IP", "203.0.113.9");
        assertThat(ClientIp.of(request)).isEqualTo("203.0.113.9");
    }
}
