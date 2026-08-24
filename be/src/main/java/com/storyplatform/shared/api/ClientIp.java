package com.storyplatform.shared.api;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Địa chỉ thật của người gọi, dùng chung cho mọi chốt chặn tính theo IP.
 *
 * <p><b>Vì sao phải có lớp này.</b> Bốn nơi trong mã nguồn từng tự đọc IP theo
 * cùng một công thức, và công thức ấy sai giống hệt nhau:
 *
 * <pre>{@code request.getHeader("X-Forwarded-For").split(",")[0]}</pre>
 *
 * <p>Hai lỗi chồng lên nhau ở một dòng đó.
 *
 * <p><b>Một:</b> tiêu đề được tin vô điều kiện. Bất kỳ ai cũng gửi kèm được
 * {@code X-Forwarded-For} vào yêu cầu của mình. Kẻ tấn công đổi giá trị đó mỗi
 * lần gọi là mỗi lần nhận một ô đếm mới toanh — bộ giới hạn tần suất, bộ khoá
 * đăng nhập sai, và mọi thứ đếm theo IP đều thành vô hiệu, trong khi nhật ký
 * ghi lại toàn địa chỉ bịa.
 *
 * <p><b>Hai:</b> lấy nhầm đầu. nginx của trang này dùng
 * {@code $proxy_add_x_forwarded_for}, tức là <em>nối thêm</em> địa chỉ thật vào
 * cuối chuỗi client gửi lên. Nên phần tử đầu tiên là phần do client tự viết,
 * còn phần tử <em>cuối cùng</em> mới là địa chỉ nginx nhìn thấy.
 *
 * <p><b>Cách làm đúng.</b> Chỉ tin tiêu đề khi yêu cầu thật sự đến từ nginx
 * chạy cùng máy. Gọi thẳng vào cổng 8080 từ ngoài thì mọi tiêu đề bị bỏ qua và
 * chỉ còn địa chỉ TCP thật.
 */
public final class ClientIp {

    private ClientIp() {
    }

    /**
     * Địa chỉ để tính hạn mức, không bao giờ rỗng.
     *
     * @param request yêu cầu đang xử lý
     */
    public static String of(HttpServletRequest request) {
        String peer = request.getRemoteAddr();

        // Yêu cầu không đi qua proxy nhà mình: tiêu đề là lời của người lạ.
        if (!isTrustedProxy(peer)) {
            return peer == null || peer.isBlank() ? "unknown" : peer;
        }

        // nginx đặt X-Real-IP bằng proxy_set_header, tức là ghi đè hẳn giá trị
        // client gửi lên — nên đây là nguồn sạch nhất.
        String realIp = trimmed(request.getHeader("X-Real-IP"));
        if (realIp != null) {
            return realIp;
        }

        // Dự phòng: lấy phần tử CUỐI của X-Forwarded-For, phần do nginx nối vào.
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] hops = forwarded.split(",");
            for (int index = hops.length - 1; index >= 0; index -= 1) {
                String hop = trimmed(hops[index]);
                if (hop != null) {
                    return hop;
                }
            }
        }

        return peer == null || peer.isBlank() ? "unknown" : peer;
    }

    /**
     * Yêu cầu này có đến từ reverse proxy chạy cùng máy không.
     *
     * <p>nginx nối tới backend qua {@code 127.0.0.1:8080}, nên chỉ địa chỉ
     * loopback mới được quyền nói hộ ai đó.
     */
    private static boolean isTrustedProxy(String peer) {
        if (peer == null || peer.isBlank()) {
            return false;
        }
        return "127.0.0.1".equals(peer)
                || "::1".equals(peer)
                || "0:0:0:0:0:0:0:1".equals(peer)
                || peer.startsWith("127.");
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }
}
