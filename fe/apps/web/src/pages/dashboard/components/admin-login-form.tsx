"use client";

import { useNavigate } from "react-router-dom";
import { type FormEvent, useState } from "react";

import { getUserRolesFromToken } from "../../../lib/auth";

type AdminLoginResponse = Readonly<{
  accessToken?: string;
  refreshToken?: string;
}>;

export function AdminLoginForm() {
  const navigate = useNavigate();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setBusy(true);
    setError("");
    const form = new FormData(event.currentTarget);
    try {
      const response = await fetch("/api/v1/auth/login", {
        body: JSON.stringify({
          email: String(form.get("email") ?? ""),
          password: String(form.get("password") ?? ""),
        }),
        headers: { "Content-Type": "application/json" },
        method: "POST",
      });
      if (!response.ok) {
        throw new Error("Email hoặc mật khẩu quản trị không đúng.");
      }
      const data = (await response.json()) as AdminLoginResponse;
      if (!data.accessToken || !getUserRolesFromToken(data.accessToken).includes("ADMIN")) {
        throw new Error("Tài khoản này không có quyền quản trị.");
      }

      document.cookie = "logged_in=true; path=/; max-age=2592000; SameSite=Lax";
      document.cookie = `access_token=${encodeURIComponent(data.accessToken)}; path=/; max-age=1800; SameSite=Lax`;
      localStorage.setItem("access_token", data.accessToken);
      if (data.refreshToken) {
        document.cookie = `refresh_token=${encodeURIComponent(data.refreshToken)}; path=/; max-age=2592000; SameSite=Lax`;
        localStorage.setItem("refresh_token", data.refreshToken);
      }
      window.dispatchEvent(new Event("auth-change"));
      navigate("/dashboard/content/stories");
    } catch (cause) {
      setError(cause instanceof Error ? cause.message : "Không thể đăng nhập.");
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit}>
      <label>Email<input autoComplete="username" defaultValue="admin@gioitruyen.local" name="email" required type="email" /></label>
      <label>Mật khẩu<input autoComplete="current-password" name="password" required type="password" /></label>
      {error && <p role="alert">{error}</p>}
      <button disabled={busy} type="submit">{busy ? "Đang đăng nhập..." : "Đăng nhập quản trị"}</button>
    </form>
  );
}
