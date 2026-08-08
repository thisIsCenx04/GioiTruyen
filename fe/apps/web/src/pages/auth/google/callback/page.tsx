import { useEffect } from "react";
import { useNavigate } from "react-router-dom";

export default function GoogleCallbackPage() {
  const navigate = useNavigate();

  useEffect(() => {
    // Parse parameters from both hash fragment (#) and query params (?)
    const searchParams = new URLSearchParams(window.location.search);
    const hashParams = new URLSearchParams(window.location.hash.replace(/^#/, ""));

    const accessToken = hashParams.get("access_token") || searchParams.get("access_token");
    const refreshToken = hashParams.get("refresh_token") || searchParams.get("refresh_token");
    const returnTo = hashParams.get("returnTo") || searchParams.get("returnTo") || "/";

    // Immediately sanitize URL to wipe tokens from browser address bar & history
    if (window.history && window.history.replaceState) {
      window.history.replaceState(null, "", window.location.pathname);
    }

    if (accessToken) {
      document.cookie = `logged_in=true; path=/; max-age=2592000; SameSite=Lax`;
      if (typeof window !== "undefined") {
        localStorage.setItem("access_token", accessToken);
        if (refreshToken) {
          localStorage.setItem("refresh_token", refreshToken);
        }
        window.dispatchEvent(new Event("auth-change"));
      }
    }

    const safeTarget = returnTo.startsWith("/") && !returnTo.startsWith("//") ? returnTo : "/";
    navigate(safeTarget, { replace: true });
  }, [navigate]);

  return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", fontFamily: "sans-serif" }}>
      <p style={{ color: "#0f5fff", fontWeight: 700 }}>Đang hoàn tất đăng nhập bằng Google...</p>
    </div>
  );
}
