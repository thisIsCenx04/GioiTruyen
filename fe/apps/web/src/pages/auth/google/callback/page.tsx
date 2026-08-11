import { useEffect } from "react";
import { useNavigate } from "react-router-dom";

import { getPostLoginDestination } from "@/lib/auth";

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
        // The cookie matters as much as localStorage: getAccessToken() reads the
        // cookie first, and password sign-in sets both. Without it a Google user
        // looked signed out to any code that only checked cookies.
        document.cookie =
          `access_token=${encodeURIComponent(accessToken)}; path=/; max-age=1800; SameSite=Lax`;
        if (refreshToken) {
          localStorage.setItem("refresh_token", refreshToken);
          document.cookie =
            `refresh_token=${encodeURIComponent(refreshToken)}; path=/; max-age=2592000; SameSite=Lax`;
        }
        window.dispatchEvent(new Event("auth-change"));
      }
    }

    navigate(getPostLoginDestination(accessToken, returnTo), { replace: true });
  }, [navigate]);

  return (
    <div style={{ minHeight: "100vh", display: "flex", alignItems: "center", justifyContent: "center", fontFamily: "sans-serif" }}>
      <p style={{ color: "#0f5fff", fontWeight: 700 }}>Đang hoàn tất đăng nhập bằng Google...</p>
    </div>
  );
}
