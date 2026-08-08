import { useEffect } from "react";
import { LoginJourney } from "../../../components/auth-journeys";
import { AuthShell } from "../../../components/auth-shell";

export default function LoginPage() {
  useEffect(() => {
    if (typeof window !== "undefined" && (window.location.search || window.location.hash)) {
      const params = new URLSearchParams(window.location.search);
      const sensitiveKeys = ["email", "password", "token", "key", "access_token", "refresh_token", "secret", "code"];
      let modified = false;

      sensitiveKeys.forEach((key) => {
        if (params.has(key)) {
          params.delete(key);
          modified = true;
        }
      });

      if (modified || window.location.hash) {
        const cleanQuery = params.toString();
        const newUrl = cleanQuery ? `${window.location.pathname}?${cleanQuery}` : window.location.pathname;
        window.history.replaceState(null, "", newUrl);
      }
    }
  }, []);

  return (
    <AuthShell
      chapter="Cổng thư viện"
      eyebrow="Ấn ký độc giả"
      lead="Mở lại tủ truyện, giữ dấu chương đang đọc và bước tiếp vào những thế giới bạn đã chọn."
      title="Trở về"
      titleAccent="thư viện phép thuật."
    >
      <LoginJourney />
    </AuthShell>
  );
}
